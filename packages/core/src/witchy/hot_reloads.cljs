(ns witchy.hot-reloads
  (:require
   ["react-native" :as rn :refer [NativeModules]]
   [react :as React]
   [reagent.core :as r]))

; NOTE: react-navigation doesn't notice that we've potentially hot-reloaded
; code, so may not re-render the current screen. This hack forces the issue
(defonce hot-reloads (r/atom 0))

(def deref-hot-reloads
  (if goog.DEBUG
    (fn deref-hot-reloads []
      @hot-reloads)

    (constantly nil)))

(def disable-rn-fast-reload
  (if goog.DEBUG
    (fn disable-rn-fast-reload []
      ; NOTE: Doing this automatically sends Android into a boot loop, since
      ; RN unconditionally reloads the JS after setting it... :'(
      ; TODO: Figure out how to do this gracefully on Android?
      (when-not (= "android" rn/Platform.OS)
        (NativeModules.DevSettings.setHotLoadingEnabled false)))

    (constantly nil)))

(when goog.DEBUG
  ; NOTE: Error boundaries don't seem to work well in RN
  ; because it insists on logging with a big modal in a way
  ; that doesn't seem to be interceptable. So, we clear
  ; the logs on hot reload for a more fluid dev experience.
  ; NOTE: However, we still *need* the error-boundary
  ; components we sprinkle in by default, because otherwise RN
  ; will give up rendering and we'll have to do a full reload
  ; anyway. It is the combination of catching the errors in an
  ; error-boundary and clearing the logs here that makes it
  ; nice and fluid.
  #_{:clj-kondo/ignore [:unused-private-var]}
  (defn- ^:dev/after-load hide-error-modals-on-hot-reload []
    (rn/LogBox.clearAllLogs)))

(def use-hot-reloads
  (if goog.DEBUG
    (fn use-hot-reloads [key]
      (let [[_ render!] (React/useState)]
        (React/useEffect
         (fn []
           (add-watch hot-reloads key (fn [_ v]
                                        (render! @v)))

           #(remove-watch hot-reloads key))
         #js [])))

    ; In production, this function is a no-op
    (constantly nil)))
