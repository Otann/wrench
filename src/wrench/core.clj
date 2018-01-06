(ns wrench.core
  (:refer-clojure :rename {get core-get})
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn]))


(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))


(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(keywordize k) v]))
       (into {})))


(defn read-edn-file [f]
  (when-let [env-file (io/file f)]
    (when (.exists env-file)
      (edn/read-string (slurp env-file)))))


(def ^:dynamic *default-config-name* ".config.edn")


;; Contents of process environment. In production usually loaded once when code is loaded.
;; During development can be reloaded using cfg/reload, including overrides from edn file
(defonce ^:private env-data (atom (merge (read-edn-file *default-config-name*)
                                         (read-system-env))))


;; Config specs discovered while loading user's code. Every cfg/def call appends to this map.
(defonce ^:private config-specs (atom {}))


(def ^:private known-conformers
  {int?     #(if (int? %) % (Integer/parseInt %))
   double?  #(if (double? %) % (Double/parseDouble %))
   string?  str
   keyword? keyword})


(defn- edn-conformer [data]
  (try
    (edn/read-string data)
    (catch Exception e ::s/invalid)))


(defn- coerce [data spec]
  (try
    (let [conformer (core-get known-conformers spec edn-conformer)
          with-conf (s/and (s/conformer conformer) spec)]
      (s/conform with-conf data))
    (catch Exception e ::s/invalid)))

(defn get
  "Reads configuration value from
  - environment variables, normalizing name to caps and underscores
  - `.config.edn` as an edn file, intended for local development
  - `:default` provided in field definition"
  [field-name]
  (let [spec      (get-in @config-specs [field-name :spec] string?)
        default   (get-in @config-specs [field-name :default])
        env-name  (get-in @config-specs [field-name :name] (keyword (name field-name)))
        env-value (core-get @env-data env-name)]
    (if env-value
      (coerce env-value spec)
      (get-in @config-specs [field-name :default]))))

(defn select-from-ns [ns-str]
  (let [ns-config-specs (->> @config-specs
                             keys
                             (filter #(= (namespace %) ns-str)))]
    (into {} (for [k ns-config-specs]
               [(keyword (name k)) (get k)]))))

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
  ;; TODO use spec for validation
  {:pre [(string? (:info field-def))]}
  (swap! config-specs #(assoc % field-name field-def)))


(defn defconfig [config]
  {:pre [(every? #(string? (:info %))
                 (vals config))]}
  (swap! config-specs #(merge % config)))


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


(defn reload!
  ([] (reload! (read-edn-file *default-config-name*)))
  ([override]
   (let [data (cond
                (map? override) override
                (string? override) (read-edn-file override)
                :else (throw (ex-info "reload supports only string/map override" {})))]
     (reset! env-data (merge (read-system-env)
                             data)))))


(defn reset-defs! []
  (reset! config-specs {}))
