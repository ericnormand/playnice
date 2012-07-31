(ns playnice.test.dispatch
  (:use playnice.dispatch)
  (:use clojure.test))

(deftest handler-dispatch
  (is (= 200 (:status (dispatch {:type :handler
                                 :handler (fn [req] {:status 200})}
                                {:uri "/abc"})))))

(def okhandler {:type :handler
                :handler (fn [req] {:status 200 :headers {}})})

(deftest path-length-dispatch
  (is (= 404 (:status (dispatch {:type :path-length} {:uri "/"}))))
  (is (= 404 (:status (dispatch {:type :path-length} {:uri "/abc"}))))
  (is (= 200 (:status (dispatch {:type :path-length
                                 0 okhandler}
                                {:uri "/"}))))
  (is (= 200 (:status (dispatch {:type :path-length
                                 1 okhandler}
                                {:uri "/hello"}))))
  (is (= 404 (:status (dispatch {:type :path-length
                                 0 okhandler}
                                {:uri "/hello"}))))
  (is (= 404 (:status (dispatch {:type :path-length
                                 1 okhandler}
                                {:uri "/hello/fdsfs"})))))

(deftest path-segment-dispatch
  (is (= 404 (:status (dispatch {:type :path-segment} {:uri "/dude/farout"}))))
  (is (= 200 (:status (dispatch {:type :path-segment
                                 "hello" okhandler}
                                {:uri "/hello"}))))
  (is (= 404 (:status (dispatch {:type :path-segment
                                 "hello" okhandler}
                                {:uri "/Hello"}))))
  (is (= 200 (:status (dispatch {:type :path-segment
                                 :variables [{:var :greeting :dsp okhandler}]}
                                {:uri "/hey"}))))
  (is (= 200 (:status (dispatch {:type :path-segment
                                 :variables [{:var :fail :dsp {:type :404}}
                                             {:var :ok :dsp {:type :handler
                                                             :handler
                                                             #(if (= "hey" (:ok %))
                                                                {:status 200
                                                                 :headers {}}
                                                                {:status 500
                                                                 :headers {}})}}]}
                                {:uri "/hey"}))))
  (is (= 200 (:status (dispatch {:type :path-segment
                                 "hey" {:type :404}
                                 :variables [{:var :fail :dsp {:type :404}}
                                             {:var :ok :dsp {:type :handler
                                                             :handler
                                                             #(if (= "hey" (:ok %))
                                                                {:status 200
                                                                 :headers {}}
                                                                {:status 500
                                                                 :headers {}})}}]}
                                {:uri "/hey"}))))
  (is (= 404 (:status (dispatch {:type :path-segment
                                 :variables [{:var :fail
                                              :dsp {:type :handler
                                                    :handler (constantly {:status 404})}
                                              :cnt 2}
                                             {:var :ok :dsp okhandler :cnt 7}]}
                                {:uri "/hey"}))))
  (is (= 200 (:status (dispatch {:type :path-segment
                                 :variables [{:var :greeting
                                              :dsp {:type :path-segment
                                                    "you" okhandler}}]}
                                {:uri "/hey/you"})))))

(deftest method-dispatch
  (is (= 405 (:status (dispatch {:type :method}
                                {:request-method :get}))))
  (is (= 200 (:status (dispatch {:type :method}
                                {:request-method :options}))))
  (is (= 200 (:status (dispatch {:type :method
                                 :get okhandler}
                                {:request-method :get}))))
  (let [resp (dispatch {:type :method
                        :get okhandler}
                       {:request-method :options})
        allow ((:headers resp) "Allow")]
    (cond
     (string? allow)
     (do
       (is (<= 0 (.indexOf allow "GET")))
       (is (<= 0 (.indexOf allow "OPTIONS")))
       (is (= -1 (.indexOf allow "PUT"))))
     (seq? allow)
     (is (some #(and
                 (is (<= 0 (.indexOf % "GET")))
                 (is (<= 0 (.indexOf % "OPTIONS")))
                 (is (= -1 (.indexOf % "PUT"))))
               allow))
     :otherwise
     (is false))))

