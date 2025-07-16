(ns witchy.cofx.now
  (:require
   [re-frame.core :refer [reg-cofx]]))

(reg-cofx
  ::cofx
  (fn now-cofx [coeffects]
    (assoc coeffects :now (js/Date.))))
