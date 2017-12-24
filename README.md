# Wrench

[![Circle CI](https://circleci.com/gh/Otann/wrench.svg?style=shield&no-cache=0)](https://circleci.com/gh/Otann/morse)
[![Clojars](https://img.shields.io/clojars/v/wrench.svg)](https://clojars.org/wrench)

<img width="30%"
     max-height="100px"
     align="right" padding="5px"
     alt=":)"
     src="/wrench.png"/>

Wrench is a library to manage your clojure app's configuration.
It is designed with specifig goals in mind:

- All values come from environment variables, as [12 factors](https://12factor.net/config) recommend
- Each confoguration key is accompanied with a description and a spec
- Whole configuration can be validated at once before app starts
- Definition of each key is easily traceable using namespaced keywords
- Configuration values are coerced to their spec from string and edn

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
- `:require` fails validation, if value is missing, default is `false`
- `:default` to provide a fallback value if it is missing
- `:secret` to hide value from `*out*` during validation, default is `false`

```clojure
(cfg/def ::oauth-secret {:info    "OAuth secret to validate token"
                         :require true
                         :secret  true})

```

Then pull the value where need it, values will be available in static (maning `def`s) and coerced

```clojure
(cfg/get ::config/http-port)
```

To ensure you have everything configured properly

```clojure
(cfg/check-or-quit!)
```

If everything is alright, then configuration will be printed to `*out*`,
replacing values marked as `:secret` with `<SECRET>`. If there were errors during validation
or required keys are missing, then aggregated summary will be printed and application will exit.

If you need softer version, and to handle errors manually, then use

```clojure
(cfg/check)
```

If during REPL development you ever need whole configuration map, it is available using:

```clojure
(cfg/config)
```

## License

Copyright © 2017 Anton Chebotaev

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
