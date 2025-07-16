(ns witchy.screen
  (:require
   [applied-science.js-interop :as j]
   [promesa.core :as p]
   [react :as React]
   [reagent.core :as r]
   [shadow.lazy :as lazy]
   [witchy.components.error-boundary :refer [error-boundary]]
   [witchy.hot-reloads :refer [use-hot-reloads]]
   [witchy.nav.core :as nav])
  (:require-macros [witchy.screen]))

(defn- assign-display-name [o display-name]
  (doto o
    (js/Object.assign #js {:displayName display-name})))

(defn- render-component [component p]
  (r/as-element
   [error-boundary
    (if-let [params (j/get-in p [:route :params])]
      [:f> component (nav/js->params params) p]
      [:f> component p])]))

(defn- install-lazy-component [props lazy-component]
  (let [component (React/lazy
                   (fn []
                     (p/let [c (lazy/load lazy-component)]
                       #js {:default (r/reactify-component
                                      (fn LazyComponent [props]
                                        [c props]))})))]
    (assoc props :component component)))

(defn- install-component [props component]
  (let [display-name (or (when-let [n (:name (meta component))]
                           (str "Witchy_" n))
                         "WitchyComponent")]
    (assoc props :component
           (doto
            (partial render-component component)
             (assign-display-name display-name)))))

(defn- inflate-chunk-component [component-name lazy-require]
  (let [c (lazy-require)]
    (when-not c
      (throw (ex-info (str "Failed to inflate chunk component " component-name)
                      {:component-name component-name})))

    (doto (partial render-component c)
      (assign-display-name
       (or (j/get c :displayName)
           (str "Witchy_" (j/get c :name)))))))

(defn- install-chunk-component [{component-name :name :as props} lazy-require do-resolve]
  (assoc
   props :getComponent
   (memoize
    (if-not (and goog.DEBUG do-resolve)
      ; For production builds we can simply return the inflated
      ; component directly
      (fn witchy-prod-component-loader []
        (inflate-chunk-component component-name lazy-require))

      ; In debug builds, to provide hot reload goodness (without
      ; the need for RN's janky fast refresh) we first perform the
      ; lazy require so the module gets loaded, then provided a
      ; wrapper component that resolves the "current" var and
      ; uses that to render.
      (fn witchy-dev-component-loader []
        (let [lazy-resolve #(deref (do-resolve))
              inflate #(inflate-chunk-component component-name lazy-resolve)]
          (lazy-require)
          (fn witchy-hot-chunked-component
            ([prop1]
             (use-hot-reloads component-name)
             ((inflate) prop1))
            ([prop1 prop2]
             (use-hot-reloads component-name)
             ((inflate) prop1 prop2))
            ([prop1 prop2 prop3 & rest]
             (use-hot-reloads component-name)
             (apply
              (inflate)
              prop1 prop2 prop3 rest)))))))))

(defn wrap [navigator-type]
  (let [screen-component (j/get navigator-type :Screen)]
    (fn witchy-screen-factory [{route-name :name
                                :keys [component
                                       chunk-component
                                       lazy-component]
                                :as opts}]
      (let [p (cond-> {:name route-name}
                component (install-component component)
                chunk-component (install-chunk-component chunk-component
                                                         (:resolve opts))
                lazy-component (install-lazy-component lazy-component))]
        [:> screen-component p]))))
