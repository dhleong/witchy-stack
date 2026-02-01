witchy web
==========

This module provides web-specific conveniences.

## Simple Routing

Reitit provides some great utilities; this package provides a simple layer atop it for common uses.

### Declaring Routes

```clojure
; modules/one/routes.cljs
(def routes
  [["/one"
    {:name :one
     :view one-view}]])

; modules/two/routes.cljs
(def routes
  [["/two"
    {:name :two
     :view two-view}]])

; router.cljs
(defn mount! []
  (nav/mount!
   (concat
     one-routes/routes
     two-routes/routes)
    {:use-fragment true}))
```

### Using Routes

Simply render `[router-view]` somewhere in the view hierarchy and the `:view` for the current route will be rendered there.

### Navigating

If you don't need to perform any side-effects, you can
simply use the `(href)` method to construct an appropriate URL path for use in eg `[:a {:href}]`.

If you do, `witchy.web.nav.fx` provides a `::navigate` fx that expects a route name and, optionally, a params map.

