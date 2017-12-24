(ns wrench.core
  (:refer-clojure :rename {get core-get})
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
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


(defn- read-env-file [f]
  (if-let [env-file (io/file f)]
    (if (.exists env-file)
      (into {} (edn/read-string (slurp env-file))))))


(def env-data (merge (read-system-env)
                     (read-env-file ".config.edn")))


(defonce config-specs (atom {}))


(def ^:private known-conformers
  {int?     #(Integer/parseInt %)
   double?  #(Double/parseDouble %)
   keyword? keyword})


(defn- coerce [data spec]
  (if-let [conformer (core-get known-conformers spec)]
    (s/conform (s/and (s/conformer conformer) spec)
               data)
    (s/conform spec
               data)))


(defn- get-value
  "Returns coerced value or throwable if it is not coercable"
  [field-name]
  (let [env-name  (keyword (name field-name))
        env-value (core-get env-data env-name)
        spec      (get-in @config-specs [field-name :spec])
        default   (get-in @config-specs [field-name :default])]
    (if env-value
      (let [result (coerce env-data spec)]
        (if (= result ::s/invalid)
          (ex-info "Requested configuration does not conform to the required spec"
                   {:name field-name
                    :spec spec})
          result))
      (get-in @config-specs [field-name :default]))))


(defn get [field-name]
  (let [result (get-value field-name)]
    (if (instance? Throwable result)
      (throw result)
      result)))


(defn defconfig [field-name {:keys [info spec require default] :as field-data}]
  (swap! config-specs #(assoc % field-name field-data)))


(defn- collect-errors []
  (for [[field-name {:keys [info spec require default] :or {require false}}] @config-specs]
    (let [config-value (get-value field-name)
          is-invalid   (instance? Throwable config-value)]
      (cond
        (and require (nil? config-value))
        (str "Field " field-name " (" info ") is required and is absent")

        (and require is-invalid)
        (str "Field " field-name " (" info ") is required and does not conform to the provided spec")

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
    (if-not (empty? errors)
      (map println errors)
      (System/exit 1))))

