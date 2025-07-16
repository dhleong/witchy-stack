(ns witchy.components.error-boundary
  (:require
   ["react-native" :as rn]
   [reagent.core :as r]))

(when goog.DEBUG
  ; in debug builds, we can auto-retry rendering error'd components
  ; every time a load occurs
  (def ^:private active-err-atoms (atom #{}))

  #_{:clj-kondo/ignore [:unused-private-var]}
  (defn- ^:dev/after-load clear-errors []
    (swap! active-err-atoms (fn [atoms]
                              (doseq [a atoms]
                                (reset! a nil))

                              ; clear
                              #{}))))

(defn- default-error-view [info]
  ; TODO: This could be nicer, but for the most part we
  ; probably won't see it anyway...
  [:> rn/View {:background-color :red}
   [:> rn/Text {:color :black}
    (str info)]])

(defn error-boundary [& _]
  (r/with-let [err (r/atom nil)
               info-atom (r/atom nil)]
    (r/create-class
     {:display-name "Error Boundary"

      :component-did-catch (fn [_this error info]
                             (js/console.warn error info)

                             (when goog.DEBUG
                                ; enqueue the atom for auto-clearing
                               (swap! active-err-atoms conj err))

                             (reset! err error)
                             (reset! info-atom info))

      :get-derived-state-from-error
      (fn [error]
         ; NOTE: It's unclear why, but it seems like
         ; component-did-catch may not consistently get called,
         ; so we set this in get-derived-state as well
        (reset! err error)
        #js {:error error})

      :reagent-render (fn [& children]
                        (let [props (when (map? (first children))
                                      (first children))
                              children (if (map? (first children))
                                         (rest children)
                                         children)]
                          (if-let [e @err]
                            [default-error-view
                             {:props props
                              :info @info-atom
                              :error e}]

                            (into [:<>] children))))})))
