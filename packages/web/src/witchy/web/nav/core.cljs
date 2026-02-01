(ns witchy.web.nav.core
  (:require
   [re-frame.alpha :refer [path reg-event-db reg-sub trim-v]]
   [reitit.core :as r]
   [reitit.frontend.easy :as rfe]
   [witchy.helpers :refer [<sub >evt]]))

(defonce ^:private mounted-router (atom nil))
(defonce ^:private mounted-opts (atom nil))

(reg-sub
 ::current-route
 :-> ::route)

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn mount!
  "Mount the given routes (which may also be a Router
   instance), 'starting' the router."
  ([routes] (mount! routes nil))
  ([routes {:keys [set-route-interceptors] :as opts}]
   (reg-event-db
    ::set-current-route
    [trim-v (path ::route) set-route-interceptors]
    (fn [_ [new-route]]
      new-route))

   (let [router (cond-> routes
                  (not (r/router? routes)) (r/router))]
     (reset! mounted-router router)
     (reset! mounted-opts opts)

     (rfe/start!
      router
      #(>evt [::set-current-route %])
      opts))))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn router-view
  "Render the current route. Your routes must look like:
  
  ```clj
  [[\"/my/route\"
    {:name :my/route
     :view reagent-view-fn}]]
  ```
  "
  ([] [router-view nil])
  ([fallback-view]
   (if-let [route (<sub [::current-route])]
     (let [view (get-in route [:data :view])]
       ; TODO: Handle lazy/loadable?
       [view (:parameters route)])
     fallback-view)))

(defn- require-router []
  (or @mounted-router
      (throw (ex-info "No router mounted" {}))))

(defn- ->href [match]
  (cond->> (:path match)
    ; NOTE: The :path returned by match-by-name doesn't
    ; "know about" the :use-fragment option, which
    ; defaults to true in reitit.
    (:use-fragment @mounted-opts true)
    (str "#")))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn href
  "Construct a link URL for the named path and optional params"
  ([path] (->href (r/match-by-name (require-router) path)))
  ([path params] (->href (r/match-by-name (require-router) path params))))
