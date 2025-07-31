(ns witchy.db.queryable
  (:require
   [clojure.core.match :as match]
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [witchy.db.core :as db]
   [witchy.db.dao :as dao]
   [witchy.db.observation :refer [extract-tables]]
   [witchy.db.shared :refer [format-sql]]
   [witchy.db.types :refer [Queryable ->Queryable]]))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(def ^:private now-ms #? (:cljs js/Date.now
                          :clj System/currentTimeMillis))

(defn create [id query]
  (let [tables (extract-tables query)
        f (fn simple-query
            ([] (simple-query identity))
            ([on-success]
             (p/let [results (dao/query tables query)]
               (on-success {:query-id id
                            :mode :initial
                            :results results})
               results)))]
    (->Queryable id (partial format-sql query) f tables query)))

(defn- perform-parameterized-query [id query params-spec initial-limit
                                    on-success params]
  (let [overwrote-id? (contains? params ::id)
        id (::id params id)
        raw-params (::raw-params params)
        params (dissoc params ::id ::raw-params)
        storage-params (if overwrote-id?
                         raw-params
                         params)
        select? (some? (:select query))]
    (when-not (or (nil? params-spec)
                  (s/valid? params-spec params))
      (let [explanation (s/explain-str params-spec params)]
        (throw (ex-info (str "Spec check failed: "
                             explanation
                             "\nHandling query: "
                             id params)
                        {:query-id id
                         :params params}))))

    (println "[queryable:" id "] loading @" storage-params "...")
    (-> (p/let [start (now-ms)
                full-query (cond-> query
                             :always
                             (assoc :params params)

                             (and select? initial-limit)
                             (update :limit (fnil min initial-limit) initial-limit))
                results (if select?
                          (dao/query full-query)
                          (db/execute full-query))
                actual-limit (:limit full-query)

                duration (- (now-ms) start)]

          (println "[queryable:" id "] success! (" duration "ms)" (when-not select?
                                                                    results))
          (when select?
            (on-success
             {:query-id id
              :params storage-params
              :mode :initial
              :results results
              :limit actual-limit}))

          (when (and select?
                     initial-limit
                     (>= (count results) initial-limit))
            (p/let [subsequent-results
                    (dao/query
                     (assoc query
                            :limit -1 ; IE "no limit"; load all the rest
                            :offset actual-limit
                            :params params))]
              (on-success
               {:query-id id
                :params storage-params
                :mode :append
                :results subsequent-results
                :limit actual-limit})))

          results)

        (p/catch (fn [e]
                   (log-error
                    (str "[queryable:" id "] ERROR ")
                    e
                    (let [[sql sql-params] (format-sql
                                            (assoc query
                                                   :params params
                                                   :pretty true))]
                      #js {:query (clj->js query)
                           :formatted (.split sql "\n")
                           :params-list (clj->js sql-params)
                           :params (clj->js params)})))))))

(defn create-parameterized
  "Create a parameterized Queryable.

   When invoked with an `on-success` function, by default the Queryable
   will run twice: first, with an :initial-limit (default to 20); and
   again to load the rest, using :offset.

   `{:initial-limit nil}` may be passed to always run a single query.

   Invoking the Queryable with only the params object will always run
   a single, un-modified query."
  ([id query] (create-parameterized id nil query))
  ([id {:keys [params-spec initial-limit] :or {initial-limit 20}} query]
   (let [f (fn parameterized-query
             ([params] (parameterized-query nil params))
             ([on-success params]
              (perform-parameterized-query
               id query params-spec
               (when on-success initial-limit)
               (or on-success identity)
               params)))
         format (fn format-parameterized
                  ([params] (format-parameterized params nil))
                  ([params opts]
                   ; This is mostly for interactive testing purposes...
                   (format-sql (merge (assoc query :params params)
                                      opts))))
         tables (extract-tables query)]
     (->Queryable id format f tables query))))

(defn with-params [^Queryable parameterized new-id params]
  (let [f (.-f parameterized)
        format (.-format parameterized)
        f' (fn [on-success params']
             (f on-success
                (merge (assoc params ::id new-id)
                       params'
                       {::raw-params params'})))]
    (->Queryable
     new-id
     #(format (merge params %))
     f'
     (.-tables parameterized)
     (.-query parameterized))))

(defn queryable? [v]
  (instance? Queryable v))

(defn apply-query-results [old-state {:keys [mode results limit]}]
  (match/match [old-state mode]
    ; Easy case
    [nil :initial] {:state :success
                    :results results}

    ; Reloading an existing query
    [{:results old} :initial]
    {:state :success
     :results (cond-> results
                (and limit
                     (> (count old) limit))
                (into (subvec old limit)))}

    [{:results old} :append]
    (assoc old-state
           :results
           (into (subvec old 0 limit)
                 results))))
