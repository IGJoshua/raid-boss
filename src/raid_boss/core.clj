(ns raid-boss.core
  (:require
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [datahike.api :as d]
   [discljord.messaging :as msg]
   [discljord.connections :as con]
   [discljord.events :as e]
   [discljord.events.middleware :as mdw]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [integrant.core :as ig]
   [raid-boss.components :refer [*db* *gateway* *messaging*]]
   [taoensso.timbre :as log])
  (:import
   (java.io PushbackReader)))

(derive :datalog.db/datahike :datalog/db)
(derive :logger.instance/timbre :logger/instance)

(derive :discord.bot.intent/guilds :discord.bot/intent)
(derive :discord.bot.intent/guild-members :discord.bot/intent)
(derive :discord.bot.intent/guild-invites :discord.bot/intent)

(derive :raid-boss.event/interaction-create :raid-boss.event/handler)
(derive :raid-boss.event/update-guild-interactions-and-roles :raid-boss.event/handler)
(derive :raid-boss.event/track-invite-use :raid-boss.event/handler)
(derive :raid-boss.event/ban-blacklisted-users :raid-boss.event/handler)
(derive :raid-boss.event/update-admin-roles :raid-boss.event/handler)
(derive :discord.event/guild-create :discord/event)
(derive :discord.event/guild-delete :discord/event)
(derive :discord.event/guild-member-add :discord/event)
(derive :discord.event/guild-role-create :discord/event)
(derive :discord.event/guild-role-update :discord/event)
(derive :discord.event/guild-role-delete :discord/event)
(derive :discord.event/interaction-create :discord/event)
(derive :discord.event/invite-create :discord/event)

(defn load-config
  [path]
  (let [file (volatile! (io/file path))]
    (block return
      (tagbody
       retry
       (restart-case
           (if (and @file (.exists @file))
             (far/wrap-exceptions
               (->> @file
                    io/reader
                    slurp
                    ig/read-string
                    (return-from return)))
             (far/error ::file-not-found :path path))
         (::far/use-value [config]
           :report "Ignore the file and use the passed configuration"
           :interactive #(list (read))
           (return-from return config))
         (::use-path [path]
           :report "Retry using an alternate file path"
           :interactive #(list (read-line))
           (vreset! file (io/file path))
           (go retry)))))))

(defn await-shutdown!
  [system]
  system)

(defn start
  [config]
  (let [system (-> config
                   ig/prep
                   ig/init)]
    (await-shutdown! system)
    (ig/halt! system)))

(defn -main
  [& args]
  (handler-case (-> (System/getenv "RAID_BOSS_CONFIG_PATH")
                    load-config
                    start)
    (::file-not-found [c & {:keys [path]}]
      (println "The file" path "is missing")))
  (shutdown-agents))

(defmethod ig/init-key :datalog/schema
  [_ schema]
  schema)

(defmethod ig/init-key :datalog.db/datahike
  [_ {:keys [db-config schema]}]
  (if-not (d/database-exists? db-config)
    (do (d/create-database db-config)
        (let [conn (d/connect db-config)]
          (d/transact conn (concat (:idents schema)
                                   (:entities schema)))
          conn))
    (d/connect db-config)))

(defmethod ig/halt-key! :datalog.db/datahike
  [_ conn]
  (d/release conn))

(def timbre-tools-logger
  (memoize
   (fn [ns]
     (reify clojure.tools.logging.impl/Logger
       (enabled? [_ level]
         (log/may-log? level ns log/*config*))
       (write! [_ level throwable message]
         (log/log! level :p
                   [message]
                   (cond-> {:config log/*config*
                            :?ns-str (str ns)
                            :?file nil
                            :?line nil}
                     throwable (assoc :?err throwable))))))))

(defn timbre-tools-logger-factory
  []
  (reify clojure.tools.logging.impl/LoggerFactory
    (name [_] "Timbre")
    (get-logger [_ ns]
      (timbre-tools-logger ns))))

(defmethod ig/init-key :logger.instance/timbre
  [_ {:keys [log-file level]}]
  (let [config log/*config*
        old-tools-logging-logger (volatile! nil)]
    (log/merge-config!
     (cond-> {:level level}
       log-file (assoc :appenders {:spit (log/spit-appender {:fname log-file})})))
    (when (find-ns 'clojure.tools.logging)
      (alter-var-root #'clojure.tools.logging/*logger-factory*
                      (fn [old-logger]
                        (vreset! old-tools-logging-logger old-logger)
                        (timbre-tools-logger-factory))))
    {:old-config config
     :old-tools-logging @old-tools-logging-logger}))

(defmethod ig/halt-key! :logger.instance/timbre
  [_ {:keys [old-config old-tools-logging]}]
  (when (find-ns 'clojure.tools.ogging)
    (alter-var-root #'clojure.tools.logging/*logger-factory*
                    (constantly old-tools-logging)))
  (log/set-config! old-config))

(defmethod ig/init-key :discord.bot/token
  [_ {:keys [source] :as opts}]
  (case source
    :file (slurp (:path opts))))

(defmethod ig/init-key :discord.connection/event-channel
  [_ {:keys [size]}]
  (a/chan size))

(defmethod ig/halt-key! :discord.connection/event-channel
  [_ chan]
  (a/close! chan))

(defmethod ig/init-key :discord.connection/messaging
  [_ {:keys [token]}]
  (msg/start-connection! token))

(defmethod ig/halt-key! :discord.connection/messaging
  [_ msg]
  (msg/stop-connection! msg))

(defmethod ig/init-key :discord.connection/gateway
  [_ {:keys [intents token channel]}]
  (log/info "Starting gateway connection with intents" (set intents))
  (con/connect-bot! token channel :intents (set intents)))

(defmethod ig/halt-key! :discord.connection/gateway
  [_ conn]
  (con/disconnect-bot! conn))

(defmethod ig/init-key :discord.bot/intent
  [_ name]
  name)

(defmethod ig/init-key :discord/event
  [_ event]
  (:name event))

(defmethod ig/init-key :raid-boss/event-handler
  [_ {:keys [event-handlers]}]
  (let [handlers (reduce
                  (fn [acc handler]
                    (reduce #(update %1 %2 (fnil conj []) (:handler-fn handler)) acc (:events handler)))
                  {}
                  event-handlers)]
    (fn [event-type event-data]
      (e/dispatch-handlers handlers event-type event-data))))

(defmethod ig/init-key :raid-boss/middleware
  [_ {:keys [handler middleware]}]
  ((apply comp middleware) handler))

(defmethod ig/init-key :raid-boss.event/handler
  [_ {:keys [events handler-fn db messaging gateway]}]
  {:events events
   :handler-fn (let [fun (resolve handler-fn)]
                 (fn [& args]
                   (binding [*messaging* messaging
                             *gateway* gateway
                             *db* db]
                     (apply fun args))))})

(defmethod ig/init-key :discord.bot/application
  [_ {:keys [event-channel handler logger]}]
  (let [stop-chan (a/chan 1)]
    (a/go-loop []
      (a/alt!
        stop-chan ([v])
        event-channel ([[event-type event-data]]
                       (handler event-type event-data)
                       (recur))
        :priority true))
    {:stop-chan stop-chan}))

(defmethod ig/halt-key! :discord.bot/application
  [_ {:keys [stop-chan]}]
  (a/put! stop-chan :stop))
