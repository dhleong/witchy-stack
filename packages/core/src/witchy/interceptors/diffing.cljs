(ns witchy.interceptors.diffing
  (:require
   [re-frame.core :refer [->interceptor get-coeffect get-effect]]))

(defn create-diffing
  "Create (as ->interceptor) a 'diffing' interceptor that monitors a
  path into the db and calls :after when that path changes. It will not
  be called if the path could not be found in the DB either before or after
  your event handler.

  The :after function is called as:

    (after context resulting-value initial-value)
  "
  [{:keys [id path after]}]
  {:pre [(some? id)
         (some? path)
         (ifn? after)]}
  (let [read-path (if (keyword? path)
                    [path]
                    path)
        get-path (fn get-path [db]
                   (get-in db read-path ::not-found))]
    (->interceptor
     :id id
     :before (when goog.DEBUG
               (fn diffing-verifier [context]
                 (when (some #(= :path (:id %))
                             (:stack context))
                   (println
                    "[WARN] Your diffing verifier" id "for"
                    path
                    "should be installed BEFORE any (path) interceptors"))

                 context))
     :after (fn diffing-after [context]
              (let [initial-value (get-path (get-coeffect context :db {}))
                    resulting-value (get-path (get-effect context :db {}))]
                (if-not (or (= ::not-found initial-value)
                            (= ::not-found resulting-value)
                            (= initial-value resulting-value))
                  (after context resulting-value initial-value)

                  context))))))
