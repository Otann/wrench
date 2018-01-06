(ns wrench.stati
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


(def ^:dynamic *config-name* ".config.edn")


;; Contents of process environment. In production usually loaded once when code is loaded.
;; During development can be reloaded using cfg/reload, including overrides from edn file
(defn get-env-data []
  (merge (read-edn-file *config-name*)
         (read-system-env)))


;; Config specs discovered while loading user's code. Every cfg/def call appends to this map.
(defonce ^:private config-specs (atom {}))


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
    (let [conformer (get known-conformers spec edn-conformer)
          with-conf (s/and (s/conformer conformer) spec)]
      (s/conform with-conf data))
    (catch Exception e ::s/invalid)))


(defn- get-value
  "Reads value from
  - environment variables, normalizing name to caps and underscores
  - `:default` provided in field definition
  - edn file, intended for local development, name taked from *config-name*"
  [name-symbol raw-definition]
  (let [env-data  (get-env-data)
        spec      (get raw-definition :spec string?)
        default   (get raw-definition :default)
        env-name  (get raw-definition :name (keyword name-symbol))
        env-value (get env-data env-name)]
    (if env-value
      (coerce env-value spec)
      (:default raw-definition))))


(defmacro def
  "Defines a config to read and validate using a spec
    Field name is encouraged to ba namespaced keyword, which name will
    be used to match corresponding environment variable
    Field definiton is a map that should include:
    - `info` to print to *out* if validation failed
    - `spec` spec to validate the value
    - `require` to tell that validation should fail if value is missing
    - `default` to provide a fallback value
    - `secret to hide value from *out* during validation`"
  [name-symbol raw-definition]
  `(let [definition (eval raw-definition)]
     (swap! config-specs #(assoc % name-symbol definition))
     (def name-symbol (get-value name-symbol raw-definition))))
