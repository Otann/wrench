(ns wrench.reload-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as ns-tools]
            [wrench.core :as cfg]))


(deftest reloading
  (testing "reloading with redefs"
    (cfg/def redefed {:default "default"})
    (cfg/reset! :with-redefs {redefed "reloaded"})
    (is (= "reloaded" redefed)))

  (testing "reloading with env"
    (cfg/def named {:name "FOO"})
    (cfg/reset! :with-env {"FOO" "reloaded"})

    (is (= "reloaded" named))))