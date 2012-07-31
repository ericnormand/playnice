(ns playnice.middleware.redirect)

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