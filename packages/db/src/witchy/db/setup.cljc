(ns witchy.db.setup
  (:require
   #?(:cljs [applied-science.js-interop :as j])
   [promesa.core :as p]
   [witchy.db.migration :as migration]))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(defn setup [db-state schema]
  (assoc
   db-state
   :db
   (-> (p/let [db-value (:db db-state)
               {:keys [query execute]} db-state
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
                  (p/rejected e))))))

#_:clj-kondo/ignore
(comment
  (setup @gleemail.db/state))
