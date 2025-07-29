(ns witchy.db.schema
  "Schema transformations"
  (:require
   [medley.core :refer [map-vals]]
   [witchy.db.transforms :as transforms]))

(defn- extract-table-column-transforms [table-schema]
  (loop [table-schema' (assoc table-schema :columns [])
         columns (:columns table-schema)]
    (if-some [column (first columns)]
      (recur
       (let [transform-type (transforms/from-column-type (second column))
             column' (if transform-type
                       (assoc column 1 (transforms/db-column-type transform-type))
                       column)]
         (cond-> table-schema'
           :always
           (update :columns conj column')

           (some? transform-type)
           (assoc-in [:transforms (first column)] transform-type)))
       (next columns))

      ; Done!
      table-schema')))

(defn- extract-column-transforms
  "Columns may be declared with type:
   - `:transit` for the value to be stored in a transit-serialized format"
  [schema]
  (update schema :tables (partial map-vals extract-table-column-transforms)))

(defn expand-schema
  "Expand schema to support dao operations, etc."
  [schema]
  (-> schema
      (extract-column-transforms)))
