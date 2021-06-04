(ns raid-boss.events
  (:require
   [clojure.core.async :as a]
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging*]]))

(def application-information
  (delay (msg/get-current-application-information! *messaging*)))

(defn await-all
  [chans]
  (a/thread
    (loop [to-poll (map (fn [ch] {::chan ch}) chans)]
      (if (every? #(= (get % ::chan ::not-found) ::not-found) to-poll)
        to-poll
        (recur
         (doall
          (for [chan to-poll
                :let [ch (::chan chan)]]
            (or (when ch (a/poll! ch)) chan))))))))

(defn update-guild-state
  [deps event-type event-data]
  (a/go
    ;; TODO: When a guild is added, the following need to happen
    ;; - Update the invite counts for the existing invites
    ;; - Update all the commands for this server
    (let [commands (:commands deps)
          application-id (a/<! @application-information)]
      (when (> (:command-version deps)
               (d/q {:query '[:find ?v
                              :in $ ?guild
                              [?g :guild/id ?guild]
                              [?g :guild/command-version ?v]]
                     :args [(d/db *db*) (:id event-data)]}))
        (a/<! (await-all
               (for [command (a/<! (msg/get-guild-application-commands! *messaging* application-id (:id event-data)))]
                 (msg/delete-guild-application-command! *messaging* application-id (:id event-data) (:id command)
                                                        :audit-reason "Update commands to most recent version"))))
        ;; TODO: Create all the commands based on the options
        ;; Tell the db that we've updated the commands
        (d/transact *db* [{:db/id [:guild/id (:id event-data)]
                           :guild/command-version (:command-version deps)}])
        )
      ;; - Update all the roles list and set them to having permission to ban or not
      )))

(defn delete-guild
  [deps event-type event-data]
  ;; If the bot was removed from the guild
  (when-not (:unavailable event-data)
    ;; Delete all the guild state
    (d/transact *db* [[:db/retractEntity [:guild/id (:id event-data)]]])))

(defn process-new-user
  [deps event-type event-data]
  ;; TODO: When a user joins
  ;; - Update all the invite counts
  ;;   - If only one increased, associate that invite with this join event
  ;;   - Add the user to a recent join-group with this invite, adding it if it doesn't exist
  ;;   - Calculate if this is a high risk join group and notify admins
  ;; - Ban the user if their name matches the blacklist
  )

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
