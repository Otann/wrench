# Wrench

[![Circle CI](https://circleci.com/gh/Otann/wrench.svg?style=shield&no-cache=0)](https://circleci.com/gh/Otann/wrench)
[![Clojars](https://img.shields.io/clojars/v/wrench.svg?no-cache=1)](https://clojars.org/wrench)
[![codecov](https://codecov.io/gh/Otann/wrench/branch/master/graph/badge.svg)](https://codecov.io/gh/Otann/wrench)

<img width="25%"
     max-height="100px"
     align="right" padding="5px"
     alt=":)"
     src="/wrench.png"/>

Wrench is a library to manage your clojure app's configuration.
It is designed with specific goals in mind:

- **All values are available during initialization of your code**
- That means you can use it in your `def`s (and def-like macros, like `defroutes`)  
- All values come from environment variables, as [12 factors manifesto](https://12factor.net/config) recommends
- Each configuration could be accompanied with a custom spec
- One can ensure that whole config matches provided specs during runtime
- Configuration values are coerced to their spec from string and edn (enables values like `[8080 8888]`)
- Definition and usage of each key are easily traceable, since they are simple vars
- For local development extra values could be provided in REPL and from local EDN file

## Installation

Add `[wrench "0.3.3"]` to the dependency section in your project.clj file.

Wrench requires 1.9 version of Clojure.

## Usage

Simplest way to use it is to define a config with a simple `def`. 
For instance, if you want to read environment variable `USER` you would do following:  

```clojure
(require '[wrench.core :as cfg])
(cfg/def user)
```

You can also customize name of the variable and provide specification:

```clojure
(cfg/def port {:name "HTTP_PORT", :spec int?})
```

In this case loaded value would be coerced to an int.

Following specs are coerced to corresponding values: `string?`, `int?`, `double?`, `boolean?`, `keyword?`.
EVerything else is coerced to edn.

There are plenty of other options:

- `:doc` will be symbol's documentation
- `:spec` spec-compatible (including any predicate) to validate the value, defaults to `string?`
- `:name` name of the environment variable, defaults to uppercased name of the var (ignoring namespace) with dashes replaced with underscores
- `:require` fails validation, if value is missing, default is `false`
- `:default` to provide a fallback value if it is missing, default is nil
- `:secret` to hide value from `*out*` during validation, default is `false`

```clojure
(cfg/def oauth-secret {:doc    "OAuth secret to validate token"
                       :require true
                       :secret  true})

(cfg/def host {:doc "Remote host for a dependency service"
               :name "SERVICE_NAME_HOST"
               :require true})
```

Then use those vars as you would use any other constant, i.e.: 

```clojure
(cfg/def port {:name "NREPL_PORT"
               :spec int?
               :default 7888})

(mount/defstate nrepl-server
  :start (nrepl-server/start-server :port port)
  :stop (nrepl-server/stop-server nrepl-server))
```

If a value does not pass validation, `::cfg/invalid` will be used.

To ensure you have everything configured properly, validate your config before app starts:

```clojure
(defn -main [& args]
  (println "Starting service!")
  (if-not (cfg/validate-and-print)
    (System/exit 1)))
```

If everything is alright, then configuration will be printed to `*out*`,
replacing values marked as `:secret` with `<SECRET>`:
 
```
Loaded config:
-  #'some.service/port 8080
-  #'some.service/token <SECRET>
``` 
 
If there were errors during validation
or required keys are missing, then aggregated summary will be printed and `false` returned:

```
Failed to load config:
- configuration #'some.service/token is required and is missing
- configuration #'some.service/port present, but does not conform spec: something-wrong
```

## Testing

Idiomatic `with-redefs` could be used to alter var's value:

```clojure
;; service.clj
(ns some.service
  (:require [wrench.core :as cfg]))
  
(cfg/def user {:default "Rich"}) 

;; service-test.clj
(ns some.service-test
  (:require [clojure.test :refer :all]
            [some.service :as svc]))
 
(deftest a-test
  (testing "Let's talk about javascript"
    (with-redefs [svc/user "David"]
      (is (= svc/user "David")))))
```

You can also permanently alter config, by providing alternative values to vars:

```clojure
(cfg/reset! :var {#'svc/user "David"})
```

or environment variables

```clojure
(cfg/reset! :env {"PORTS" "[8080 8081 8082]"})
; or load from file
(cfg/reset! :env (cfg/from-file "dev-config.edn"))
;; or in combination
(cfg/reset! :env (from-file "dev-config.edn")
            :var {#'ports [8080 8081]})
```

Those changes will be applied to global scope, your experience may vary depending on your test runner.

## REPL and reloaded workflow

If during REPL development you ever need whole configuration map, it is available using:

```clojure
(cfg/config)
```

Note, that wrench relies on default reloading mechanics, maning that changes in environment variables
or in external config file would not trigger reloading variables with 
`(clojure.tools.namespace.repl/refresh)`.

To mitigate this you could either use `refresh-all` or reload config manually before your system starts:

```clojure
(defn reset "reloads modified source files, and restarts the states" []
  (stop)
  (cfg/reset! :env (cfg/from-file "dev-config.edn"))
  (ns-tools/refresh :after 'user/go))
```

## License

Copyright © 2018 Anton Chebotaev

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
