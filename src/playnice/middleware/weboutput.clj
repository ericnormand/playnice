(ns playnice.middleware.weboutput
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as string]))


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
    (into {} (map #(map json-safe %) o))))

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
