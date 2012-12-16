(ns playnice.dispatch.protocol
  (:require [clojure.set :as set])
  (:require [clojure.string :as string]))

(defmulti dispatch (fn [dsp _]
                     (if (fn? dsp)
                       :fn
                       (:type dsp))) :default nil)

(defmulti dassoc (fn [dsp _ _]
                   (if (fn? dsp)
                       :fn
                       (:type dsp))) :default nil)

(defn pathlength [] {:type :pathlength})
(defn pathvariable [var sub cnt pth] {:type :pathvariable :var var :sub sub :cnt cnt :pth pth})
(defn pathvariablelist [subs] {:type :pathvariablelist :subs subs})
(defn pathsegment [variables] {:type :pathsegment :variables variables})

(defmethod dispatch :fn
  [dsp req]
  (dsp req))

(defmethod dispatch :pathvariable
  [dsp req]
  (let [fps (first (:remaining-path-segments req))
        rps (rest (:remaining-path-segments req))
        req2 (assoc req (:var dsp) fps :remaining-path-segments rps)]
    (when fps
      (dispatch (:sub dsp) req2))))
(defmethod dassoc :pathvariable
  [dsp ps handler]
  (let [fps (first ps)
        rps (rest ps)]
    (if (= fps (:var dsp))
      (update-in dsp [:sub] #(dassoc % rps handler))
      (throw (ex-info "In variable dispatch: Refusing to overwrite."
                      {:path ps})))))

(defn same-path? [a b]
  (and
   (= (count a) (count b))
   (every? identity (map #(or (= %1 %2) (and (keyword? %1) (keyword? %2))) a b))))

(defmethod dispatch :pathvariablelist
  [dp req]
  (loop [subs (:subs dp)]
    (if (empty? subs)
      {:status 404
       :headers {}
       :body "Not found."
       :dispatch true
       :failed :path-variable-list}
      (let [resp (dispatch (first subs) req)]
        (if (not (:dispatch resp))
          resp
          (recur (rest subs)))))))
(defmethod dassoc :pathvariablelist
  [dp ps handler]
  (let [fps (first ps)
        rps (next ps)
        subs (or (:subs dp) [])]
    (cond
     (some #(same-path? (:pth %) rps) subs)
     (throw
      (ex-info "In path variable dispatch: refusing to overwrite."
               {:path ps}))
     (empty? rps)
     (assoc dp :subs (cons (pathvariable fps handler 0 [])
                           (:subs dp)))
     :otherwise
     (assoc dp :subs (sort-by :cnt
                              (conj subs
                                    (pathvariable
                                     fps
                                     (dassoc (pathsegment nil) rps handler)
                                     (count (drop-while string? rps))
                                     rps)))))))

(defmethod dispatch :pathsegment
  [dp req]
  (let [rps (:remaining-path-segments req)
        fps (first rps)
        rrps (rest rps)
        sub (get dp fps)
        req2 (assoc req :remaining-path-segments rrps)
        subresp (when sub (dispatch sub req2))]
    (if (and fps sub subresp (not (and (= 404 (:status subresp)) (:dispatch subresp))))
      subresp
      (dispatch (:variables dp) req))))
(defmethod dassoc :pathsegment
  [dp ps handler]
  (assert (seq ps) (str dp))
  (let [fps (first ps)
        rps (next ps)]
    (if (empty? rps)
      (if (and (string? fps)
               (get dp fps))
        (throw
         (ex-info "In path segment dispatch: Refusing to overwrite path."
                  {:path ps}))
        (cond
         (string? fps)
         (assoc dp fps handler)
         (keyword? fps)
         (update-in dp [:variables] #(dassoc (or % (pathvariablelist nil))
                                             ps
                                             handler))))
      
      (cond
       (string? fps)
       (update-in dp [fps] #(dassoc (or % (pathsegment nil)) rps handler))

       (keyword? fps)
       (update-in dp [:variables] #(dassoc (or % (pathvariablelist nil))
                                           ps
                                           handler))))))

(defmethod dispatch :pathlength
  [dsp req]
  (if-let [sub (get dsp (count (:remaining-path-segments req)))]
    (dispatch sub req)
    {:status 404
     :body "Not found."
     :dispatch true
     :headers {}
     :failed :path-length
     }))
(defmethod dassoc :pathlength
  [dsp ps handler]
  (let [ln (count ps)]
    (if (zero? ln)
      (if (dsp 0)
        (throw
         (ex-info "In path length dispatch: Refusing to overwrite root path."
                  {:path ps}))
        (assoc dsp 0 handler))
      (update-in dsp [ln] #(dassoc (or % (pathsegment nil)) ps handler)))))

(defmethod dispatch nil
  [_ _]
  {:status 404
   :body "Not found."
   :dispatch true
   :headers {}
   :failed :nil})
(defmethod dassoc nil
  [dsp path handler]
  (dassoc (pathlength) path handler))