(ns playnice.middleware.fakemethods
  (:require [clojure.string :as string]))

(defn wrap-fake-methods [h]
  (fn [req]
    (if (:__method (:params req))
      (h (assoc req :request-method (keyword (string/lower-case (:__method (:params req))))))
      (h req))))