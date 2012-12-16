(def feature-version "0.0.5")
(def build-version (or (System/getenv "BUILD_NUMBER") "HANDBUILT"))
(def release-version (str feature-version "." build-version))
(def project-name "playnice")


(defproject project-name feature-version
  :description "A library to help you serve HTTP standard responses and be lenient with HTTP requests."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.1.0-beta2"]
                 [ring-json-params "0.1.3"]
                 [org.clojure/data.json "0.2.1"]
                 [ring "1.0.2"]])
