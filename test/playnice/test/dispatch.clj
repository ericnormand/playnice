(ns playnice.test.dispatch
  (:use playnice.core)
  (:use clojure.test))

(deftest handler-dispatch
  (is (= 200 (:status (dispatch (fn [req] {:status 200})
                                {:uri "/abc"}))))
  (is (= 400 (:status (dispatch (fn [req] {:status 400})
                                {:uri "/fdsfs/fdsfs"})))))

(def okhandler (fn [req] {:status 200 :headers {}}))

(defn pld [& rest]
  (if (empty? rest)
    (playnice.dispatch/pathlength)
    (apply assoc (pld) rest)))

(deftest path-length-dispatch
   (is (= 404 (:status (dispatch (pld) {:uri "/"}))))
   (is (= 404 (:status (dispatch (pld) {:uri "/abc"}))))
   (is (= 200 (:status (dispatch (pld 0 okhandler) {:uri "/"}))))
   (is (= 200 (:status (dispatch (pld 1 okhandler) {:uri "/hello"}))))
   (is (= 404 (:status (dispatch (pld 0 okhandler) {:uri "/hello"}))))
   (is (= 404 (:status (dispatch (pld 1 okhandler) {:uri "/hello/fdsfs"})))))

(deftest nil-dispatch
  (is (= 404 (:status (dispatch nil {:uri "/anything"})))))

(defn pvd [v s c p] (playnice.dispatch/pathvariable v s c p))

(deftest path-variable-dispatch
  (is (= 200 (:status (dispatch (pvd :greeting okhandler 0 nil) {:uri "/hello"}))))
  )

(defn psd [& rest]
  (if (empty? rest)
    (playnice.dispatch/pathsegment nil)
    (apply assoc (psd) rest)))

(defn pvld [vars]
  (playnice.dispatch/pathvariablelist vars))

(deftest path-segment-dispatch
  (is (= 404 (:status (dispatch (psd) {:uri "/dude/farout"}))))
  (is (= 200 (:status (dispatch (psd "hello" okhandler) {:uri "/hello"}))))
  (is (= 404 (:status (dispatch (psd "hello" okhandler) {:uri "/Hello"}))))

  (is (= 200 (:status (dispatch (pvd :greeting okhandler 0 []) {:uri "/hey"}))))
  
  (is (= 200 (:status (dispatch (pvld [(pvd :greeting okhandler 0 [])])
                                {:uri "/hey"}))))

  (is (= 200 (:status (dispatch (pvld [(pvd :fail nil 0 [])
                                       (pvd :ok #(if (= "hey" (:ok %))
                                                   {:status 200
                                                    :headers {}}
                                                   {:status 500
                                                    :headers {}}) 0 [])])
                                {:uri "/hey"}))))
  (is (= 200 (:status (dispatch (psd "hey" nil
                                     :variables (pvld [(pvd :fail nil 0 [])
                                                       (pvd :ok #(if (= "hey" (:ok %))
                                                                   {:status 200
                                                                    :headers {}}
                                                                   {:status 500
                                                                    :headers {}}) 0 [])]))
                                {:uri "/hey"}))))

  (is (= 404 (:status (dispatch (pvld [(pvd :fail (constantly {:status 404}) 2 [])
                                       (pvd :ok okhandler 7 [])])
                                {:uri "/hey"}))))

  (is (= 200 (:status (dispatch (pvd :greeting (psd "you" okhandler) 0 [])
                                {:uri "/hey/you"})))))


(deftest test-cases
  (let [c1 (pld 3 (psd "hello"
                       (pvld
                        [(pvd :there (psd "a" #(hash-map :status 200
                                                         :headers {}
                                                         :there (:there %)))
                              1 [])
                         (pvd :abcd (psd "b" #(hash-map :status 200
                                                        :headers {}
                                                        :abcd (:abcd %)))
                              1 [])])))]
    (is (= 404 (:status (dispatch c1 {:uri "/"                 :request-method :get}))))
    (is (= 404 (:status (dispatch c1 {:uri "/hello"            :request-method :get}))))
    (is (= 404 (:status (dispatch c1 {:uri "/hello/there"      :request-method :get}))))
    (is (= 200 (:status (dispatch c1 {:uri "/hello/anything/a" :request-method :get}))))
    (is (= 200 (:status (dispatch c1 {:uri "/hello/anything/b" :request-method :get}))))))

(deftest test-dispatch-assoc
  (is (dassoc nil "/" okhandler))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/" okhandler)
                       {:uri "/"
                        :request-method :get}))))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/" okhandler)
                       {:uri "/"
                        :request-method :options}))))
  (is (= 404 (:status (dispatch
                       (dassoc nil "/hello" okhandler)
                       {:uri "/"
                        :request-method :get}))))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/hello" okhandler)
                       {:uri "/hello"
                        :request-method :get}))))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/hello" okhandler)
                       {:uri "/hello"
                        :request-method :get}))))
  (is (= 404 (:status (dispatch
                       (dassoc nil "/hello/there" okhandler)
                       {:uri "/hello"
                        :request-method :get}))))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/hello/there" okhandler)
                       {:uri "/hello/there"
                        :request-method :get}))))
  (is (= 404 (:status (dispatch
                       (dassoc nil "/hello" okhandler)
                       {:uri "/hello/there"
                        :request-method :get}))))
  (is (= 200 (:status (dispatch
                       (dassoc nil "/hello/:there" okhandler)
                       {:uri "/hello/there"
                        :request-method :get}))))
  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello/:there" okhandler)
                   (dassoc "/hello/:abcd" okhandler))))
  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello/:there" okhandler)
                   (dassoc "/hello/:abcd" okhandler))))

  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello/:there" okhandler)
                   (dassoc "/hello/:there"  okhandler))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/a" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/b" (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:abcd/b" (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                           (dassoc "/hello/:there/a" (fn [req] {:status 200 :headers {} :there (:there req)})))
                       {:uri "/hello/there/a"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/a/2" (fn [req] {:status 200 :headers {} :there (:there
                                                                                                      req)}))
                           (dassoc "/hello/:abcd/b/2" (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/a/b/2" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/a/c/2" (fn [req]
                                                               {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/b/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/:a/b/2" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/:a/c/2" (fn [req]
                                                                {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/b/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/:a/b/2" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/:a/c/2" (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:abcd resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/a/:b/2" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/:a/c/2" (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:there/a/c/:2" (fn [req] {:status 200 :headers {} :there (:there req)}))
                           (dassoc "/hello/:abcd/:a/c/2" (fn [req] {:status 200 :headers {} :abcd (:abcd req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (let [resp (dispatch (-> nil
                           (dassoc "/hello/:abcd/:a/c/2" (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                           (dassoc "/hello/:there/a/c/:2" (fn [req] {:status 200 :headers {} :there (:there req)})))
                       {:uri "/hello/there/a/c/2"
                        :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= "there" (:there resp))))

  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello/:abcd/a/c/:0" (fn [req] {:status 200 :headers {} :abcd (:abcd req)}))
                   (dassoc "/hello/:there/a/c/:2" (fn [req] {:status 200 :headers {} :there (:there req)})))))

  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello" (fn [req] {:status 200 :headers {}}))
                   (dassoc "/hello" (fn [req] {:status 200 :headers {}})))))

  (is (thrown? Exception
               (-> nil
                   (dassoc "/hello" (fn [req] {:status 200 :headers {}}))
                   (dassoc "/hello" (fn [req] {:status 200 :headers {}})))))
  )
