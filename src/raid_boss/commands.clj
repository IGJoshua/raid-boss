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
  (or (every? empty? [options data])
      (and (every? (complement :required) options)
           (empty? data))
      (and (apply = (map (comp #(select-keys % [:name :type]) first) [options data]))
           (if (#{1 2} (:type (first options))) ; Check if this is a subcommand or group
             (options-match? (:options (first options)) (:options (first data)))
             true)
           (recur (rest options) (rest data)))))

(defn test-handler
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4 :data {:content "Hello, world!"}))
