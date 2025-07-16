(ns witchy.expo
  (:require
   ["react-native" :as rn]
   [expo-navigation-bar :as NavigationBar]
   [expo-splash-screen :as SplashScreen]
   [promesa.core :as p]
   [react :as React]
   [reagent.core :as r]
   [shadow.expo :as expo]
   [witchy.components.error-boundary :refer [error-boundary]]
   [witchy.hot-reloads :refer [disable-rn-fast-reload hot-reloads]]))

(defonce ^:private last-navigation-state (atom nil))

(defn- on-state-change [new-state]
  (when goog.DEBUG
    (reset! last-navigation-state new-state))

  ; Return the state for convenient comp usage
  new-state)

(defn with-splash [{:keys [is-ready? component]}]
  (SplashScreen/preventAutoHideAsync)

  (fn witchy-splash-helper [p]
    (let [actually-ready? (is-ready?)]
      (React/useEffect
       (fn []
         (when actually-ready?
           (SplashScreen/hideAsync))
         js/undefined)
       #js [actually-ready?])
      (when actually-ready?
        [:f> component p]))))

(defonce ^:private has-initialized? (atom false))

(defn render-root [root-component]
  (when (compare-and-set! has-initialized? false true)
    ; Perform some initialization just once
    ; NOTE: RN's built-in "fast reload" is actually quite slow
    ; compared to shadow-cljs', and also interferes with it.
    ; We disable it for you automatically:
    (disable-rn-fast-reload))

  (expo/render-root
   (r/as-element [error-boundary
                  [:f> root-component
                   {:initial-state @last-navigation-state
                    :on-state-change on-state-change}]]))

  (swap! hot-reloads inc))

(defn configure-edge-to-edge! []
  (when (= "android" rn/Platform.OS)
    (p/do!
     (NavigationBar/setPositionAsync "absolute")
     (NavigationBar/setBackgroundColorAsync "#ffffff00"))))
