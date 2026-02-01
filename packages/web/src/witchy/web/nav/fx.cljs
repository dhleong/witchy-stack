(ns witchy.web.nav.fx
  (:require
   [re-frame.alpha :refer [reg-fx]]
   [reitit.frontend.easy :as rfe]))

(reg-fx
 ::navigate
 (fn [[route path-params]]
   (rfe/navigate route {:path-params path-params})))

(reg-fx
 ::navigate-replace
 (fn [[route path-params]]
   (rfe/navigate route {:path-params path-params
                        :replace true})))

(reg-fx
 ::navigate-back
 (fn [_]
   (js/window.history.back)))
