(ns witchy.db.queryable
  (:require
   [clojure.core.match :as match]
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [witchy.db.core :as db]
   [witchy.db.observation :refer [extract-tables]]
   [witchy.db.shared :refer [format-sql]])
  #?(:clj (:import
           [clojure.lang IFn])))

(def ^:private log-error #? (:cljs js/console.error
                             :clj println))

(def ^:private now-ms #? (:cljs js/Date.now
                          :clj System/currentTimeMillis))

(deftype Queryable [id format f tables query]
  IFn
  (-invoke [_this]
    (f))
  (-invoke [_this params]
    ; NOTE: For non-parametrized queries, this arity is on-success
    (f params))
  (-invoke [_this on-success params]
    (f on-success params)))

(defn create [id query]
  (let [f (fn simple-query
            ([] (simple-query identity))
            ([on-success]
             (p/let [results (db/query query)]
               (on-success {:query-id id
                            :mode :initial
                            :results results})
               results)))
        tables (extract-tables query)]
    (->Queryable id format-sql f tables query)))

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

                             select?
                             (update :limit (fnil min initial-limit) initial-limit))
                results (if select?
                          (db/query full-query)
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
                     (>= (count results) initial-limit))
            (p/let [subsequent-results
                    (db/query
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
  ([id query] (create-parameterized id nil query))
  ([id {:keys [params-spec initial-limit] :or {initial-limit 20}} query]
   (let [f (fn parameterized-query
             ([params] (parameterized-query identity params))
             ([on-success params]
              (perform-parameterized-query
               id query params-spec initial-limit
               on-success params)))
         format (fn format-parameterized
                  ([params] (format-parameterized params nil))
                  ([params opts]
                   ; This is mostly for interactive testing purposes...
                   (format-sql (merge (assoc query :params params)
                                      opts))))
         tables (extract-tables query)]
     (->Queryable id format f tables query))))

(defn with-params [^Queryable parametrized new-id params]
  (let [f (.-f parametrized)
        format (.-format parametrized)
        f' (fn [on-success params']
             (f on-success
                (merge (assoc params ::id new-id)
                       params'
                       {::raw-params params'})))]
    (->Queryable
     new-id
     #(format (merge params %))
     f'
     (.-tables parametrized)
     (.-query parametrized))))

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
                (> (count old) limit)
                (into (subvec old limit)))}

    [{:results old} :append]
    (assoc old-state
           :results
           (into (subvec old 0 limit)
                 results))))
