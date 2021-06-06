(ns raid-boss.components
  (:require
   [discljord.messaging :as msg]))

(def ^:dynamic *messaging* nil)
(def ^:dynamic *gateway* nil)
(def ^:dynamic *db* nil)

(def application-information
  (delay (msg/get-current-application-information! *messaging*)))
