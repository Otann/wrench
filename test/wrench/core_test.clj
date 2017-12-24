(ns wrench.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [wrench.core :as cfg]))


(deftest env-vars
  (testing "Reading from env-var"
    (cfg/def ::user {:info "Currently logged-in user"})
    (is (= (cfg/get ::user)
           (System/getenv "USER")))))


(deftest env-file
  (testing "Spec is read from file"
    (cfg/def ::sample-key {:info "Sample key to test configuration"})
    (is (= (cfg/get ::sample-key)
           "value from file"))))


(deftest coercion
  (testing "Value is read from an simple edn"
    (cfg/def ::sample-list {:info "Sample key to test coercion"
                            :spec (s/+ int?)})

    (is (= (cfg/get ::sample-list)
           [1 2 3]))))


(deftest config-collection
  (testing "Config collection works"
    (with-redefs [cfg/config-specs (atom {})]
      (cfg/def ::user {:info "Currently logged-in user"})
      (cfg/def ::sample-key {:info "Sample key to test configuration"})

      (is (= {::sample-key "value from file"
              ::user       (System/getenv "USER")}
             (cfg/config))))))


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



