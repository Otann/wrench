# Wrench

[![Circle CI](https://circleci.com/gh/Otann/wrench.svg?style=shield&no-cache=0)](https://circleci.com/gh/Otann/wrench)
[![Clojars](https://img.shields.io/clojars/v/wrench.svg?no-cache=1)](https://clojars.org/wrench)

<img width="25%"
     max-height="100px"
     align="right" padding="5px"
     alt=":)"
     src="/wrench.png"/>

Wrench is a library to manage your clojure app's configuration.
It is designed with specific goals in mind:

- **All values and related functionality is available during initialization of your code**
- That means you can use it in your `def`s (and def-like macros, like `defroutes`)  
- All values come from environment variables, as [12 factors menifesto](https://12factor.net/config) recommends
- Each configuration key is accompanied with a description and a spec
- One can ensure that configuration matches provided specs
- Configuration values are coerced to their spec from string and edn (enables values like `[8080 8888]`)
- Namespaced keywords are allowed and encouraged, so definition of each key is easily traceable 

In addition to environment variables, for local development, wrench reads from `.config.edn`.

## Installation

Add `[wrench "0.1.0"]` to the dependency section in your project.clj file.

## Usage

Start by defininig your configuration keys and giving them description.
Namespaced keywords are encouraged, for the better code navigation and autocompletion.

```clojure
(require '[wrench.core :as cfg])
(cfg/def ::http-port {:info    "HTTP port"
                      :spec    int?                    
                      :default 8080})
```

Options map structure:

- `:info` to print to `*out*` if validation failed
- `:spec` spec-compatible (including any predicate) to validate the value, defaults to `string?`
- `:name` name of the environment variable, defaults to capitalised name of the keyword (ignoring namespace) with dashes replaced with underscores
- `:require` fails validation, if value is missing, default is `false`
- `:default` to provide a fallback value if it is missing
- `:secret` to hide value from `*out*` during validation, default is `false`

```clojure
(cfg/def ::oauth-secret {:info    "OAuth secret to validate token"
                         :require true
                         :secret  true})

(cfg/def ::host {:info "Remote host for a dependency service"
                 :name "SERVICE_NAME_HOST"
                 :require true})
```

If you prefer to keep your configuration in one file, you have an option to define whole config at once

```clojure
(cfg/defconfig {::oauth-secret {:info    "OAuth secret to validate token"
                                :require true
                                :secret  true}
                ::host         {:info    "Remote host for a dependency service"
                                :name    "SERVICE_NAME_HOST"
                                :require true}})
```

Then pull the value where need it. 

```clojure
(cfg/get ::config/http-port)
```

Values will be available in static (meaning `def`s) and coerced to described spec if possible.
If a value does not pass validation, `nil` will be used.

No exceptions will be raised, because if there are multiple errors, you'll have to fix them one by one.
Instead, to ensure you have everything configured properly, validate your config before app starts.

```clojure
(cfg/check-or-quit!)
```

If everything is alright, then configuration will be printed to `*out*`,
replacing values marked as `:secret` with `<SECRET>`. If there were errors during validation
or required keys are missing, then aggregated summary will be printed and application will exit.

If you need softer version, that does not quit, and wish to fix errors manually then use

```clojure
(cfg/check)
```

## REPL and reloaded workflow

If during REPL development you ever need whole configuration map, it is available using:

```clojure
(cfg/config)
```

If you use reloaded workflow, it may happen that you remove some defs during development. As those are statically 
collected in a global var, you need to reset it **before** reloading your code:

```clojure
(cfg/reset-defs!)
```

To re-read your configuration

```clojure
(cfg/reload!)
```

Additionally you can use different filename or supply raw data

```clojure
(cfg/reload! "dev-config.edn")
; or
(cfg/reload! {:http-port 8080
              :oauth-secret "xxxxxx"})
```

Be careful including sensitive data like keys, when using latter.

## License

Copyright © 2017 Anton Chebotaev

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
