(ns witchy.cofx.now
  (:require
   [re-frame.core :refer [inject-cofx reg-cofx]]))

(reg-cofx
 ::date-cofx
 (fn date-cofx [coeffects]
   (assoc coeffects :date #?(:cljs (js/Date.)
                             :clj (java.util.Date.)))))

(reg-cofx
 ::now-cofx
 (fn now-cofx [coeffects]
   (assoc coeffects :now #?(:cljs (js/Date.now)
                            :clj (System/currentTimeMillis)))))

(def inject-date (inject-cofx ::date-cofx))
(def inject-now (inject-cofx ::now-cofx))
