(ns witchy.db.interop
  #? (:cljs (:require [reagent.core :as r])))

#? (:cljs (def ratom r/atom)
    :clj (def ratom atom))