(deftest test-cases
  (let [c1 {3 {"hello" {:variables [{:var :there
                                     :dsp {"a" {:get {:type :handler,
                                                      :handler (fn [req] {:status 200
                                                                         :headers {}
                                                                         :there (:there req)})},
                                                :type :method},
                                           :type :path-segment}}
                                    {:var :abcd
                                     :dsp {"b" {:get {:type :handler,
                                                      :handler (fn [req] {:status 200
                                                                         :headers {}
                                                                         :abcd (:abcd req)})},
                                                :type :method},
                                           :type :path-segment}}],
                        :type :path-segment},
               :type :path-segment},
            :type :path-length}]
    (is (= 404 (:status (dispatch c1 {:uri "/" :request-method :get}))))
    (is (= 404 (:status (dispatch c1 {:uri "/hello" :request-method :get}))))
    (is (= 404 (:status (dispatch c1 {:uri "/hello/there" :request-method :get}))))
    (is (= 200 (:status (dispatch c1 {:uri "/hello/anything/a" :request-method :get}))))
    (is (= 200 (:status (dispatch c1 {:uri "/hello/anything/b" :request-method :get}))))))

(deftest test-dispatch-assoc
  (is (dispatch-assoc nil "/" :get (fn [req] {:status 200 :headers {}})))
  (is (= 200 (:status (dispatch (dispatch-assoc nil "/" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/"
                                 :request-method :get}))))
  (is (= 200 (:status (dispatch (dispatch-assoc nil "/" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/"
                                 :request-method :options}))))
  (is (= 404 (:status (dispatch (dispatch-assoc nil "/hello" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/"
                                 :request-method :get}))))
  (is (= 200 (:status (dispatch (dispatch-assoc nil "/hello" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/hello"
                                 :request-method :get}))))
  (is (= 404 (:status (dispatch (dispatch-assoc nil "/hello/there" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/hello"
                                 :request-method :get}))))
  (is (= 200 (:status (dispatch (dispatch-assoc nil "/hello/there" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/hello/there"
                                 :request-method :get}))))
  (is (= 404 (:status (dispatch (dispatch-assoc nil "/hello" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/hello/there"
                                 :request-method :get}))))
  (is (= 200 (:status (dispatch (dispatch-assoc nil "/hello/:there" :get (fn [req] {:status 200 :headers {}}))
                                {:uri "/hello/there"
                                 :request-method :get}))))
  (is (thrown? Exception
               (-> nil
                   (dispatch-assoc "/hello/:there" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                   (dispatch-assoc "/hello/:abcd" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))))
  (is (thrown? Exception
               (-> nil
                   (dispatch-assoc "/hello/:there" :post (fn [req] {:status 200 :headers {} :there (:there req)}))
                   (dispatch-assoc "/hello/:abcd" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))))
  (is (-> nil
          (dispatch-assoc "/hello/:there" :post (fn [req] {:status 200 :headers {} :there (:there req)}))
          (dispatch-assoc "/hello/:there" :get  (fn [req] {:status 200 :headers {} :there (:there req)}))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/a" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/b" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:abcd/b" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                           (dispatch-assoc "/hello/:there/a" :get (fn [req] {:status 200 :headers {} :there (:there req)})))
                       {:uri "/hello/there/a"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/a/2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/b/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/a/b/2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/b/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/:a/b/2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/:a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/b/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/:a/b/2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/:a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:abcd resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/a/:b/2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/:a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:there/a/c/:2" :get (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dispatch-assoc "/hello/:abcd/:a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (let [resp (dispatch (-> nil
                           (dispatch-assoc "/hello/:abcd/:a/c/2" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                           (dispatch-assoc "/hello/:there/a/c/:2" :get (fn [req] {:status 200 :headers {} :there (:there req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))
  (is (thrown? Exception
               (-> nil
                   (dispatch-assoc "/hello/:abcd/a/c/:0" :get (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                   (dispatch-assoc "/hello/:there/a/c/:2" :get (fn [req] {:status 200 :headers {} :there (:there req)})))))
  (is (thrown? Exception
               (-> nil
                   (dispatch-assoc "/hello" :get (fn [req] {:status 200 :headers {}}))
                   (dispatch-assoc "/hello" :get (fn [req] {:status 200 :headers {}})))))
  (is (-> nil
          (dispatch-assoc "/hello" :get (fn [req] {:status 200 :headers {}}))
          (dispatch-assoc "/hello" :post (fn [req] {:status 200 :headers {}}))))
  )