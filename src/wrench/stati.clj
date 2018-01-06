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


(defn- reload-var
  "Reads value from
  - environment variables, normalizing name to caps and underscores
  - `:default` provided in config definition
  - edn file, intended for local development, name taked from *config-name*"
  [var-symbol]
  (let [definition (meta var-symbol)
        env-data   (get-env-data)
        spec       (get definition :spec string?)
        default    (get definition :default)
        env-name   (get definition :name (keyword var-symbol))
        env-value  (get env-data env-name)
        var-value  (if env-value
                     (coerce env-value spec)
                     (:default definition))]
    (alter-var-root var-symbol var-value)))


(defn- find-all-vars
  "Collects all defined configurations, based on meta"
  []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::cfg (meta v))]
    v))


(defn- vars-with-def
  "Builds mapping of vars to their configuration meta"
  []
  (into {} (for [v (find-all-vars)] [v (::cfg (meta v))])))


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
  (every? nil? (collect-errors (vars-with-def))))


(defn validate-or-quit!
  "Ensures that every defined config conforms to it's spec if it is required, quits otherwise"
  []
  (let [vars   (vars-with-def)
        errors (filter (complement nil?)
                       (collect-errors vars))]
    (if (empty? errors)
      (doseq [[var-name var-def] vars]
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
  [var-symbol raw-definition]
  (let [definition# (eval raw-definition)]
    `(do (def ^{::cfg definition#} ~var-symbol ::uninitialized)
         (reload-var #'~var-symbol))))
