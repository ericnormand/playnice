(ns playnice.dispatch.protocol
  (:require [clojure.set :as set])
  (:require [clojure.string :as string]))

(defprotocol Dispatch
  (dispatch [dsp req])
  (dassoc   [dsp path method handler]))

(defrecord PathLengthDispatch       [])
(defrecord PathVariableDispatch     [var sub cnt pth])
(defrecord PathVariableListDispatch [subs])
(defrecord PathSegmentDispatch      [variables])
(defrecord MethodDispatch           [])

(def http-methods #{:options :get :head :post :put :delete :trace :connect :patch})

(extend-type MethodDispatch
  Dispatch
  (dispatch [dp req]
    (let [method (:request-method req)
          sub (get dp method)]
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
             :body (str "Method not allowed (" (string/upper-case (name method)) ")")})))))
  (dassoc [dp ps method handler]
    (if (get dp method)
     (throw (ex-info "In method dispatch: Refusing to overwrite handler."
                     {:path ps :method method}))
     (assoc dp method handler))))

(extend-type clojure.lang.Fn
  Dispatch
  (dispatch [hndlr req]
    (hndlr req)))

(extend-type PathVariableDispatch
  Dispatch
  (dispatch [dp req]
    (let [fps (first (:remaining-path-segments req))
          rps (rest (:remaining-path-segments req))
          req2 (assoc req (:var dp) fps :remaining-path-segments rps)]
      (when fps
        (dispatch (:sub dp) req2))))
  (dassoc [dsp ps method handler]
    (let [fps (first ps)
          rps (rest ps)]
      (if (= fps (:var dsp))
        (update-in dsp [:sub] #(dassoc % rps method handler))
        (throw (ex-info "In variable dispatch: Refusing to overwrite."
                        {:path ps}))))))

(defn same-path? [a b]
  (and
   (= (count a) (count b))
   (every? identity (map #(or (= %1 %2) (and (keyword? %1) (keyword? %2))) a b))))

(extend-type PathVariableListDispatch
  Dispatch
  (dispatch [dp req]
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
  (dassoc [dp ps method handler]
    (let [fps (first ps)
          rps (next ps)
          subs (or (:subs dp) [])
          subs2 (map #(if (same-path? (:pth %) rps)
                        (dassoc % ps method handler)
                        %) subs)]
      (if (not (= subs subs2))
        (assoc dp :subs subs2)
        (assoc dp :subs (sort-by :cnt
                                 (conj subs
                                       (PathVariableDispatch. 
                                        fps
                                        (dassoc (PathSegmentDispatch. nil) rps method handler)
                                        (count (drop-while string? rps))
                                        rps))))))))

(extend-type PathSegmentDispatch
  Dispatch
  (dispatch [dp req]
    (let [rps (:remaining-path-segments req)
          fps (first rps)
          rrps (rest rps)
          sub (get dp fps)
          req2 (assoc req :remaining-path-segments rrps)
          subresp (when sub (dispatch sub req2))]
      (if (and fps sub subresp (not (and (= 404 (:status subresp)) (:dispatch subresp))))
        subresp
        (dispatch (:variables dp) req))))
  (dassoc [dp ps method handler]
    (let [fps (first ps)
          rps (next ps)
          default (if rps
                    (PathSegmentDispatch. nil)
                    (MethodDispatch.))]
      (cond
       (string? fps)
       (update-in dp [fps] #(dassoc (or % default) rps method handler))

       (keyword? fps)
       (update-in dp [:variables] #(dassoc (or % (PathVariableListDispatch. nil))
                                           ps
                                           method
                                           handler))

       ;; no path
       :otherwise
       (dassoc (MethodDispatch.) ps method handler)))))


(extend-type PathLengthDispatch
  Dispatch
  (dispatch [dsp req]
    (if-let [sub (get dsp (count (:remaining-path-segments req)))]
      (dispatch sub req)
      {:status 404
       :body "Not found."
       :dispatch true
       :headers {}
       :failed :path-length
       }))
  (dassoc [dsp ps method handler]
    (let [ln (count ps)
          default (if (zero? ln) (MethodDispatch.) (PathSegmentDispatch. nil))]
      (update-in dsp [ln] #(dassoc (or % default) ps method handler)))))

(extend-type nil
  Dispatch
  (dispatch [_ _]
    {:status 404
     :body "Not found."
     :dispatch true
     :headers {}
     :failed :nil})
  (dassoc [dsp path method handler]
    (dassoc (PathLengthDispatch.) path method handler)))