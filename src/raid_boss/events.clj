(ns raid-boss.events
  (:require
   [clojure.core.async :as a]
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging* application-information]]
   [superv.async :as sa]
   [taoensso.timbre :as log]))

(defn update-guild-state
  [deps event-type event-data]
  (a/go
    (con/guild-request-members! *gateway* (:id event-data))

    ;; Ensure the guild exists in the db
    (log/debug "Upsert the guild with id" (:id event-data))
    (d/transact *db*
                [{:guild/id (:id event-data)}])

    ;; Update the invite counts for the existing invites
    (a/go
      (log/debug "Update the invites to the guild" (:id event-data))
      (let [invites (a/<! (msg/get-guild-invites! *messaging* (:id event-data)))]
        (d/transact *db*
                    (mapcat (fn [invite]
                              [[:db/add [:guild/id (:id event-data)] :guild/invite (:code invite)]
                               {:db/id (:code invite)
                                :invite/code (:code invite)
                                :invite/author (:id (:inviter invite))
                                :invite/count (:uses invite)}])
                            invites))))

    ;; Update all the roles list and set them to having permissions
    (log/debug "Update all the role permissions for roles" (:roles event-data))
    (d/transact *db*
                (mapcat (fn [role]
                          [[:db/add [:guild/id (:id event-data)] :guild/role (:id role)]
                           {:db/id (:id role)
                            :role/id (:id role)
                            :role/permissions (:permissions role)}])
                        (:roles event-data)))

    ;; Update all the commands for this server
    (let [commands (:commands deps)
          application-id (:id (a/<! @application-information))
          guild-version (first
                         (first
                          (d/q {:query '[:find ?v
                                         :in $ ?guild
                                         :where
                                         [?g :guild/id ?guild]
                                         [?g :guild/command-version ?v]]
                                :args [(d/db *db*) (:id event-data)]})))]
      (log/debug "Guild with id" (:id event-data) "has command version" guild-version)
      ;; If the commands are out of date
      (when (or (not guild-version)
                (> (:command-version deps)
                   guild-version))
        (log/info "The commands need to be updated in guild" (:id event-data))
        (log/debug "Deleting all the commands")
        ;; Delete all the commands
        (sa/<!*
         (for [command (a/<! (msg/get-guild-application-commands! *messaging* application-id (:id event-data)))]
           (msg/delete-guild-application-command! *messaging* application-id (:id event-data) (:id command)
                                                  :audit-reason "Update commands to most recent version")))

        (log/debug "Construct all the commands")
        ;; Create all the commands based on the options
        (sa/<!*
         (let [everyone (first (filter (comp #{(:id event-data)} :id) (:roles event-data)))]
           (log/trace "Everyone role:" everyone)
           (for [command commands]
             (a/go
               (log/trace "Starting command" command)
               (log/trace "Default permissions" (empty? (:permissions command)))
               (let [created (a/<! (msg/create-guild-application-command!
                                    *messaging* application-id (:id event-data)
                                    (:name command)
                                    (:description command)
                                    :options (:options command)
                                    :default-permission (empty? (:permissions command))
                                    :audit-reason "Update commands to most recent version"))]
                 (log/trace "Created the command" created)
                 (when-not (empty? (:permissions command))
                   (log/trace "The permissions is not empty")
                   (a/<! (msg/edit-application-command-permissions!
                          *messaging* application-id (:id event-data)
                          (:id created)
                          (into
                           [{:type 2
                             :id (:owner-id event-data)
                             :permission true}]
                           (map (fn [id]
                                  {:type 1
                                   :id id
                                   :permission true}))
                           (for [role (filter (comp (complement #{(:id event-data)}) :id) (:roles event-data))
                                 :when (perms/has-permissions? (:permissions command)
                                                               (:permissions everyone)
                                                               [(:permissions role)])]
                             (:id role)))))))))))

        (log/debug "Update the guild state to the new version")
        ;; Tell the db that we've updated the commands
        (d/transact *db* (into [{:db/id [:guild/id (:id event-data)]
                                 :guild/command-version (:command-version deps)}]
                               (map (fn [{:keys [id permissions]}]
                                      {:role/id id
                                       :role/permissions permissions}))
                               (:roles event-data)))))))

(defn delete-guild
  [deps event-type event-data]
  ;; If the bot was removed from the guild
  (when-not (:unavailable event-data)
    ;; Delete all the guild state
    (d/transact *db* [[:db/retractEntity [:guild/id (:id event-data)]]])))

(defmulti username-matches?
  (fn [type pattern username]
    type))

(defmethod username-matches? :regex
  [_ pattern username]
  (re-matches pattern username))

(defmethod username-matches? :text
  [_ pattern username]
  (.equalsIgnoreCase pattern username))

(defn process-new-user
  [deps event-type event-data]
  ;; TODO: When a user joins
  ;; - Update all the invite counts
  ;;   - If only one increased, associate that invite with this join event
  ;;   - Add the user to a recent join-group with this invite, adding it if it doesn't exist
  ;;   - Calculate if this is a high risk join group and notify admins
  ;; Ban the user if their name matches the blacklist
  (block did-ban?
    (doseq [[pattern type]
            (d/q {:query '[:find ?pattern ?type
                           :in $ ?guild
                           :where
                           [?g :guild/id ?guild]
                           [?g :guild/blacklist ?p]
                           [?p :blacklist/pattern ?pattern]
                           [?p :blacklist/type ?type]]
                  :args [(d/db *db*) (:guild-id event-data)]})
            :when (username-matches? type pattern (get-in event-data [:user :username]))]
      (msg/create-guild-ban! *messaging* (:guild-id event-data) (get-in event-data [:user :id]))
      (return-from did-ban? true))))

(defn record-unquarantined-user
  [deps event-type event-data]
  ;; TODO: When a user is updated
  ;; - If the quarantine role was removed, or the user was unbanned
  ;;   - Mark the user as having no action taken against them
  ;;   - Mark action requested and ban appealed to false
  ;;   - Set the user's ham and spam counts to 0
  )

(defn update-admin-roles
  [deps event-type event-data]
  ;; TODO: When a role is added or updated
  ;; - If the ban perm is enabled
  ;;   - Enable the commands
  ;; - Otherwise
  ;;   - Disable the commands
  )

(defn delete-role
  [deps event-type event-data]
  ;; TODO: When a role is deleted
  ;; - Remove the role from the cache
  )

(defn record-messages
  [deps event-type event-data]
  ;; TODO: When a user posts a message
  ;; - If the message is spam
  ;;   - Record a spam on the user
  ;;   - If the user has over 50% spam, take action against them
  ;;   - If the join group the user is a part of has over 50% spam users, take action against them all
  ;; - If the message is ham
  ;;   - Record a ham on the user
  )
