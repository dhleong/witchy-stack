(ns witchy.db.migration
  (:require
   [witchy.db.shared :refer [format-sql]]
   [honey.sql :as sql]
   [promesa.core :as p]))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(def ^:private format-obj #? (:cljs clj->js
                              :clj identity))

(defn- format-create-table [[table-name table]]
  (let [columns (->> (:columns table)
                     (map (fn [column]
                            ; Keep only keyword values; we might have
                            ; some transformers in there...
                            (filter keyword? column))))]
    ; TODO: unique keys
    {:create-table [table-name :if-not-exists]
     :with-columns (concat columns
                           (when-let [primary-key (:primary-key table)]
                             [[(into [:primary-key] primary-key)]]))}))

(defn- perform-create-table [{:keys [execute]} table-spec]
  (let [sql (format-create-table table-spec)]
    (-> (execute sql)
        (p/catch
         (fn [e]
           (log-error "[migration] executing: " (format-obj sql))
           (log-error "            sql:\n"
                      (first (format-sql (assoc sql :pretty true))))
           (p/rejected e))))))

(defn- perform-create-trigger [{:keys [execute]} [trigger-name spec]]
  (let [sql (assoc spec :create-trigger [trigger-name :if-not-exists])]
    (-> (execute sql)
        (p/catch
         (fn [e]
           (log-error "[migration] executing: " (format-obj sql))
           (log-error "            sql:\n"
                      (first (format-sql (assoc sql :pretty true))))
           (p/rejected e))))))

(defn perform [{:keys [execute] :as cmds} initial-version schema]
  (p/let [new-version
          (cond
            (= (:version schema) initial-version)
            nil

            (= 0 initial-version)
            (p/do!
              ; TODO: Consider: PRAGMA journal_mode = WAL

             (p/doseq [spec (:tables schema)]
               ; TODO: indexes
               (perform-create-table cmds spec))

             (p/doseq [spec (:triggers schema)]
               (perform-create-trigger cmds spec)))

            ; TODO: Migrations
            :else
            (println "TODO Migrate from " initial-version " -> " (:tables schema)))]
    (if new-version
      (p/do
        (execute {:raw (str "PRAGMA user_version = " new-version)})
        (println "Migrated from " initial-version " -> " (:version schema)))
      (println "DB up-to-date!"))))

#_:clj-kondo/ignore
(comment
  (honey.sql/format
   (format-create-table
    (nth (seq schema/current-tables) 2))))
