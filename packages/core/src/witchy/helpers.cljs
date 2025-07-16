(ns witchy.helpers
  (:require [re-frame.core :refer [subscribe dispatch]]))

(def <sub (fn [subscription]
            (if-some [sub (subscribe subscription)]
              @sub
              (throw (ex-info (str "Invalid subscription: " subscription ". Does it exist yet?") {})))))

(def >evt dispatch)
