(ns witchy.db.dao
  "Common DAO helper functions"
  (:require
   [promesa.core :as p]
   [witchy.db.core :as db]
   [witchy.db.internal :as internal]
   [witchy.db.observation :refer [extract-tables]]
   [witchy.db.transforms :as transforms]))

(defn ->db [table-id map-like]
  (let [table (internal/table-schema table-id)]
    (transforms/perform-clj->db table map-like)))

(defn ->clj [table-id-or-ids map-like]
  (let [table-ids (if (keyword? table-id-or-ids)
                    [table-id-or-ids]
                    table-id-or-ids)
        tables (map internal/table-schema table-ids)]
    (reduce
     (fn [v table]
       (transforms/perform-db->clj table v))
     map-like
     tables)))

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
   (p/let [rows (db/query statement)]
     ; TODO: If rows rename a transformed column, we kinda need to
     ; transform that renamed column, too
     (map (partial ->clj tables) rows))))
