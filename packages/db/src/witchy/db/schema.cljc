(ns witchy.db.schema
  "Schema transformations"
  (:require
   [medley.core :refer [map-vals]]
   [witchy.db.transforms :as transforms]))

(defn- extract-table-column-transforms
  "Columns may be declared with type:
   - `:transit` for the value to be stored in a transit-serialized format"
  [table-schema]
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

(defn- extract-primary-key [table-schema]
  (let [pk (or (:primary-key table-schema)
               (some->> (:columns table-schema)
                        (filter (partial some #{:primary-key}))
                        (ffirst)))]
    (assoc table-schema :primary-key pk)))

(def ^:private expand-table-schema
  (comp
   extract-table-column-transforms
   extract-primary-key))

(defn expand-schema
  "Expand schema to support dao operations, etc."
  [schema]
  (-> schema
      (update :tables (partial map-vals expand-table-schema))))
