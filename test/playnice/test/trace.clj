(ns playnice.test.trace
  (:use clojure.test)
  (:use playnice.middleware.trace)
  (:use playnice.dispatch))

(def okhandler (fn [req] {:status 200 :headers {}}))

(deftest test-trace-middleware
  (let [hdrs {"Hello" "Hello"
              "Goodbye" "Goodbye"}]
    (is (= 200 (:status ((wrap-trace #(dispatch okhandler %))
                         {:request-method :trace
                          :headers hdrs}))))
    (is (= 200 (:status ((wrap-trace #(dispatch okhandler %))
                         {:request-method :options
                          :headers hdrs
                          :uri "/fdsfs"}))))
    (is (= 404 (:status ((wrap-trace #(dispatch nil %))
                         {:request-method :get
                          :uri "/fdsfs"}))))
    (is (= 200 (:status ((wrap-trace #(dispatch nil %))
                         {:request-method :trace
                          :uri "/fdsfs"}))))))