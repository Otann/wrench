(ns wrench.core
  (:refer-clojure :rename {get core-get})
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]))


(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))


(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))


(defn- read-env-file [f]
  (if-let [env-file (io/file f)]
    (if (.exists env-file)
      (into {} (edn/read-string (slurp env-file))))))


(def ^:private env-data (merge (read-system-env)
                               (read-env-file ".config.edn")))


(def ^:private config-specs (atom {}))


(def ^:private known-conformers
  {int?     #(Integer/parseInt %)
   double?  #(Double/parseDouble %)
   string?  identity
   keyword? keyword})


(defn- edn-conformer [data]
  (try
    (edn/read-string data)
    (catch Throwable e ::s/invalid)))


(defn- coerce [data spec]
  (try
    (let [conformer (core-get known-conformers spec edn-conformer)
          with-conf (s/and (s/conformer conformer) spec)]
      (s/conform with-conf data))
    (catch RuntimeException e ::s/invalid)))


(defn get
  "Reads configuration value from
  - environment variables, normalizing name to caps and underscores
  - `.config.edn` as an edn file, intended for local development
  - `:fallback` provided in field definition"
  [field-name]
  (let [env-name  (keyword (name field-name))
        env-value (core-get env-data env-name)
        spec      (get-in @config-specs [field-name :spec] string?)
        default   (get-in @config-specs [field-name :default])]
    (if env-value
      (coerce env-value spec)
      (get-in @config-specs [field-name :default]))))


(defn def
  "Defines a config to read and validate using a spec
  Field name is encouraged to ba namespaced keyword, which name will
  be used to match corresponding environment variable
  Field definiton is a map that should include:
  - `info` to print to *out* if validation failed
  - `spec` spec to validate the value
  - `require` to tell that validation should fail if value is missing
  - `default` to provide a fallback value
  - `secret to hide value from *out* during validation`"
  [field-name field-def]
  {:pre [(string? (:info field-def))]}
  (swap! config-specs #(assoc % field-name field-def)))


(defn- collect-errors []
  (for [[field-name field-data] @config-specs]
    (let [{:keys [info spec require default]
           :or   {require false
                  spec    string?}} field-data
          config-value (get field-name)
          is-invalid   (= config-value ::s/invalid)]
      (cond
        (and require (nil? config-value))
        (str "Field " field-name " (" info ") is required and is absent or does not conform")

        is-invalid
        (str "Field " field-name " (" info ") was not required, but supplied and does not conform spec")))))


(defn config []
  ;; ensures all namespaces with defs are loaded
  (dir/scan-all (track/tracker))
  (into {} (for [field-name (keys @config-specs)]
             [field-name (get field-name)])))


(defn check []
  (every? nil? (collect-errors)))


(defn check-or-quit!
  "Ensures that every defined config conforms to it's spec if it is required, quits otherwise"
  []
  (let [errors (filter (complement nil?)
                       (collect-errors))]
    (if (empty? errors)
      (do (for [[field-name value] (config)]
            (println "- "
                     field-name ": "
                     (if (get-in @config-specs [field-name :secret])
                       "<SECRET>"
                       value))))
      (do (map println errors)
          (System/exit 1)))))

