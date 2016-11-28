(ns yuppiechef.sentry
  (:require
    [yuppiechef.sentry.impl :as impl]
    [clojure.string :as str]))

(def fallback (atom {:enabled? false}))

(defn build-url [{port :server-port :keys [scheme server-name uri]}]
  (str (name scheme) "://" server-name
       (when (and port (not= 80 port))
         (str ":" port))
       uri))

(defn http-info [req]
  {:url (build-url req)
   :method (:method req)
   :headers (:headers req {})
   :query_string (:query-string req "")
   :data (:params req {})
   :cookies (:cookies req)
   :env {:session (:session req {})}})

(defn parse-dsn [dsn]
  (let [[proto-auth url] (str/split dsn #"@")
        [protocol auth] (str/split proto-auth #"://")
        [key secret] (str/split auth #":")]
    {:key key
     :secret secret
     :uri (format "%s://%s" protocol (str/join "/" (butlast (str/split url #"/"))))
     :project-id (Integer/parseInt (last (str/split url #"/")))}))

(defn -normalize [{:keys [dsn enabled? ignore?] :as config}]
  (let [enabled? (if (some? enabled?) enabled? (seq dsn))
        capture? (if (and enabled? ignore?)
                   (comp not ignore?)
                   (constantly enabled?))]
    (assoc dsn
      ;; dsn
      :packet-info (parse-dsn dsn)
      :capture? capture?
      ;; extra
      ;; namespaces
      ;; user-info
      :http-info (:http-info config http-info))))

(def normalize (memoize -normalize))

(defn extract-config [config req]
  (or (when (vector? config)
        (get-in req config))
      (when (ifn? config)
        (config req))
      (when (and (map? config) (contains? config :dsn))
        config)
      @fallback))

(defn capture-error
  ([e]
   (capture-error nil nil e))
  ([config-or-req e]
   (capture-error config-or-req config-or-req e))
  ([config req e]
   (#'impl/capture-error (normalize (extract-config config req)) req e)))

(defmacro capture
  [config req & body]
  `(try
     ~@body
     (catch Exception e#
       (capture-error ~config ~req e#)
       (throw e#))
     (catch AssertionError e#
       (capture-error ~config ~req e#)
       (throw e#))))

(defn wrap-sentry
  ([handler]
   (fn [req]
     (capture nil req (handler req))))
  ([handler config]
   (fn [req]
     (capture config req (handler req)))))

(defn match? [catch? e]
  (cond
    (class? catch?) (instance? catch? e)
    (coll? catch?) (some #(match? % e) catch?)
    (ifn? catch?) (or (catch? e) (catch? (class e)))
    :else false))

(defn wrap-catch
  ([handler response-fn]
   (fn [req]
     (try
       (handler req)
       (catch Exception e
         (response-fn req e))
       (catch AssertionError e
         (response-fn req e)))))
  ([handler catch? response-fn]
   (fn [req]
     (try
       (handler req)
       (catch Exception e
         (if (match? catch? e)
           (response-fn req e)
           (throw e)))
       (catch AssertionError e
         (if (match? catch? e)
           (response-fn req e)
           (throw e)))))))
