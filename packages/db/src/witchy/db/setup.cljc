(ns witchy.db.setup
  (:require
   #?(:cljs [applied-science.js-interop :as j])
   [promesa.core :as p]
   [witchy.db.migration :as migration]
   [witchy.db.schema :as schema]))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(defn- auto-migrate [db commands schema]
  (-> (p/let [db-value db
              {:keys [query execute]} commands
              [result] (query db-value {:raw "PRAGMA user_version"})
              {initial-version :user_version} #?(:cljs (j/lookup result)
                                                 :clj result)]
        (migration/perform
         {:query (partial query db-value)
          :execute (partial execute db-value)}
         initial-version
         schema)
        db-value)
      (p/catch (fn [e]
                 (log-error "[migration] FAILED to setup db: " e)
                 (p/rejected e)))))

(defn setup [db-state schema]
  (let [schema (schema/expand-schema schema)]
    (cond-> db-state
      schema
      (assoc :schema schema)

      (:auto-migrate? schema true)
      (update :db auto-migrate db-state schema))))
