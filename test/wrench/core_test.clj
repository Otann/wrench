(ns wrench.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as ns-tools]
            [wrench.core :as cfg]))


(cfg/def user)
(cfg/def path {:spec int?})
(cfg/def port {:default 8080 :spec int?})
(cfg/def missing {:require true})
(cfg/def conformed {:spec (s/+ int?)})


(deftest sources
  ;; We hope that USER is present in every OS
  (testing "Reading from env-var"
    (is (= user (System/getenv "USER")))

    (cfg/reset! :with-env {"CONFORMED" "[1 2 3]"})
    (is (= conformed [1 2 3]))

    (cfg/reset! :with-env {"CONFORMED" "1,2,3"})
    (is (= conformed ::cfg/invalid))

    (cfg/reset! :with-env (cfg/from-file "test/config.edn"))
    (is (= user "from-file"))))


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
    (is (= (System/getenv "USER")
           (get (cfg/config) #'user)))
    (is (= 8080
           (get (cfg/config) #'port)))))


(deftest errors
  (testing "Spec mismatch"
    (is (= ::cfg/invalid path)))

  (testing "Missing config"
    (is (= nil missing))
    (is (false? (cfg/validate-and-print))))

  (testing "With fixed errors"
    (cfg/reset! :with-redefs {missing "foo"
                              path    239})
    (is (true? (cfg/validate-and-print)))))




