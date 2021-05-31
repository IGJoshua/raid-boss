(ns raid-boss.commands
  (:require
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging*]]))

(defn options-match?
  [options data]
  true)

(defn test-handler
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4 :data {:content "Hello, world!"}))
