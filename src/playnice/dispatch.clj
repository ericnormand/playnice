(ns playnice.dispatch 
  (:require [clojure.string :as string])
  (:require [clojure.data.json :as json])
  (:require [clojure.set :as set]))

(defn split-path [p]
  (vec (drop 1 (string/split p #"/"))))

(defn maybe-kw [s]
  (let [[_ p] (re-matches #":(.+)" s)]
    (if p (keyword p) s)))

(defn split-path-kws [p]
  (vec (map maybe-kw (split-path p))))

(defmulti dispatch (fn [dp _] (:type dp :404)) :default :404)

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
  (let [ps (or (:path-segments req)
               (split-path (:uri req)))
        sub (dp (count ps))]
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
  (let [ps (or (:path-segments req)
               (split-path (:uri req)))
        rps (or (:remaining-path-segments req) ps)
        fps (first rps)
        rrps (rest rps)
        sub (dp fps)
        subresp (when sub (dispatch sub (assoc req :path-segments ps :remaining-path-segments rrps)))
        vars (dp :variables)]
    (if (and fps sub subresp (not (and (= 404 (:status subresp)) (:dispatch subresp))))
      subresp
      (let [svar (first (for [{:keys [var dsp]} vars
                              :let [r (dispatch dsp (assoc req var fps :remaining-path-segments rrps))]
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
    (if sub
      (dispatch sub req)
      (let [methods (conj (set/intersection (set (keys dp)) http-methods) :options)
            methods (string/join ", " (map #(string/upper-case (name %)) methods))
            hdrs {"Allow" methods}]
        (if (= :options method)
          {:status 200
           :headers hdrs
           :body ""}
          
          {:status 405
           :headers hdrs
           :body "Method not allowed"})))))

(defmulti dispatch-assoc (fn [dp _ _ _] (:type dp)) :default nil)

(defmethod dispatch-assoc nil
  [dp path method handler]
  (dispatch-assoc {:type :path-length} path method handler))

(defmethod dispatch-assoc :path-length
  [dp path method handler]
  (let [ps (split-path-kws path)
        ln (count ps)
        default {:type (if (zero? ln) :method :path-segment)}]
    (update-in dp [ln] #(dispatch-assoc (or % default) ps method handler))))

(defn same-path? [a b]
  (and
   (= (count a) (count b))
   (every? identity (map #(or (= %1 %2) (and (keyword? %1) (keyword %2))) a b))))

;; path-segment dispatching: a map with the following properties:
;; :type :path-segment
;; :variables (seq of variables, each a map of
(defmethod dispatch-assoc :path-segment
  [dp path method handler]
  (let [ps (if (string? path) (split-path-kws path) path)
        fps (first ps)
        rps (next ps)
        default (if rps
                  {:type :path-segment}
                  {:type :method})]
    (cond
     (string? fps)
     (update-in dp [fps] #(dispatch-assoc (or % default) rps method handler))

     ;; variable
     rps
     (if (some #(same-path? (:pth %) rps) (dp :variables))
       (throw (ex-info "In path dispatch: Refusing to overwrite."
                       {:path path
                        :remaining rps}))
       (let [vs (sort-by :cnt
                         (conj (dp :variables)
                               {:var fps
                                :dsp (dispatch-assoc {:type :path-segment} rps method handler)
                                :cnt (count (drop-while string? rps))
                                :pth rps}))]
         (assoc dp :variables vs)))
     
     (empty? (:variables dp))
     (assoc dp :variables [{:var fps
                            :dsp (dispatch-assoc {:type :method} nil method handler)
                            :cnt 0
                            :pth nil}])

     :otherwise
     (if-let [b (first (filter #(= fps (:var %)) (:variables dp)))]
       (assoc dp :variables (map #(if (= fps (:var %))
                                    {:var fps
                                     :dsp (dispatch-assoc (:dsp b) nil method handler)
                                     :cnt 0
                                     :pth nil}
                                    %)
                                 (:variables dp)))
       (throw (ex-info "In path dispatch: Refusing to overwrite path."
                       {:path path}))))))

(defmethod dispatch-assoc :method
  [dp path method handler]
  (if (dp method)
    (throw (ex-info "In method dispatch: Refusing to overwrite handler."
                    {:path path :method method}))
    (assoc dp method {:type :handler :handler handler})))

