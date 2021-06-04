(ns raid-boss.events
  (:require
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging*]]))

(defn update-guild-state
  [deps event-type event-data]
  (let [commands (:commands deps)])
  ;; TODO: When a guild is added, the following need to happen
  ;; - Update all the roles list and set them to having permission to ban or not
  ;; - Update the invite counts for the existing invites
  ;; - Update all the commands for this server
  )

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
