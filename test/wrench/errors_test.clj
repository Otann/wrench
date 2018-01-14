(ns wrench.errors-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as ns-tools]
            [wrench.core :as cfg]))

(deftest errors
  (testing "Spec mismatch"
    (cfg/def path {:spec int?})
    (is (= ::cfg/invalid path)))

  (testing "Missing config"
    (cfg/def missing {:require true})

    (is (= nil missing))
    (is (false? (cfg/validate-and-print)))))