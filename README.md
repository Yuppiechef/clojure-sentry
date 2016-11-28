# yuppiechef.sentry

Utilities for interactive with sentry.io error tracking from Clojure.

## Usage

```
(require '[yuppichef.sentry :as sentry])

;; Run code with tracking.
;; Takes a ring request (nil is fine) in order to extract request metadata.
(capture config req
  (do-stuff))

;; Capture across entire app by adding middleware:
(-> handler (sentry/wrap-sentry {:dsn ".."}))

;; This middleware can also take path into ring request, if that's your style
(-> handler (sentry/wrap-sentry [:config :sentry]))

;; It's not convenient to require reference to config
;; at adhoc places in the code..

;; Set up global value
(reset! sentry/fallback {:dsn "..."})

;; Capture adhoc exception anywhere in project:
(sentry/capture-error e)

;; Middleware will also fall back to global value
(-> handler sentry/wrap-sentry)

;; There is also `sentry/wrap-catch` which provides a mechanism to
;; match specific exceptions and substitute a dynamic response.
```

## License

Copyright © 2016 Yuppiechef

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
