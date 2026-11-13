(ns witchy.interceptors.diffing
  (:require
   [re-frame.core :refer [->interceptor get-coeffect get-effect]]))

(defn create-diffing
  "Create (as ->interceptor) a 'diffing' interceptor that monitors a
  path into the db and calls :after when that path changes.

  The :after function is called as:

    (after context resulting-value initial-value)

  Other config keys:

  :skip-if-initializing? - If truthy, :after will not be called if the path
                could not be found in the DB before your event handler ran.
  :skip-if-clearing? - If truthy, :after will not be called if the path could
                not be found in the DB after your event handler ran.

  If the key was not in the DB either before *or* after the event handler,
  :after will not be run regardless of the above two configs.
  "
  [{:keys [id path after skip-if-clearing? skip-if-initializing?]}]
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
              (let [initial-db (get-coeffect context :db {})
                    initial-value (get-path initial-db)
                    resulting-value (get-path (get-effect context :db initial-db))
                    initializing? (= ::not-found initial-value)
                    clearing? (= ::not-found resulting-value)]
                (if-not (or (and skip-if-initializing?
                                 initializing?)
                            (and skip-if-clearing?
                                 clearing?)
                            ; If both "initializing" *and* "clearing" then
                            ; actually we just didn't touch it
                            (and initializing? clearing?)
                            (= initial-value resulting-value))
                  (after
                   context
                   (when-not clearing?
                     resulting-value)
                   (when-not initializing?
                     initial-value))

                  context))))))
