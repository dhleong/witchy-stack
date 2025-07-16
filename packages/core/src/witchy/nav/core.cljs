(ns witchy.nav.core
  (:require
   [cognitect.transit :as t]))

(defonce navigation-ref (atom nil))

(defn set-navigation-ref! [navigation]
  (reset! navigation-ref navigation))

(defn params->js [params]
  (when params
    (t/write (t/writer :json) params)))

(defn js->params [js-value]
  (when js-value
    (t/read (t/reader :json) js-value)))

