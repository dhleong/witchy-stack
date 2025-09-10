(ns witchy.db.setup
  (:require
   [witchy.db.migration :as migration]
   [witchy.db.schema :as schema]))

(defn setup [db-state schema]
  (let [schema (schema/expand-schema schema)]
    (cond-> db-state
      schema
      (assoc :schema schema)

      (:auto-migrate? schema true)
      (update :db migration/auto-migrate db-state schema {}))))
