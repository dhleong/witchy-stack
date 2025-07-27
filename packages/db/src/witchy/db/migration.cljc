(ns witchy.db.migration
  (:require
   [witchy.db.shared :refer [format-sql]]
   [honey.sql :as sql]
   [promesa.core :as p]))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(def ^:private format-obj #? (:cljs clj->js
                              :clj identity))

(defn- execute-logging [{:keys [execute]} sql]
  (-> (execute sql)
      (p/catch
       (fn [e]
         (log-error "[migration] executing: " (format-obj sql))
         (log-error "            sql:\n"
                    (first (format-sql (assoc sql :pretty true))))
         (p/rejected e)))))

(defn- format-create-table [[table-name table]]
  (let [columns (->> (:columns table)
                     (map (fn [column]
                            ; Keep only keyword or column descriptor values; we
                            ; might have some transformers in there...
                            (filter #(or (keyword? %)
                                         (and (vector? %)
                                              (keyword? (first %))))
                                    column))))]
    {:create-table [table-name :if-not-exists]
     :with-columns (concat columns
                           (when-let [primary-key (:primary-key table)]
                             [[(into [:primary-key] primary-key)]])
                           (when-let [unique (:unique table)]
                             [(map
                               #(into [:unique nil] %)
                               unique)]))}))

(defn- perform-create-table [cmds table-spec]
  (let [sql (format-create-table table-spec)]
    (execute-logging cmds sql)))

(defn- format-create-index [table-name index-name columns]
  {:create-index (-> [index-name]
                     (conj (concat [table-name] columns))
                     (conj :if-not-exists))})

(defn- perform-create-index [cmds table-name index-name columns]
  (let [sql (format-create-index table-name index-name columns)]
    (execute-logging cmds sql)))

(defn- perform-create-trigger [cmds [trigger-name spec]]
  (let [sql (assoc spec :create-trigger [trigger-name :if-not-exists])]
    (execute-logging cmds sql)))

(defn perform [{:keys [execute] :as cmds} initial-version schema]
  (p/let [new-version
          (cond
            (= (:version schema) initial-version)
            nil

            (= 0 initial-version)
            (p/do!
              ; TODO: Consider: PRAGMA journal_mode = WAL

             (p/doseq [spec (:tables schema)]
               (perform-create-table cmds spec)
               (let [[table-name table-spec] spec]
                 (p/doseq [[index-name & columns] (:indexes table-spec)]
                   (println "index=" index-name columns)
                   (perform-create-index cmds table-name index-name columns))))

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
