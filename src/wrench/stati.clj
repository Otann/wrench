(ns wrench.stati
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:import (java.io Writer)
           (clojure.lang IObj IDeref Var)))


;; Minimal object structure to carry clojure metadata
(deftype Uninitialized [definition])
(defmethod print-method Uninitialized [x ^Writer writer]
  (print-ctor x (fn [o w] (print-method (.toString o) w)) writer))


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


(defn- find-all-vars
  "Collects all defined configurations, based on meta"
  []
  (for [n (all-ns)
        [_ v] (ns-publics n)
        :when (::definition (meta v))]
    v))


(defn- collect-errors []
  (for [var-name (find-all-vars)]
    (let [var-meta   (meta var-name)
          var-def    (::definition var-meta)
          required   (:require var-def)
          var-value  (var-get var-name)
          is-invalid (= var-value ::s/invalid)]
      (cond
        (and require (nil? var-value))
        (str "Configuration " var-name " (" var-def ") is required and is absent or does not conform")

        is-invalid
        (str "Configuration " var-name " (" var-def ") was not required, but supplied and does not conform spec")))))


(defn validate []
  (every? nil? (collect-errors)))


(defn load-var
  [^Var var-symbol]
  (when (instance? Uninitialized (var-get var-symbol))
    (let [definition (.definition (var-get var-symbol))
          env-data   (get-env-data)
          spec       (get definition :spec string?)
          default    (get definition :default)
          env-name   (get definition :name (keyword (.sym var-symbol)))
          env-value  (get env-data env-name)
          var-value  (if env-value
                       (coerce env-value spec)
                       (:default definition))]
      (alter-var-root var-symbol (constantly var-value))
      (alter-meta! var-symbol assoc ::definition definition)
      var-value)))


(defmacro defcfg
  "Defines a config to read and validate using a spec
    be used to match corresponding environment variable
    Definiton is a map that should include:
    - `info` to print to *out* if validation failed
    - `spec` spec to validate the value
    - `require` to tell that validation should fail if value is missing
    - `default` to provide a fallback value
    - `secret to hide value from *out* during validation`"
  [var-symbol raw-definition]
  `(do
     (def ~var-symbol (Uninitialized. ~raw-definition))
     (load-var #'~var-symbol)))


(defcfg port {:info    "USER"
              :name    :user
              :require true})

(comment


  (with-redefs [port 6060]
    port)
  (meta #'port)

  (find-all-vars)
  (vars-with-meta)

  (collect-errors)

  port



  (eval
    (macroexpand-1
      `(test port {:info    "HTTP posrt"
                   :default 8080}))
    )

  )


(comment

  ;; Maybe store value in derefable?
  (deftype Config [definition]
    IObj
    (meta [_] definition)
    (withMeta [_ definition] (Config. definition))
    IDeref
    (deref [_] nil)
    Object
    (toString [this]
      (str "Uninitialized value of " (pr-str definition))))

  (defmethod print-method Config [x ^Writer writer]
    (print-ctor x (fn [o w] (print-method (.toString o) w)) writer))

  )