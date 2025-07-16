(ns witchy.interceptors.persister
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [->interceptor assoc-effect get-coeffect get-effect]]))

(defn- path->str [path]
  (->> path
       (map #(if (keyword? %) (name %) (str %)))
       (str/join ".")))

(defn create-path-persistor
  ([save-fx db-path] (create-path-persistor save-fx db-path identity))
  ([save-fx db-path data->fx-payload]
   (let [read-path (if (keyword? db-path)
                     [db-path]
                     db-path)
         get-path (fn get-path [db]
                    (get-in db read-path ::not-found))]
     (->interceptor
       :id (keyword 'witchy.persister (cond
                                        (keyword? db-path) db-path
                                        (coll? db-path) (path->str db-path)
                                        :else (str db-path)))
       :after (fn path-persister-after [context]
                (when goog.DEBUG
                  (when (some #(= :path (:id %))
                              (:stack context))
                    (println
                      "[WARN] Your path-persister for"
                      db-path
                      "should be installed BEFORE any (path) interceptors")))

                (let [initial-value (get-path (get-coeffect context :db {}))
                      resulting-value (get-path (get-effect context :db {}))]
                  (if-not (or (= ::not-found initial-value)
                              (= ::not-found resulting-value)
                              (= initial-value resulting-value))
                    (assoc-effect
                      context
                      save-fx
                      (data->fx-payload resulting-value))

                    context)))))))
