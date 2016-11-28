(ns yuppiechef.sentry-test
  (:require
    [clojure.test :refer :all]
    [yuppiechef.sentry :refer :all]))

(deftest extract-config-test
  (is (= {:enabled? false} (extract-config nil nil)))
  (is (= {:enabled? false} (extract-config nil {:a 1})))
  (is (= {:enabled? false} (extract-config {:a 1} nil)))
  (is (= {:dsn "mydsn"} (extract-config {:dsn "mydsn"} nil)))
  (is (= {:a 2} (extract-config :a {:a {:a 2} :b 2})))
  (is (= {:a 3} (extract-config [:a :b] {:a {:b {:a 3}}})))
  (is (= #{:a :b} (extract-config (comp set keys) {:a 1 :b 2}))))

(defn ex-msg [_ ^Throwable e] (.getMessage e))

(deftest wrap-catch-test
  (is (= 2 ((wrap-catch inc ex-msg) 1)))
  (is (string? ((wrap-catch inc ex-msg) "a")))
  (is (string? ((wrap-catch inc #{ClassCastException} ex-msg) "a")))
  (is (string? ((wrap-catch inc #{Exception} ex-msg) "a")))
  (is (thrown? ClassCastException ((wrap-catch inc #{SecurityException} ex-msg) "a"))))
