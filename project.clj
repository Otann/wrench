(defproject wrench "0.1.0-SNAPSHOT"
  :description "Elegant project configuration for a more civilised zge"

  :url "https://github.com/otann/wrench/"

  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]]

  :profiles {:uberjar {:aot :all}
             :dev     {:plugins [[pjstadig/humane-test-output "0.8.2"]
                                 [com.jakemccrary/lein-test-refresh "0.22.0"]
                                 [com.taoensso/timbre "4.1.4"]]}}

  ;; Artifact deployment info
  :scm {:name "git"
        :url  "https://github.com/otann/wrench"}

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]]

  :pom-addition [:developers [:developer
                              [:name "Anton Chebotaev"]
                              [:url "http://otann.github.io"]
                              [:email "anton.chebotaev@gmail.com"]
                              [:timezone "+1"]]])