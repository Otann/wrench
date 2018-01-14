(ns wrench.core
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:import (clojure.lang Var)))


;; Minimal structure to carry config definition
(deftype Uninitialized [definition])


(def ^:dynamic *config-name* "dev-config.edn")


(defn- read-system-env []
  (into {} (System/getenv)))


(defn read-edn-file [f]
  (when-let [env-file (io/file f)]
    (when (.exists env-file)
      (edn/read-string (slurp env-file)))))


(defn- read-env-data []
  (merge (read-system-env)
         (read-edn-file *config-name*)))


(def ^:private known-conformers
  {int?     #(if (int? %) % (Integer/parseInt %))
   double?  #(if (double? %) % (Double/parseDouble %))
   string?  str
   keyword? keyword})


(defn- edn-conformer [data]
  (try
    (edn/read-string data)
    (catch Exception e ::invalid)))


(defn- coerce [data spec]
  (try
    (let [conformer (get known-conformers spec edn-conformer)
          with-conf (s/and (s/conformer conformer) spec)]
      (s/conform with-conf data))
    (catch Exception e ::invalid)))


(defn- find-all-vars
  "Collects all defined configurations, based on attached meta"
  []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::definition (meta v))]
    v))


(defn- cfg-error-msg [^Var cfg-var]
  (let [var-meta  (meta cfg-var)
        required  (-> var-meta ::definition :require)
        var-value (var-get cfg-var)
        invalid?  (= var-value ::invalid)]
    (cond
      (and required (nil? var-value))
      (str "- configuration " cfg-var " is required and is missing")

      invalid?
      (str "- configuration " cfg-var " present, but does not conform spec: " (pr-str (::loaded var-meta))))))


(defn- printable-value [^Var cfg-var]
  (let [var-meta (meta cfg-var)
        secret?  (-> var-meta ::definition :secret)]
    (if secret? "<SECRET>" (var-get cfg-var))))


(defn- symbol->env-name [^Var cfg-var]
  (-> (.sym cfg-var)
      (str/upper-case)
      (str/replace "-" "_")))


(defn- symbol->keyword [^Var cfg-var]
  (keyword (.sym cfg-var)))


(defn load-cfg [^Var cfg-var]
  (let [definition (.definition (var-get cfg-var))
        spec       (get definition :spec string?)
        default    (get definition :default)
        env-name   (get definition :name (symbol->env-name cfg-var))
        env-value  (or (get (read-env-data) env-name)
                       (get (read-env-data) (symbol->keyword cfg-var)))
        var-value  (if env-value
                     (coerce env-value spec)
                     (:default definition))
        var-meta   {::definition definition
                    ::loaded     env-value}
        var-doc    (select-keys definition [:doc])]
    (alter-var-root cfg-var (constantly var-value))
    (alter-meta! cfg-var merge var-meta var-doc)
    var-value))


(defn config
  "Provides map with all defined configs and their loaded values"
  []
  (->> (find-all-vars)
       (map #(vector % (var-get %)))
       (into {})))


(defn validate-and-print
  "Validates that all defined configurations mathces their requirements and returns.
  Returns false if at least one definition is missing or does not confogrm to the spec.
  Either prints loaded configuration or list of errors."
  []
  (let [all-vars (find-all-vars)
        errors   (map cfg-error-msg all-vars)
        valid?   (every? nil? errors)]
    (if valid?
      (do (println "Loaded config:")
          (doseq [cfg-var (find-all-vars)]
            (println "- " cfg-var (printable-value cfg-var))))
      (do (println "Failed to load config:")
          (doseq [error errors]
            (when error (println error)))))
    valid?))


(defmacro def
  "Defines a config to read from environment variable and validate with a spec
  Definition map could could have:
  - `doc` if provided, will be var's docstring
  - `spec` spec to validate the value (default: string?)
  - `name` name of the environment variable to read (default: uppercased var name)
  - `require` to tell that validation should fail if value is missing (default: false)
  - `default` to provide a fallback value (default: nil)
  - `secret` if true, value will be replaced with <SECRET> while printing (default: false)"
  [var-symbol & [raw-definition]]
  `(do
     (def ~var-symbol (Uninitialized. (or ~raw-definition {})))
     (load-cfg #'~var-symbol)))
