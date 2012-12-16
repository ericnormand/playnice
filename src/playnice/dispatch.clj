(ns playnice.dispatch
  (:require [playnice.dispatch.protocol :as protocol])
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

(defn dispatch [dsp req]
  (let [p (split-path (:uri req))]
    (protocol/dispatch dsp (assoc req :path-segments p :remaining-path-segments p))))

(defn dassoc [dsp path handler]
  (protocol/dassoc dsp (split-path-kws path) handler))
