(ns raid-boss.commands
  (:use
   com.rpl.specter)
  (:require
   [clojure.core.async :as a]
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [discljord.events.state :as st]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging* application-information state]]
   [taoensso.timbre :as log]))

(defn options-match?
  [options data]
  (if (seq data)
    (let [[datum & data] data]
      (case (:type datum)
        (1 2) (recur
               (:options (get (transform [MAP-VALS] first (group-by :name (filter (comp #{1 2} :type) options)))
                              (:name datum)))
               (:options datum))
        (when (apply = (map #(select-keys % [:type :name]) [datum (first options)]))
          (recur (rest options) (rest data)))))
    true))

(defmulti blacklist
  (fn [interaction]
    (select-one [:data :options FIRST :name (view keyword)] interaction)))

(defmethod blacklist :view
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4
                                    :data {:content "Viewing the blacklist"}))

(defmulti blacklist-add
  (fn [interaction]
    (select-one [:data :options FIRST :options FIRST :name (view keyword)] interaction)))

(defmethod blacklist :add
  [interaction]
  (blacklist-add interaction))

(defmethod blacklist-add :regex
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4
                                    :data {:content "Added a regex to the blacklist"}))

(defmethod blacklist-add :text
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4
                                    :data {:content "Added a text pattern to the blacklist"})
  (a/go
    (a/<! (a/timeout 10000))
    (msg/create-followup-message! *messaging* (:id (a/<! @application-information)) (:token interaction)
                                  :content "Banned all the users with that name")))
