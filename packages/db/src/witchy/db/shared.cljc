(ns witchy.db.shared
  (:require
   [clojure.string :as str]
   [honey.sql :as sql]))

(def ^:private default-opts {:quoted true})

(sql/register-clause!
 :create-trigger
 (fn [_ x]
   (let [[name ine] (if (sequential? x)
                      x
                      [x nil])]
     [(->> [(sql/sql-kw :create)
            (sql/sql-kw :trigger)
            (when ine (sql/sql-kw ine))
            (sql/format-entity name)]
           (keep identity)
           (str/join " "))]))
 :create-view)

(defn format-trigger-condition
  "EG: :after [:update [:col1 :col2] :on :table-name]"
  [clause x]
  (let [[trigger-when & x] x
        ; NOTE: If provided, the update cols should be a seq. If not provided,
        ; the seq from (drop-last 2 x) should be empty
        [update-columns] (drop-last 2 x)
        [on table-name] (take-last 2 x)]
    [(->> (-> [(sql/sql-kw clause)]
              (into [(sql/sql-kw trigger-when)])
              (into (when (seq update-columns)
                      (assert (= :update trigger-when))
                      [(sql/sql-kw :of)
                       (str/join ", " (map sql/format-entity update-columns))]))
              (into [(sql/sql-kw on)
                     (sql/format-entity table-name)]))
          (keep identity)
          (str/join " "))]))

(doseq [condition [:before :after :instead-of]]
  (sql/register-clause!
   condition
   format-trigger-condition
   :raw))

(sql/register-clause!
 :begin
 (fn [_ x]
   (let [[nested & rest] (sql/format-dsl x)]
     (into [(->> [(sql/sql-kw :begin)
                  nested
                  ";"
                  (sql/sql-kw :end)]
                 (str/join " "))]
           rest)))
 :where)

(defn- compute-params [computed params]
  (reduce-kv
   (fn [m k v]
     (assoc m k (v m)))
   params
   computed))

(defn- format-sql-impl [statement]
  (let [opts (merge
              default-opts
              (select-keys statement [:params :pretty]))
        opts (update opts :params (fn [params]
                                    (cond->> params
                                      (some? (:params-defaults statement))
                                      (merge (:params-defaults statement))

                                      (some? (:params-computed statement))
                                      (compute-params (:params-computed statement)))))
        explain? (:explain-query? statement)
        statement (dissoc statement
                          :params :params-computed :params-defaults
                          :explain-query? :pretty)
        [query & args] (sql/format statement opts)]
    (if explain?
      [(str "EXPLAIN QUERY PLAN " query) args]
      [query args])))

(defn format-sql [statement]
  (try
    (format-sql-impl statement)
    (catch #? (:cljs :default :clj Throwable) e
      (#? (:cljs js/console.warn
           :clj println)
       "Failed to format statement: "
       "keys= " (str (keys statement))
       "input= "
       (try (-> statement
                (update :values (fn [v]
                                  (str "(" (count v) " items)")))
                (str))
            (catch #? (:cljs :default :clj Throwable) _
              (try
                (-> statement
                    (assoc :values "(redacted)")
                    (str))
                (catch #? (:cljs :default :clj Throwable) _
                  "(bad input?)")))))
      (println e)
      (throw e))))
