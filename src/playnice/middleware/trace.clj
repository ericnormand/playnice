(ns playnice.middleware.trace)

(defn wrap-trace [hndlr]
  (fn [req]
    (if (= :trace (:request-method req))
      {:status 200
       :headers {}
       :body (with-out-str
               (dorun (for [[h vs] (:headers req) v vs]
                        (println h ": " v))))}
      (hndlr req))))