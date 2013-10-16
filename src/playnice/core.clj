(ns playmice.core
  (:require [clojure.string :as string]
            [pantomime.mime :refer [mime-type-of]]
            [playnice.dispatch :as dispatch]))

(defn wrap-fake-methods [h]
  (fn [req]
    (if-let [request-method (or (get-in req [:params :__method])
                                (get-in req [:params "__method"]))]
      (h (assoc req :request-method (keyword (string/lower-case request-method))))
      (h req))))

(defn wrap-ip-forwarding [hdlr]
  (fn [req]
    (if-let [ip (get-in req [:headers "x-forwarded-for"])]
      (hdlr (-> req
                (assoc :remote-addr ip)
                (assoc-in [:headers "x-forwarded-by"] (:remote-addr req))))
      (hdlr req))))

(defn wrap-fake-accept [h]
  (fn [req]
    (if-let [[_ pre-extension extension] (re-matches #"(.+)(\.[^./]+)" (:uri req))]
      (h (-> req
             (assoc-in [:headers "accept"] (mime-type-of extension))
             (assoc :uri pre-extension)))
      (h req))))

(defn split-path [p]
  (vec (drop 1 (string/split p #"/"))))

(defn maybe-kw [s]
  (if-let [[_ p] (re-matches #":(.+)" s)]
    (keyword p)
    s))

(defn split-path-kws [p]
  (vec (map maybe-kw (split-path p))))

(defn dispatch [dsp req]
  (let [p (split-path (:uri req))]
    (dispatch/dispatch dsp (assoc req
                             :path-segments p
                             :remaining-path-segments p))))

(defn dassoc [dsp path handler]
  (dispatch/dassoc dsp (split-path-kws path) handler))
