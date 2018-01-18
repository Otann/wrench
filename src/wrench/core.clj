(ns wrench.core
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:import (clojure.lang Var)))


;; Minimal structure to carry config definition
(deftype Uninitialized [definition])


(def system-env (into {} (System/getenv)))


(defn from-file [f]
  (when-let [env-file (io/file f)]
    (when (.exists env-file)
      (edn/read-string (slurp env-file)))))


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


(defn find-all-vars
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


(defn reset-cfg! [^Var cfg-var definition extra-env extra-redefs]
  (let [spec      (get definition :spec string?)
        default   (get definition :default)
        env-name  (get definition :name (symbol->env-name cfg-var))
        env-map   (merge system-env extra-env)
        env-value (or (get env-map env-name)
                      (get env-map (symbol->keyword cfg-var))
                      (get extra-redefs cfg-var))
        var-value (if env-value
                    (coerce env-value spec)
                    (:default definition))
        var-meta  {::definition definition
                   ::loaded     env-value}
        var-doc   (select-keys definition [:doc])]
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
  (let [definition# (or raw-definition {})]
    `(do
       (def ~var-symbol ::uninitialised)
       (reset-cfg! #'~var-symbol ~definition# nil nil))))


(defn reset-all-vars! [redefs extra-env]
  (doseq [cfg-var (find-all-vars)]
    (reset-cfg! cfg-var (::definition (meta cfg-var)) extra-env redefs)))


(defmacro reset!
  "Reloads all loaded configuration vars, using extra datasources, provided as parameters to this macro:
  - :with-redefs accepts map of existing vars that should be replaced
  - :with-env accepts map with that simulates extra environment variables passed"
  [& {:keys [with-redefs with-env]}]
  (let [redefs# (into {} (for [[k v] with-redefs] `[#'~k ~v]))]
    `(reset-all-vars! ~redefs# ~with-env)))



(comment

  (cfg/reset! :with-env {"PORTS" "[8080 8081 8082]"})

  (cfg/reset! :with-env (from-file "dev-config.edn"))


  )