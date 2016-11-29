(ns yuppiechef.sentry.impl
  (:require
    [clojure.string :as str]
    [clj-http.client :as http]
    [cheshire.core :as json])
  (:import
    (java.net InetAddress)
    (java.sql Timestamp)
    (java.util Date UUID)))

(defn- make-frame [^StackTraceElement element app-namespaces]
  {:filename (.getFileName element)
   :lineno (.getLineNumber element)
   :function (str (.getClassName element) "." (.getMethodName element))
   :in_app (boolean (some #(.startsWith (.getClassName element) %) app-namespaces))})

(defn- make-stacktrace-info [elements app-namespaces]
  {:frames (reverse (map #(make-frame % app-namespaces) elements))})

(defn add-stacktrace [event-map ^Exception e & [app-namespaces]]
  (assoc event-map
    :exception
    [{:stacktrace (make-stacktrace-info (.getStackTrace e) app-namespaces)
      :type (str (class e))
      :value (.getMessage e)}]))

(defn- generate-uuid []
  (str/replace (UUID/randomUUID) #"-" ""))

(defn make-sentry-url [uri project-id]
  (format "%s/api/%s/store/" uri project-id))

(defn make-sentry-header [ts key secret]
  (str "Sentry sentry_version=2.0, "
       "sentry_client=clojure-sentry/0.1.0, "
       "sentry_timestamp=" ts ", "
       "sentry_key=" key ", "
       "sentry_secret=" secret))

(defn send-event [{:keys [ts uri project-id key secret]} event-info]
  (let [url (make-sentry-url uri project-id)
        header (make-sentry-header ts key secret)]
    (http/post url
      {:insecure? true
       :throw-exceptions false
       :headers {"X-Sentry-Auth" header, "User-Agent" "yuppiechef.sentry/0.1.0"}
       :body (json/generate-string event-info)})))

(defn capture [packet-info event-info]
  "Send a message to a Sentry server.
  event-info is a map that should contain a :message key and optional
  keys found at http://sentry.readthedocs.org/en/latest/developer/client/index.html#building-the-json-packet"
  (send-event
    packet-info
    (merge
      {:level "error"
       :platform "clojure"
       :server_name (.getHostName (InetAddress/getLocalHost))
       :ts (str (Timestamp. (.getTime (Date.))))
       :event_id (generate-uuid)}
      event-info)))

(defn- add-info [event-map iface info-fn req]
  (if info-fn
    (assoc event-map iface (info-fn req))
    event-map))

(defn capture-error [{:keys [packet-info extra namespaces capture? http-info user-info]} req ^Throwable e]
  (when (and capture? (capture? e))
    (future
      (capture packet-info
        (-> (merge extra {:message (.getMessage e)})
            (add-info "sentry.interfaces.Http" http-info req)
            (add-info "sentry.interfaces.User" user-info req)
            (add-stacktrace e namespaces))))))
