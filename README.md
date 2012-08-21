# playnice

A library to help you serve HTTP standard responses and be lenient with HTTP requests.

## Philosophy

1. Make it easier to serve HTTP standard responses without confounding
   HTTP logic with business logic.
2. Make it easier for limited or non-standard clients to access resources.
3. As much as possible separate orthogonal functionality into composable chunks.

## Quick Start

Add this to your dependencies:

    [playnice "0.0.4"]

Then you can define a simple server like this.

    (ns my.namespace
      (:require [playnice.dispatch  :as dis])
      (:require [ring.adapter.jetty :as jetty]))
    
    (def routes (-> nil
                  (dis/dassoc "/" :get (constantly "Home page!"))
                  (dis/dassoc "/:greeting/:greetee" :get #(str (:greeting %) ", " (:greetee %) "!"))))
    
    (defn app [req] (dis/dispatch routes req))
    
    (jetty/run-jetty app 8080)

## Use it!

Please use it and send your patches. I'd love to hear your feedback.
Just create an issue in github.
https://github.com/ericnormand/playnice

## License

Copyright (C) 2012 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
