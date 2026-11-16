(ns witchy.reagent
  (:require
   ["react" :as React]
   [reagent.impl.batching :as batch]
   [reagent.impl.protocols :as p]
   [reagent.impl.template :as tmpl]
   [reagent.impl.util :as util]))

;; Imported from reagent.dom.client

(defn- reagent-root [^js js-props]
  ;; This will flush initial r/after-render callbacks.
  ;; Later that queue will be flushed on Reagent render-loop.
  (React/useEffect (fn []
                     (binding [util/*always-update* false]
                       (batch/flush-after-render)
                       js/undefined)))
  (binding [util/*always-update* true]
    (React/createElement (.-comp js-props))))

(defn as-root
  "Wrap a `[component]` for use as a reagent root, for platforms
   that reagent doesn't have first-class dom root support for."
  ([el] (as-root el nil))
  ([el compiler]
   (let [eff-compiler (or compiler tmpl/*current-default-compiler*)
         comp (fn [] (p/as-element eff-compiler el))
         js-props #js {}
         _ (set! (.-comp js-props) comp)]

     (React/createElement reagent-root js-props))))
