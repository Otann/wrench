(ns wrench.main
  (:gen-class)
  (:require [wrench.core :as cfg]))


(cfg/def port {:doc     "Port of Amsterdam"
               :spec    int?
               :default 8080})


(defn main [& args]
  (println "Test run, value of the port is" port)
  (cfg/validate-and-print))
