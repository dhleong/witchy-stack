(ns witchy.nav.fx
  (:require
   [re-frame.core :refer [reg-fx]]
   [witchy.nav.core :refer [navigation-ref params->js]]))

(reg-fx
  ::push!
  (fn [[screen params]]
    (if-let [^js navigation @navigation-ref]
      (if (.isReady navigation)
        (let [js-params (params->js params)]
          (println "NAVIGATE TO" screen params (pr-str js-params))
          (.navigate navigation (name screen) js-params))

        (println "WARN: navigation not ready"))
      (println "WARN: navigation-ref not set"))))

(reg-fx
  ::pop!
  (fn [_]
    (if-let [^js navigation @navigation-ref]
      (if (and (.isReady navigation)
               (.canGoBack navigation))
        (.goBack navigation)

        (println "WARN: navigation not ready or cannot go back; ready = "
                 (.isReady navigation)))
      (println "WARN: navigation-ref not set"))))

(defn- into-route [route-param]
  (cond
    (vector? route-param)
    (let [[route-name params] route-param]
      #js {:name (name route-name)
           :params (params->js params)})

    (map? route-param)
    #js {:name (name (:name route-param))
         :params (params->js (:params route-param))}))

(reg-fx
  ::reset-root
  (fn [{:keys [index routes]
        :or {index 0}}]
    (if-let [^js navigation @navigation-ref]
      (if (.isReady navigation)
        (let [state #js {:index index
                         :routes (-> (map into-route routes)
                                     (into-array))}]
          (.resetRoot navigation state))

        (println "WARN: navigation not ready"))
      (println "WARN: navigation-ref not set"))))
