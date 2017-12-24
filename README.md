# Wrench

Configuration management for a civilized age

Goals:

- follows 12-factor design pattern, reads values only from environment variables
- each configuration key is acompanied with a description and a spec
- definition of each key is easily traceable using namespaced keywords
- configuration values are coerced to their spec from json & yaml (*)
- whole configuration can be validated at once before app starts

In addition to environment variables, for local development, wrench reads `.config.edn`.

## Installation

Add `[wrench "0.1.0"]` to the dependency section in your project.clj file.

## Usage

First, define your configuration key.
Namespaced keywords are encouraged, for the better code navigation and autocompletion

```clojure
(require '[wrench.core :as cfg])
(cfg/defconfig ::http-port {:info    "HTTP port"
                            :spec    int?
                            :default 8080})

(cfg/defconfig ::oauth-secret {:info    "OAuth secret for requesting token"
                               :require true
                               :secret? true})
```

By default everything is not secret, not required and is `string?`.

Then pull the value where needed

```clojure
(defn create-service []
  {::http/port              (cfg/get ::config/http-port)
   ::http/resource-path     "/public"
   ::http/routes            (create-routes)
   ::http/type              :jetty)
```

To ensure you have everything configured properly

```clojure
(cfg/check-or-quit!)
```

If everything is configured properly, then configuration will be printed out,
replacing values marked as `:secret` with `<SECRET>`. If there were errors during validation
or required keys are missing, then aggregated summary will be printed and application will exit.

If you need softer version, and to handle errors manually, then use

```clojure
(cfg/check)
```

## License

Copyright © 2017 Anton Chebotaev

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
