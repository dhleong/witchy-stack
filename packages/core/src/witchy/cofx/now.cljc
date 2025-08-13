(ns witchy.cofx.now
  (:require
   [re-frame.core :refer [inject-cofx reg-cofx]]))

(reg-cofx
 ::date-cofx
 (fn now-cofx [coeffects]
   (assoc coeffects :date #?(:cljs (js/Date.)
                             :clj (java.util.Date.)))))

(def inject-date (partial inject-cofx ::date-cofx))
