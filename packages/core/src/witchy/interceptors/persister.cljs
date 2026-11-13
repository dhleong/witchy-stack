(ns witchy.interceptors.persister
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [assoc-effect]]
   [witchy.interceptors.diffing :refer [create-diffing]]))

(defn- path->str [path]
  (->> path
       (map #(if (keyword? %) (name %) (str %)))
       (str/join ".")))

(defn create-path-persistor
  ([save-fx db-path] (create-path-persistor save-fx db-path identity))
  ([save-fx db-path data->fx-payload]
   (create-diffing
    {:id (keyword 'witchy.persister (cond
                                      (keyword? db-path) db-path
                                      (coll? db-path) (path->str db-path)
                                      :else (str db-path)))
     :path db-path
     :after (fn path-persister-after [context resulting-value]
              (assoc-effect
               context
               save-fx
               (data->fx-payload resulting-value)))})))
