(ns wrench.core
  (:refer-clojure :rename {reset! core-reset!})
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
          with-conf (s/and (s/conformer conformer) spec)
          conformed (s/conform with-conf data)]
      (if (= conformed ::s/invalid)
        ::invalid
        conformed))
    (catch Exception e ::invalid)))


(defn find-all-vars
  "Collects all defined configurations, based on attached meta"
  []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::definition (meta v))]
    v))


(defn config
  "Provides map with all defined configs and their loaded values"
  []
  (->> (find-all-vars)
       (map #(vector % (var-get %)))
       (into {})))


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


(defn- symbol->env-name [^Var cfg-var]
  (-> (.sym cfg-var)
      (str/upper-case)
      (str/replace "-" "_")))


(defn- symbol->keyword [^Var cfg-var]
  (keyword (.sym cfg-var)))


(def ^:private dev-env (atom {}))
(def ^:private dev-var (atom {}))


(defn evaluate-cfg [^Var cfg-var definition]
  (let [spec      (get definition :spec string?)
        default   (get definition :default)
        env-name  (get definition :name (symbol->env-name cfg-var))
        env-value (or (get @dev-var cfg-var)
                      (get @dev-env env-name)
                      (get system-env env-name))]
    (if env-value
      (coerce env-value spec)
      (:default definition))))


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
       (def
         ~(vary-meta var-symbol assoc ::definition definition#)
         (evaluate-cfg #'~var-symbol ~definition#)))))


(defn reload [var-symbol]
  (when-let [definition (::definition (meta var-symbol))]
    (let [new-value (evaluate-cfg var-symbol definition)]
      (alter-var-root var-symbol (constantly new-value)))))


(defn reset!
  "Fakes value of configs for repl-driven development
  - :var accepts map of existing vars that should be replaced
  - :env accepts map with that simulates extra environment variables passed"
  [& {:keys [var env]}]
  (core-reset! dev-env env)
  (core-reset! dev-var var)
  (doseq [cfg-var (find-all-vars)]
    (reload cfg-var)))


(comment

  (cfg/reset! :var {#'ports [8080 8081]})

  (cfg/reset! :env {"SERVER_PORTS" "[8080 8081 8082]"})

(cfg/reset! :env (from-file "dev-config.edn")
            :var {#'ports [8080 8081]})

  )