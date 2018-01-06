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
  - `:default` provided in config definition
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


(defn- find-all-definitions
  "Collects all defined configurations, based on meta"
  []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::cfg (meta v))]
    v))


(defn config
  "Loads all config definitions as a map"
  []
  (into {} (for [v (find-all-definitions)] [v (::cfg (meta v))])))


(defn- collect-errors [config]
  (for [[var-name var-def] config]
    (let [{:keys [info spec require default]
           :or   {require false
                  spec    string?}} var-def
          config-value (var-get var-name)
          is-invalid   (= config-value ::s/invalid)]
      (cond
        (and require (nil? config-value))
        (str "Configuration " var-name " (" info ") is required and is absent or does not conform")

        is-invalid
        (str "Configuration " var-name " (" info ") was not required, but supplied and does not conform spec")))))



(defn validate []
  (every? nil? (collect-errors (config))))


(defn validate-or-quit!
  "Ensures that every defined config conforms to it's spec if it is required, quits otherwise"
  []
  (let [config-data (config)
        errors      (filter (complement nil?)
                            (collect-errors config-data))]
    (if (empty? errors)
      (doseq [[var-name var-def] config-data]
        (println "- " var-name ": " (if (:secret var-def) "<SECRET>" var-def)))
      (do (map println errors)
          (System/exit 1)))))


(defmacro def
  "Defines a config to read and validate using a spec
    be used to match corresponding environment variable
    Definiton is a map that should include:
    - `info` to print to *out* if validation failed
    - `spec` spec to validate the value
    - `require` to tell that validation should fail if value is missing
    - `default` to provide a fallback value
    - `secret to hide value from *out* during validation`"
  [name-symbol raw-definition]
  `(let [definition (eval raw-definition)]
     (def ^{::cfg raw-definition} name-symbol ::uninitialized)
     (alter-var-root #'~name-symbol (get-value name-symbol raw-definition))))
