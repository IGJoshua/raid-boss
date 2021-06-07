(ns raid-boss.commands
  (:use
   com.rpl.specter)
  (:require
   [clojure.core.async :as a]
   [clojure.string :as str]
   [datahike.api :as d]
   [discljord.connections :as con]
   [discljord.messaging :as msg]
   [discljord.permissions :as perms]
   [discljord.events.state :as st]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [raid-boss.components :refer [*db* *gateway* *messaging* application-information state]]
   [superv.async :as sa]
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

(defn ban-existing-matches
  [matches? guild-id interaction-token]
  (a/go
    (let [ban-count
          (count
           (sa/<!*
            (for [user (select [(transformed [(collect ::st/guilds (keypath guild-id) :members MAP-KEYS)
                                              ::st/users]
                                             #(select-keys %2 %1))
                                ::st/users MAP-VALS (selected? :username (pred matches?))]
                               @state)]
              (msg/create-guild-ban! *messaging* guild-id (:id user)
                                     :delete-message-days 1
                                     :reason "Username matched a server blacklist."
                                     :audit-reason "New blacklist pattern"))))]
      (msg/create-followup-message! *messaging* (:id (a/<! @application-information)) interaction-token
                                    :content (str "Completed a purge of the new pattern, banning "
                                                  ban-count " users.")
                                    :flags 64))))

(defn pattern-exists?
  [guild-id pattern type]
  (pos? (count (d/q {:query '[:find ?p
                              :in $ ?guild ?pattern ?type
                              :where
                              [?g :guild/id ?guild]
                              [?g :guild/blacklist ?p]
                              [?p :blacklist/pattern ?pattern]
                              [?p :blacklist/type ?type]]
                     :args [(d/db *db*) guild-id pattern type]}))))

(def pattern-path (comp-paths :data :options FIRST :options FIRST :options FIRST :value))

(defmethod blacklist-add :regex
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4
                                    :data {:content "Added a regex to the blacklist" :flags 64})
  (let [regex (select-one pattern-path interaction)]
    (ban-existing-matches (partial re-matches regex) (:guild-id interaction) (:token interaction))))

(defmethod blacklist-add :text
  [interaction]
  (msg/create-interaction-response! *messaging* (:id interaction) (:token interaction) 4
                                    :data {:content "Added a text pattern to the blacklist" :flags 64})
  (let [pattern (select-one pattern-path interaction)]
    (ban-existing-matches #(.equalsIgnoreCase pattern %) (:guild-id interaction) (:token interaction))))
