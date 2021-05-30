(ns raid-boss.events
  (:require
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [raid-boss.components :refer [*db* *gateway* *messaging*]]))

(defn update-guild-state
  [event-type event-data]
  )

(defn track-invite-use
  [event-type event-data]
  )

(defn ban-blacklisted-user-on-join
  [_ event-data]
  )

(defn update-admin-roles
  [event-type event-data]
  )
