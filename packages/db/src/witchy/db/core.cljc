(ns witchy.db.core
  (:require
   [promesa.core :as p]
   [witchy.db.observation :refer [notify-updates-from-query]]
   [witchy.db.setup :as setup]
   [witchy.db.shared :as shared]))

(defonce ^:private ^:dynamic *notify-transaction?* false)
(defonce ^:private state (atom {}))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(defn- wrap-db-fn [f]
  (fn perform-db-fn [db-value statement]
    (let [[query args] (shared/format-sql statement)]
      (-> (f db-value query args)
          (p/catch (fn [e]
                     (log-error "[db] ERROR performing (" f ") statement:\n " query)
                     (log-error "[db] ->" e)
                     (p/rejected e)))))))

(defn init!
  ([impl] (init! impl nil))
  ([{:keys [db execute query]} schema]
   {:pre [(and db execute query)]}
   (reset! state {:db db
                  :execute (wrap-db-fn execute)
                  :query (wrap-db-fn query)})
   (when schema
     (swap! state setup/setup schema))))

(defn execute [statement]
  (let [{f :execute db :db} @state]
    (p/do
      (f db statement)
      (when-not *notify-transaction?*
        (binding [*notify-transaction?* true]
          (notify-updates-from-query statement))))))

(defn query [statement]
  (let [{f :query db :db} @state]
    (f db statement)))

(defn query-first [statement]
  (p/let [result-set (query statement)]
    (first result-set)))
