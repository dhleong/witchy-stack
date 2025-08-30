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

(defn- verify-compatible-transforms [already-run proposed]
  (reduce-kv
   (fn [m k v]
     (let [existing (get already-run k :unset)]
       (cond
         (= existing :unset) m ; New transform
         (= existing v) (dissoc m k) ; Compatible
         :else (throw (ex-info (str "Incompatible transforms found for column " k)
                               {:column k
                                :existing-transform existing
                                :proposed-transform v})))))
   proposed
   proposed))

(defn ->clj [table-id-or-ids map-like]
  (let [table-ids (->> (if (keyword? table-id-or-ids)
                         [table-id-or-ids]
                         table-id-or-ids))
        tables (map internal/table-schema table-ids)]
    (loop [v map-like
           tables tables
           transforms {}]
      (if-some [table (first tables)]
        (let [to-apply (verify-compatible-transforms transforms (:transforms table))]
          (recur
           (transforms/perform-db->clj (assoc table :transforms to-apply) v)
           (next tables)
           (merge transforms to-apply)))

        ; Done!
        v))))

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
  (replace-maps table-id [map-like]))

(defn query
  "Unlike the core query function, this dao method will automatically
   transform any special/transformed columns"
  ([statement] (query (extract-tables statement) statement))
  ([tables statement]
   (p/let [rows (db/query statement)
           with-tables (into #{} (keys (:with statement)))
           transform-tables (->> tables (remove with-tables))]
     ; TODO: If rows rename a transformed column, we kinda need to
     ; transform that renamed column, too.
     ; TODO: In addition, it would be better to directly pass the transforms,
     ; so we can select based on the actual table's columns, rather than just
     ; blindly applying everything from each table
     ; NOTE: returning a vector is required for reg-query to work right
     (mapv (partial ->clj transform-tables) rows))))

(defn- build-where-clause [{:keys [primary-key]} value]
  (cond
    (keyword? primary-key)
    [:= primary-key value]

    (sequential? primary-key)
    (into [:and]
          (map
           (fn [column pk-value]
             [:= column (if (map? value)
                           ; ignore the MapEntry and use the original map
                          (get value column)
                          pk-value)])
           primary-key
           value))))

(defn by-primary-key
  "Read a single row from a table by its primary key. If the table's primary
   key is compound, you may provide either a tuple or a map of column names"
  [table-id primary-key]
  (p/let [table (internal/table-schema table-id)
          where-clause (build-where-clause table primary-key)
          rows (query [table-id]
                      {:select :*
                       :from table-id
                       :where where-clause})]
    (first rows)))

(defn update-primary-key [table-id primary-key map-like]
  (p/let [table (internal/table-schema table-id)
          where-clause (build-where-clause table primary-key)]
    (db/execute {:update table-id
                 :set (->db table-id map-like)
                 :where where-clause})))

(defn delete-primary-keys
  "Delete a collection of entries by their primary keys in the given table.
   See by-primary-key for how to provide a primary key."
  [table-id primary-keys]
  (p/let [table (internal/table-schema table-id)
          where-clauses (map (partial build-where-clause table) primary-keys)
          rows (query [table-id]
                      {:delete-from table-id
                       :where (into [:or] where-clauses)})]
    (first rows)))
