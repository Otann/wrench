(ns wrench.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as ns-tools]
            [wrench.core :as cfg]))

;; Let's hope that USER is presented on every OS that we test on
(cfg/def user)
(cfg/def nick {:name "USER"})
(deftest sources-of-values

  (testing "when cfg/def used, then value is read from environment variables"
    (is (= user (System/getenv "USER"))))

  (testing "when custom name is provided, config uses to read from env"
    (is (= nick (System/getenv "USER"))))

  (testing "when env is faked, then corresponding configs are reset"
    (cfg/reset! :env {"USER" "root-env"})
    (is (= user "root-env")))

  (testing "when var is faked, then it's value is replaces"
    (cfg/reset! :var {#'user "root-var"})
    (is (= user "root-var"))))


(cfg/def port {:default 8080 :spec int?})
(deftest default-values

  (testing "when not supplied in env, then default value is used"
    (is (= nil (System/getenv "PORT")))
    (is (= port 8080)))

  (testing "when overriden with fake, then default is replaced by it"
    (cfg/reset! :env {"PORT" "9000"})
    (is (= port 9000)))

  (testing "when default does not conform to spec, then cfg is set to ::invalid"
    (cfg/reset! :env {"PORT" "hahaha"})
    (is (= port ::cfg/invalid))))

(cfg/def missing-not-required)
(cfg/def missing-but-required {:require true})
(cfg/def required-with-default {:require true
                                :default "default-value"})
(deftest required

  (testing "when env var is missing, then value is nil"
    (is (= missing-not-required nil)))

  (testing "when env var is missing, but required, then value is nil"
    (is (= missing-but-required nil)))

  (testing "when env var is missing, cfg is required and default is provided, then default is used"
    (is (= required-with-default "default-value"))))


(deftest coercion
  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
      (do (is (= ?out (#'cfg/coerce ?in ?spec)))
          (is (= ?out (#'cfg/coerce ?out ?spec))))

      "123" int? 123
      "1.5" double? 1.5
      12345 string? "12345"
      "false" boolean? false
      "" boolean? false
      "true" boolean? true
      "foo" keyword? :foo)))


(deftest config-collection
  (cfg/reset! :env {} :var {})
  (testing "config contains defined above vars"
    (is (contains? (cfg/config) #'user))
    (is (contains? (cfg/config) #'port)))

  (testing "config contains correct values for vars"
    (is (= (get (cfg/config) #'user)
           (System/getenv "USER")))
    (is (= (get (cfg/config) #'port)
           8080))))


(deftest errors-reporting

  (testing "when there are missing values, validation fails (config is defined above)"
    (is (false? (cfg/validate-and-print))))

  (testing "when errors are fixed with fake values, validation passes"
    (cfg/reset! :var {#'missing-but-required "value"})
    (is (true? (cfg/validate-and-print)))))




