(ns playnice.core
  (:require [clojure.string :as string]
            [pantomime.mime :refer [mime-type-of]]
            [playnice.dispatch :as dispatch]))

(defn wrap-fake-methods
  "Middleware to wrap a handler to use the HTTP request method
  specified in the __method query or post parameter if it exists.

  Note that this middleware must be called after the query and/or post
  parameters are parsed using ring.middleware/wrap-params or similar."
  [hdlr]
  (fn [req]
    (if-let [request-method (and (= :post (:request-method req))
                                 (or (get-in req [:params :__method])
                                     (get-in req [:params "__method"])))]
      (hdlr (assoc req :request-method (keyword (string/lower-case request-method))))
      (hdlr req))))

(defn wrap-ip-forwarding
  "Middleware to wrap a handler to restore the :remote-addr value in
  the Ring request based on the X-Forwarded-For header. It also adds
  the X-Forwarded-By header, which will contain the IP address of the
  nginx proxy which passed on the request, if there was one."
  [hdlr]
  (fn [req]
    (if-let [ip (get-in req [:headers "x-forwarded-for"])]
      (hdlr (-> req
                (assoc :remote-addr ip)
                (assoc-in [:headers "x-forwarded-by"] (:remote-addr req))))
      (hdlr req))))

(defn wrap-fake-accept
  "Middleware to wrap a handler to change the Accept header based on
  the file extension in the URL path, if it exists."
  [hdlr]
  (fn [req]
    (if-let [[_ pre-extension extension] (re-matches #"(.+)(\.[^./]+)" (:uri req))]
      (hdlr (-> req
             (assoc-in [:headers "accept"] (mime-type-of extension))
             (assoc :uri pre-extension)))
      (hdlr req))))

(defn split-path [p]
  (vec (drop 1 (string/split p #"/"))))

(defn maybe-kw [s]
  (if-let [[_ p] (re-matches #":(.+)" s)]
    (keyword p)
    s))

(defn split-path-kws [p]
  (vec (map maybe-kw (split-path p))))

(defn dispatch
  "Dispatch a request (req) to the appropriate handler in the routing
  tree (dsp)."
  [dsp req]
  (let [p (split-path (:uri req))]
    (dispatch/dispatch dsp (assoc req
                             :path-segments p
                             :remaining-path-segments p))))

(defn dassoc
  "Associate a handler with a path in the routing tree (dsp).

   Returns a new routing tree.

   Will throw an exception when the path already exists in the tree."
  [dsp path handler]
  (dispatch/dassoc dsp (split-path-kws path) handler))
