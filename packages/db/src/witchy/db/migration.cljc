(ns witchy.db.migration
  (:require
   #?(:cljs [applied-science.js-interop :as j])
   [honey.sql :as sql]
   [promesa.core :as p]
   [witchy.db.internal :as internal]
   [witchy.db.shared :refer [format-sql]]))

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
  {:create-table [table-name :if-not-exists]
   :with-columns (concat
                  (:columns table)
                  (when-let [primary-key (:primary-key table)]
                    ; NOTE: If primary-key is not seq? then it was
                    ; just extracted by our transform code
                    (when (seq? primary-key)
                      [[(into [:primary-key] primary-key)]]))
                  (when-let [unique (:unique table)]
                    [(map
                      #(into [:unique nil] %)
                      unique)]))})

(defn- perform-create-table [cmds table-spec]
  (let [sql (format-create-table table-spec)]
    (execute-logging cmds sql)))

(defn- format-create-index [table-name index-name columns]
  {:create-index (-> [[index-name :if-not-exists]]
                     (conj (concat [table-name] columns)))})

(defn- perform-create-index [cmds table-name index-name columns]
  (let [sql (format-create-index table-name index-name columns)]
    (execute-logging cmds sql)))

(defn- perform-create-trigger [cmds [trigger-name spec]]
  (let [sql (assoc spec :create-trigger [trigger-name :if-not-exists])]
    (execute-logging cmds sql)))

(defn perform [{:keys [execute] :as cmds} initial-version schema {:keys [update-version?]}]
  (p/let [new-version
          (cond
            (= (:version schema) initial-version)
            nil

            ; Initial setup
            (= 0 initial-version)
            (p/do!
              ; TODO: Consider: PRAGMA journal_mode = WAL

             (p/doseq [spec (:tables schema)]
               (perform-create-table cmds spec)
               (let [[table-name table-spec] spec]
                 (p/doseq [[index-name & columns] (:indexes table-spec)]
                   (perform-create-index cmds table-name index-name columns))))

             (p/doseq [spec (:triggers schema)]
               (perform-create-trigger cmds spec))

             ; We migrated directly to the schema's version
             (:version schema))

            ; TODO: Migrations
            :else
            (println "TODO Migrate from " initial-version " -> " (:tables schema)))]
    (if new-version
      (p/do
        (when update-version?
          (execute {:raw (str "PRAGMA user_version = " new-version)}))
        (println "Migrated from " initial-version " -> " (:version schema))
        new-version)
      (println "DB up-to-date!"))))

(defn auto-migrate
  ([opts]
   (let [{:keys [db schema] :as commands} @internal/state]
     (auto-migrate db commands schema opts)))
  ([db commands schema {:keys [initial-version]}]
   (-> (p/let [db-value db
               {:keys [query execute]} commands
               use-pragma-version? (nil? initial-version)
               initial-version (or initial-version
                                   (p/let [[result] (query db-value {:raw "PRAGMA user_version"})
                                           {initial-version :user_version} #?(:cljs (j/lookup result)
                                                                              :clj result)]
                                     initial-version))]
         (perform
          {:query (partial query db-value)
           :execute (partial execute db-value)}
          initial-version
          schema
          {:update-version? use-pragma-version?})
         db-value)
       (p/catch (fn [e]
                  (log-error "[migration] FAILED to setup db: " e)
                  (p/rejected e))))))

#_:clj-kondo/ignore
(comment
  (honey.sql/format
   (format-create-table
    (nth (seq schema/current-tables) 2))))
