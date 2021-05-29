(ns raid-boss.core
  (:require
   [clojure.java.io :as io]
   [farolero.core :as far :refer [restart-case handler-case handler-bind
                                  block return-from tagbody go]]
   [integrant.core :as ig])
  (:import
   (java.io PushbackReader)))

(defn load-config
  [path]
  (let [file (volatile! (io/file path))]
    (block return
      (tagbody
       retry
       (restart-case
           (if (and @file (.exists @file))
             (->> @file
                 io/reader
                 PushbackReader.
                 ig/read-string
                 (return-from return))
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
