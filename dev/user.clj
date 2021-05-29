(ns user
  (:require
   [integrant.core :as ig]
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [raid-boss.core :as app]))

(integrant.repl/set-prep! #(-> "development-config.edn"
                               app/load-config
                               ig/prep))
