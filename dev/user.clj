(ns user
  (:use
   com.rpl.specter)
  (:require
   [clojure.core.async :as a]
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.formatting :as fmt]
   [discljord.permissions :as perm]
   [discljord.events.middleware :as mdw]
   [farolero.core :as far :refer [handler-bind handler-case restart-case]]
   [integrant.core :as ig]
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [raid-boss.core :as app]
   [raid-boss.components :refer [state]]
   [taoensso.timbre :as log]))

(integrant.repl/set-prep! #(-> "development-config.edn"
                               app/load-config
                               ig/prep))

(defn get-component
  [component]
  (get integrant.repl.state/system component))

(defmacro defcomponent
  [name key]
  `(defn ~name
     []
     (get-component ~key)))

(defcomponent db :datalog.db/datahike)
(defcomponent gateway :discord.connection/gateway)
(defcomponent messaging :discord.connection/messaging)
(defcomponent event-channel :discord.connection/event-channel)
