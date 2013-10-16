# playnice

playnice contains a URL router and helpful Ring middleware for
creating HTTP-compliant web servers.

## Quick Start

Add this to your dependencies:

    [playnice "1.0.0"]

Then you can define a simple server like this.

    (ns my.namespace
      (:require [playnice.core  :as pn])
      (:require [ring.adapter.jetty :as jetty]))

    (defn homepage [req]
       {:status 200
        :body "Home page!"
        :headers {}})

    (defn greeting [{:keys [greeting greetee]}]
       {:status 200
        :body (str greeting ", " greetee)
        :headers {}})

    (def routes (-> nil
                  (pn/dassoc "/"                   homepage)
                  (pn/dassoc "/:greeting/:greetee" greeting)))

    (defn app [req] (pn/dispatch routes req))

    (jetty/run-jetty app 8080)

## Philosophy

1. Make it easier to serve correct HTTP responses.
2. Avoid confounding HTTP logic with business logic.
3. Make it easier for limited or non-standard clients to access resources.
4. As much as possible separate orthogonal functionality into composable units.

## Usage

playnice tries to be good about the HTTP Spec. It also tries to
separate out functionality into composable units. playnice works
really well with [Liberator].

[Liberator]: https://github.com/clojure-liberator/liberator

### Router

The router only does one thing: determine which handler to run for a
given URL. It is most readily compared to Compojure. It differs from
Compojure in the following ways:

  1. Compojure matches the HTTP Method before it matches the URL. This
  violates the HTTP Spec. Compojure will respond 404 when the URL
  matches and a POST was sent.

  The correct response is 405 Method Not Allowed. playnice does not
  route differently based on the request method. Instead, it is left
  to the handler to determine how to handle the request. I typically
  use Liberator to properly handle the resource.

  2. Compojure routes are dependent on the order in which they are
  defined. While this works well when there are a small number of
  non-overlapping routes, it becomes difficult to reason about when
  the number of routes increases.

  playnice is not order-dependent. As you add more routes, the new
  routes are checked against existing routes. If there is a conflict,
  an exception is thrown. This means that the operation of adding a
  new route is commutative. Storing the routes in an `atom` or `ref`
  gives you nice guarantees.

  3. Compojure routes are tried in order using a linear search.

  playnice routes are stored in a tree, meaning log(n) complexity.

  4. Compojure routes are built using handy macros.

  playnice routes are stored in a Clojure map and are built using
  functions. This means that a routing tree is first-class. This is
  especially helpful when aggregating routes from different files.

Using the router is quite easy. There are two functions to know:
`dassoc` and `dispatch` in the `playnice.core` namespace.

`dassoc` takes three arguments: the routing tree, a path pattern, and a
handler. It returns a new routing tree.

The path pattern is quite simple. The path pattern is a string that is
broken down into path segments. If the path segment starts with a ":",
then it is considered a variable segment. Otherwise, it is an
exact-match segment.

Examples:

  "/" -> Will match only the root
  "/some/path/to/something" -> All segments much match exactly
  "/user/:userid" -> Will match all paths of two segments that start with "/user/"

Exact-match segments are self-explanatory. Variable segments always
succed when the segment exists. They also store the entire segment
that is matched in the request. For instance:

 "/purchase/:purchaseid" matched on "/purchase/123" will assoc
 `:purchaseid "123"` in the Ring request object passed to the
 handler. You can then access it as `(:purchaseid req)`

The other function you need to know is called `dispatch`. It takes a
routing tree and a Ring request and returns a Ring
response. `dispatch` performs the routing logic required to choose a
handler and either runs the handler or returns a generic 404 response.

### `wrap-fake-methods`

Forms submitted in browsers can only perform GET and POST
requests. This is unfortunate since sometimes the operation that form
submission is supposed to perform maps better to DELETE or another
HTTP method. But you don't want to bake in the limitations of each
client into the logic of your server.

`wrap-fake-methods` lets you code your handler to use the correct
method for the operation at hand. This function is Ring middleware
which looks for the "__method" parameter (query or form post
parameter). If it exists, the value of that parameter is used as the
request method instead. This happens transparently so that your
handler does not have to know the difference. You can now POST to
`/purchase/123?__method=DELETE` to cancel the purchase order.

Reminder: this middleware depends on parameters from the query string
and/or the form post body. This middleware should be wrapped by the
middleware which parses those parameters.

### `wrap-fake-accept`

The HTTP standard defines the `Accept` header to notify the server
what mime-types it can handle in the response. This is great. However,
it can be quite inconvenient when headers cannot be set or if setting
them is a chore. For instance, how can you set headers in the `href`
of a link?

This middleware solves the problem by looking at the file name
extension of the URL. Let's say there is a resource at `/user/1138`
that returns information about a user. Normally, it will use HTTP Content
negotiation to determine what content-type to use. For this example,
we assume we can return results in JSON, HTML, and XML. Because of the
standard `Accept` header of most browsers, HTML will always be
preferred.

But in this case, we don't want HTML, we want JSON. We could alter the
`Accept` header. But we can't do that in a link. Or we could add a new
route just for JSON (`/user/1138/json`). But that is not scalable (3
handlers per resource?) and does not take full advantage of our
content negotiation machinery. What `wrap-fake-accept` does is to fake
an `Accept` header using the filename extension of the path.

If the last path segment contains a "." and doesn't end in a slash,
then this middleware considers it a filename with an extension. The
mime-type of the extension is looked up and set as the `Accept` header
for the handler. Also, the extension is removed from the path so that
the generic filename is used. If there is no extension, the request is
passed as-is.

Reminder: this middleware should happen before content negotiation.

Example:

    {:uri "/user/1138.json"
     :headers {"accept" "text/html, text/plain, image/jpeg, */*"
               ...}
     ...}

becomes

    {:uri "/user/1138"
     :headers {"accept" "application/json"}
               ...}
     ...}

### `wrap-ip-forwarding`

Many servers put a forwarding proxy in front of them. These servers
add security, load balancing, and performance features. However, they
often transform the requests. On our development machines, we often
will hit the server directly. This middleware un-transforms a
particular part of the request, namely, it restores the `:remote-addr`
portion of the Ring request to the value of the `X-Forwarded-For`, if
it exists. This way, we ensure that the behavior is the same
regardless of whether it is deployed or running on our local machine.

## Use it!

Please use it and send your patches. I'd love to hear your feedback.
Just create an issue in github.
https://github.com/ericnormand/playnice

## License

Copyright (C) 2012 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
