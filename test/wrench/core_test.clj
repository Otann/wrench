(ns wrench.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as ns-tools]
            [wrench.core :as cfg]))


(cfg/def user {:doc "Currently logged-in user"})

(cfg/def port {:default 8080
               :spec    int?})

(cfg/def test-list {:spec (s/+ int?)})

(deftest sources
  ;; We hope that USER is present in every OS
  (testing "Reading from env-var"
    (is (= user (System/getenv "USER"))))

  (testing "Coersion"
    (is (= test-list [1 2 3]))))


(deftest coercion
  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
      (do (is (= ?out (#'cfg/coerce ?in ?spec)))
          (is (= ?out (#'cfg/coerce ?out ?spec))))

      "123" int? 123
      "1.5" double? 1.5
      12345 string? "12345"
      "foo" keyword? :foo)))


(deftest collection
  (testing "Config collection works"
    (cfg/def user)
    (cfg/def port {:default 8080
                   :spec    int?})

    (is (= {#'user      (System/getenv "USER")
            #'port      8080
            #'test-list [1 2 3]}
           (cfg/config)))

    (is (cfg/validate-and-print))))
