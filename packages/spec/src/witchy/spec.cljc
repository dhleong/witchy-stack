(ns witchy.spec
  (:require
   [clojure.spec.alpha :as s]))

(defn validate [s value]
  (when-not (s/valid? s value)
    (throw
     (ex-info
      (s/explain-str s value)
      {:spec s
       :value value}))))
