(ns wrench.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [wrench.core :as cfg]
            [clojure.java.io :as io]))


(use-fixtures
  :each (fn [f]
          (cfg/reset-defs!)
          (cfg/reload! {})
          (f)))


(deftest config-from-env
  ;; We hope that USER is present in every OS
  (testing "Reading from env-var"
    (cfg/def ::user {:info "Currently logged-in user"})

    (is (= (cfg/get ::user)
           (System/getenv "USER")))))


(deftest config-from-override
  (testing "Value is discovered from override"
    (cfg/reload! {:sample-key "value from override"})
    (cfg/def ::sample-key {:info "Sample key to test configuration"})

    (is (= (cfg/get ::sample-key)
           "value from override"))))


(deftest coercion
  (testing "Value is read from an simple edn"
    (cfg/reload! {:sample-list "[1 2 3]"})
    (cfg/def ::sample-list {:info "Sample key to test coercion"
                            :spec (s/+ int?)})

    (is (= (cfg/get ::sample-list)
           [1 2 3])))

  (testing "Coercions work and are idempotent"
    (are [?in ?spec ?out]
      (do (is (= ?out (#'cfg/coerce ?in ?spec)))
          (is (= ?out (#'cfg/coerce ?out ?spec))))

      "123" int? 123
      "1.5" double? 1.5
      12345 string? "12345"
      "foo" keyword? :foo)))


(deftest config-collection
  (testing "Config collection works"
    (cfg/reload! {:sample-key "value from override"})
    (cfg/def ::user {:info "Currently logged-in user"})
    (cfg/def ::sample-key {:info "Sample key to test configuration"})

    (is (= {::sample-key "value from override"
            ::user       (System/getenv "USER")}
           (cfg/config)))))


(deftest errors
  (testing "Spec mismatch"
    (cfg/def ::user {:info "This key will be present, but in wrong format"
                     :spec int?})

    (is (= ::s/invalid
           (cfg/get ::user))))

  (testing "Missing config"
    (cfg/def ::missing {:info    "This key will be absent"
                        :require true})

    (is (= nil (cfg/get ::missing)))
    (is (false? (cfg/check)))))


(deftest reading-env-file
  (testing "No error when file does not exist"

    (is (nil? (cfg/read-edn-file "nonexistent.edn"))))

  (testing "Parses as EDN"
    (spit "target/dev-env.edn" {:sample-key "1"})

    (is (= {:sample-key "1"} (cfg/read-edn-file "target/dev-env.edn")))))
