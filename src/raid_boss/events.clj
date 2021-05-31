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
  [event-type event-data]
  ;; TODO: When a guild is added, the following need to happen
  ;; - Update all the roles list and set them to having permission to ban or not
  ;; - Update the invite counts for the existing invites
  )

(defn process-new-user
  [event-type event-data]
  ;; TODO: When a user joins, we need to get all the invites and see which ones went up
  ;; Then update the existing invites with the new numbers, and then add the member with the invite
  ;; If there's only one that went up, just use that invite as the name
  ;; Otherwise, just don't insert the member item

  ;; If we associate this with one invite, check to see how the invites over the last x minutes
  ;; compares to the average for this invite over the last y days
  ;; If it's way higher, then notify the admins and mark it as high risk

  ;; To notify the admins, make a message with a button to mark it as false-positive
  ;; If false-positive is marked, then remove the high-risk assessment on the item
  ;; Also add a button to take action which will invalidate the invite and ban everyone from the raid
  )

(defn record-unquarantined-user
  [event-type event-data]
  ;; TODO: Note that a user has been unbanned or unquarantined
  )

(defn update-admin-roles
  [event-type event-data]
  ;; TODO: Update the role passed to add or remove a role that can use the commands
  )

(defn record-spam-ham-messages
  [event-type event-data]
  ;; TODO: Record whether a given message was ham or spam for a user, maybe banning them
  )
