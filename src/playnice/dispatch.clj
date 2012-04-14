(ns playnice.dispatch 
  (:require [clojure.string :as string])
  (:require [clojure.data.json :as json]))

(defn split-path [p]
  (vec (drop 1 (string/split p #"/"))))

(defn maybe-kw [s]
  (let [[_ p] (re-matches #":(.+)" s)]
    (if p (keyword p) s)))

(defn split-path-kws [p]
  (vec (map maybe-kw (drop 1 (string/split p #"/")))))

(defmulti dispatch (fn [dp _] (:type dp)) :default :404)

(defmethod dispatch nil
  [_ req]
  (dispatch {:type :404} req))

(defmethod dispatch :404
  [_ _]
  {:status 404
   :body "Not found."
   :dispatch true
   :headers {}
   :failed :404
   })

(defmethod dispatch :path-length
  [dp req]
  (let [ps (or (:path-segments req) (split-path (:uri req)))
        ln (count ps)
        sub (dp ln)]
    (if sub
      (dispatch sub (assoc req :path-segments ps))
      {:status 404
       :body "Not found."
       :dispatch true
       :headers {}
       :failed :path-length
       })
    ))

(defmethod dispatch :handler
  [dp req]
  ((:handler dp) req))

(defmethod dispatch :path-segment
  [dp req]
  (let [ps (or (:path-segments req) (split-path (:uri req)))
        rps (or (:remaining-path-segments req) ps)
        fps (first rps)
        rrps (rest rps)
        sub (dp fps)
        subresp (when sub (dispatch sub (assoc req :path-segments ps :remaining-path-segments rrps)))
        vars (dp :variables)]
    (if (and fps sub subresp (not (and (= 404 (:status subresp)) (:dispatch subresp))))
      subresp
      (let [svar (first (for [[v d] vars
                              :let [r (dispatch d (assoc req v fps :remaining-path-segments rrps))]
                              :when (not (and (= 404 (:status r))
                                              (:dispatch r)))]
                          r))]
        (if (and fps svar)
          svar
          {:status 404
           :body "Not found."
           :headers {}
           :dispatch true
           :failed :path-segment
           :which fps})))))

(def http-methods #{:options :get :head :post :put :delete :trace :connect :patch})

(defmethod dispatch :method
  [dp req]
  (let [method (:request-method req)
        sub (dp method)]
    (cond
     sub
     (dispatch sub req)
     (= :options method)
     {:status 200
      :headers {"Allow" (string/join ", " (set (cons "OPTIONS"
                                                     (for [[k _] dp
                                                           :when (http-methods k)]
                                                       (string/upper-case (name k))))))}
      :body ""}
     :otherwise
     {:status 405
      :headers {"Allow" (string/join ", " (set (cons "OPTIONS"
                                                     (for [[k _] dp
                                                           :when (http-methods k)]
                                                       (string/upper-case (name k))))))}
      :body "Method not allowed"})))

(defmulti dispatch-assoc (fn [dp _ _ _] (:type dp)) :default nil)

(defmethod dispatch-assoc nil
  [dp path method handler]
  (dispatch-assoc {:type :path-length} path method handler))

(defmethod dispatch-assoc :path-length
  [dp path method handler]
  (let [ps (split-path-kws path)
        ln (count ps)]
    (if (zero? ln)
      (update-in dp [ln] #(dispatch-assoc (or % {:type :method})       nil method handler))
      (update-in dp [ln] #(dispatch-assoc (or % {:type :path-segment}) ps  method handler)))))

(defn same-path? [a b]
  (and
   (= (count a) (count b))
   (every? identity (map #(or (= %1 %2) (and (keyword? %1) (keyword %2))) a b))))

(defmethod dispatch-assoc :path-segment
  [dp path method handler]
  (let [ps (if (string? path) (split-path-kws path) path)
        fps (first ps)
        rps (next ps)]
    (if (string? fps)
      (if rps
        (update-in dp [fps] #(dispatch-assoc (or % {:type :path-segment}) rps method handler))
        (if (and (dp fps) ((dp fps) method))
          (throw (RuntimeException. (str "Refusing to overwrite path 1 " path)))
          (update-in dp [fps] #(dispatch-assoc (or % {:type :method})       nil method handler))))
      ;; assuming it's a keyword
      (if rps
        (update-in dp [:variables] #(sort-by (fn [a] (a 2)) (conj (vec (remove (fn [co] (when (same-path? (co 3) rps) (throw (RuntimeException. (str "Refusing to overwrite path for " path))))) %1)) %2)) [fps (dispatch-assoc {:type :path-segment} rps method handler) (count (drop-while string? rps)) rps])
        (if (empty? (:variables dp))
          (assoc dp :variables [[fps (dispatch-assoc {:type :method}       nil method handler)]])
          (if-let [b (first (filter #(= fps (first %)) (:variables dp)))]
            (assoc dp :variables (vec (map #(if (= fps (first %))
                                              [fps (dispatch-assoc (second b) nil method handler)]
                                              %) (:variables dp))))
            (throw (RuntimeException. (str "Refusing to overwrite path 2 " path)))))))))

(defmethod dispatch-assoc :method
  [dp path method handler]
  (assoc dp method {:type :handler :handler handler}))

(defn trace-middleware [hndlr]
  (fn [req]
    (if (= :trace (:request-method req))
      {:status 200
       :headers {}
       :body (with-out-str
               (dorun (for [[h vs] (:headers req) v vs]
                        (println h ": " v))))}
      (hndlr req))))

(defprotocol JSONSafe
  (json-safe [x]))

(extend-protocol JSONSafe
  nil
  (json-safe [_] nil)
  Object
  (json-safe [o] (str o))
  clojure.lang.Keyword
  (json-safe [kw] kw)
  clojure.lang.LazySeq
  (json-safe [v] (vec (map json-safe v)))
  clojure.lang.IPersistentVector
  (json-safe [v] (vec (map json-safe v)))
  java.lang.Iterable
  (json-safe [v] (vec (map json-safe v)))
  clojure.lang.IPersistentMap
  (json-safe [o]
    (into {}
          (for [[k v] o]
            [(json-safe k) (json-safe v)]))))

(defprotocol WebOutput
  (to-text [v])
  (to-html [v])
  (to-json [v])
  (to-clj  [v]))

(extend-protocol WebOutput
  nil
  (to-text [v] "")
  (to-json [v] "{}")
  (to-clj  [v] "nil")
  String
  (to-text [v] v)
  (to-json [v] (json/json-str {:string v}))
  (to-clj  [v] (str \" v \"))
  (to-html [v] v)
  clojure.lang.IPersistentMap
  (to-clj  [v] (str v))
  (to-json [v] (json/json-str (json-safe v))))

(defn num-or-string [s]
  (try
    (Double/parseDouble s)
    (catch Exception e
      s)))

(defn parse-accept [accept]
  (sort-by #(vector (- (:q %)) (- (count %)))
           (for [tp (string/split accept #",")]
             (let [[type & options] (string/split tp #";")
                   options (into {:q 1.0} (for [o options :let [ [k v] (string/split o #"=")]]
                                            [(keyword (string/trim k)) (num-or-string v)]))
                   [a b] (string/split type #"/")
                   [b plus] (string/split b #"\+")
                   r (assoc options :a (string/trim a) :b (string/trim b))]
               (if plus
                 (assoc r :plus plus)
                 r)))))

(defn to-web [a b plus resp]
  (cond
   (and (= "text" a) (= "plain" b))
   (assoc-in (assoc resp :body (to-text (:body resp))) [:headers "content-type"] "text/plain;charset=UTF-8")
   (or (= "html" b) (= "xhtml" b))
   (assoc-in (assoc resp :body (to-html (:body resp))) [:headers "content-type"] "text/html;charset=UTF-8")
   (= "json" b)
   (assoc-in (assoc resp :body (to-json (:body resp))) [:headers "content-type"] "application/json;charset=UTF-8")
   (= "clojure" b)
   (assoc-in (assoc resp :body (to-clj (:body resp)))  [:headers "content-type"] "application/clojure;charset=UTF-8")
   (= "*" b)
   (do
     (println "Handler */*")
     (first (filter identity (map #(try (to-web (:a %) (:b %) (:plus %) resp) (catch Throwable e (println "Ignoring: " e) (.printStackTrace e) nil)) (parse-accept "text/plain,text/html,application/json,application/clojure")))))))

(defn web-output [accept resp]
  (or
   (first (filter identity (map #(try (to-web (:a %) (:b %) (:plus %) resp) (catch Throwable e (println "Ignoring: " e) (.printStackTrace e) nil)) (parse-accept accept))))
   {:body (str "Not acceptable: " accept " for value: " (:body resp))
    :status 406
    :headers {}}))

(defn web-output-middleware [hdlr]
  (fn [req]
    (let [resp (hdlr req)]
      (web-output (get-in req [:headers "accept"] "*/*") resp))))

(defprotocol ToRingResponse
  (to-ring-response [t]))

(defn wrap-fake-methods [h]
  (fn [req]
    (if (:__method (:params req))
      (h (assoc req :request-method (keyword (string/lower-case (:__method (:params req))))))
      (h req))))

(def unsafe-methods #{:post :delete :put :patch})

(defn wrap-redirect [h]
  (fn [req]
    (let [resp (h req)
          red (or (get-in req  [:params :redirect])
                  (get-in resp [:headers "location"]))]
      (if (and red
               (contains? unsafe-methods (:request-method req))
               (re-matches #".*html.*" (get-in resp [:headers "content-type"] "")))
        (assoc resp
          :headers (assoc (:headers resp) "location" red)
          :status 303)
        resp))))