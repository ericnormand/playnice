(ns playnice.test.trace
  (:use clojure.test)
  (:use playnice.middleware.weboutput))

(deftest test-parse-accept
  (is (= [{:q 1.0 :a "*" :b "*"}] (parse-accept "*/*")))
  (is (= [{:q 0.5 :a "*" :b "*"}] (parse-accept "*/*;q=.5")))
  (is (= [{:q 0.5 :a "text" :b "html"}] (parse-accept "text/html;q=.5")))
  (is (= [{:q 0.5 :a "text" :b "html" :level 1.0}] (parse-accept "text/html;q=.5;level=1")))
  (is (= [{:q 1.0 :a "text" :b "html"} {:q 0.5 :a "*" :b "*"}] (parse-accept "text/html, */*;q=.5")))
  (is (= [{:q 1.0 :a "text" :b "html"} {:q 0.5 :a "*" :b "*"}] (parse-accept "*/*;q=.5,text/html  ")))
  (is (= [{:q 1.0 :a "text" :b "html"} {:q 1.0 :a "text" :b "json"} {:q 1.0 :a "text" :b "a"} {:q 0.5 :a "*" :b "*"}] (parse-accept "*/*;q=.5,text/html,text/json,text/a  ")))
  (is (= [{:q 1.0 :a "text" :b "a" :level 9.0} {:q 1.0 :a "text" :b "html"} {:q 1.0 :a "text" :b "json"}  {:q 0.5 :a "*" :b "*"}] (parse-accept "*/*;q=.5,text/html,text/json,text/a;level=9  ")))
  (is (= [{:q 1.0 :a "text" :b "html" :plus "xml"}] (parse-accept "text/html+xml")))
  )

(deftest web-output-test
  (is (= 406 (:status (web-output "*/*" {:headers {} :status 200 :body (java.net.URL. "http://www.google.com/")}))))
  (is (= 200 (:status (web-output "*/*" {:headers {} :status 200 :body "hello"})))))
