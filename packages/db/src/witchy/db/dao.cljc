(ns witchy.db.dao
  "Common DAO helper functions"
  (:require
   [witchy.db.core :as db]
   [witchy.db.internal :as internal]
   [witchy.db.observation :refer [extract-tables]]))

(defn ->db [table-id map-like]
  (let [_table (internal/table-schema table-id)]
    ; TODO: Validate and/or transform any special columns
    map-like))

(defn ->clj [table-id-or-ids map-like]
  (let [table-ids (if (keyword? table-id-or-ids)
                    [table-id-or-ids]
                    table-id-or-ids)
        ; TODO: Assemble a combined dict of special columns.
        ; For now, we simply doall to verify the table IDs
        _tables (doall (map internal/table-schema table-ids))]
    ; TODO: Un-transform any special columns
    map-like))

(defn- replace-or-insert [method table-id map-likes]
  (when (seq map-likes)
    (db/execute {method table-id
                 :values (map (partial ->db table-id) map-likes)})))

(defn insert-maps [table-id map-likes]
  (replace-or-insert :insert-into table-id map-likes))

(defn insert-map [table-id map-like]
  (insert-maps table-id [map-like]))

(defn replace-maps [table-id map-likes]
  (replace-or-insert :replace-into table-id map-likes))

(defn replace-map [table-id map-like]
  (insert-maps table-id [map-like]))

(defn query
  "Unlike the core query function, this dao method will automatically
   transform any special/transformed columns"
  ([statement] (query (extract-tables statement) statement))
  ([tables statement]
   (->> (db/query statement)
        (map (partial ->clj tables) statement))))
