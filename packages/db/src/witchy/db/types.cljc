(ns witchy.db.types
  #?(:clj (:import
           [clojure.lang IFn])))

; NOTE: This is mostly in a separate NS to avoid HMR breaking things by
; instance? checks by redefining this type

(deftype Queryable [id format f tables query]
  IFn
  (-invoke [_this]
    (f))
  (-invoke [_this params]
    ; NOTE: For non-parametrized queries, this arity is on-success
    (f params))
  (-invoke [_this on-success params]
    (f on-success params)))

