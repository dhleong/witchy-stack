(ns witchy.db.reactive
  #_{:clj-kondo/ignore [:private-call]}
  (:require
   [re-frame.core :refer [subscribe]]
   [re-frame.db :refer [app-db]]
   [re-frame.interop :refer [make-reaction]]
   [re-frame.loggers :refer [console]]
   [re-frame.registrar :refer [register-handler]]
   [re-frame.subs :refer [deref-input-signals]]
   [reagent.core :as r]
   [witchy.db.observation :refer [deref-table-versions]]
   [witchy.db.queryable :as queryable :refer [Queryable queryable?]]))

(defn build-queryable [query-id query-or-factory signals query-vec]
  (cond
    (fn? query-or-factory)
    (build-queryable
     query-id
     (query-or-factory signals query-vec)
     signals
     query-vec)

    (and (vector? query-or-factory)
         (queryable? (first query-or-factory)))
    query-or-factory

    ; queryable:
    (queryable? query-or-factory)
    [query-or-factory]

    (map? query-or-factory)
    [(queryable/create query-id query-or-factory)]

    :else
    (throw (ex-info
            (str "Unexpected reg-query value for " query-id ": " (type query-or-factory))
            {:value query-or-factory}))))

(defn- deref-tables [tables]
  (println " - subscribe to: " tables)
  (deref-table-versions tables))

(defn- derive-inputs-fn [err-header query input-args]
  (if-not (fn? query)
    ; If the query is static, then it does not depend on anything from
    ; app-db! This is a smol optimization; the larger one is to compare
    ; the chosen query/params before triggering a refresh
    (constantly nil)

    ; NOTE: Pulled from re-frame.subs.cljc:
    (case (count input-args)
      ; no `inputs` function provided - give the default
      0 (fn
          ([_] app-db)
          ([_ _] app-db))

      ; a single `inputs` fn
      1 (let [f (first input-args)]
          (when-not (fn? f)
            (console :error err-header "2nd argument expected to be an inputs function, got:" f))
          f)

      ; one sugar pair
      2 (let [[marker vec] input-args]
          (when-not (= :<- marker)
            (console :error err-header "expected :<-, got:" marker))
          (fn inp-fn
            ([_] (subscribe vec))
            ([_ _] (subscribe vec))))

      ; multiple sugar pairs
      (let [pairs   (partition 2 input-args)
            markers (map first pairs)
            vecs    (map second pairs)]
        (when-not (and (every? #{:<-} markers) (every? vector? vecs))
          (console :error err-header "expected pairs of :<- and vectors, got:" pairs))
        (fn inp-fn
          ([_] (map subscribe vecs))
          ([_ _] (map subscribe vecs)))))))

(defn reg-query
  "The simplest form of reg-query is a static query:

   (reg-query
     :all-labels
     {:select :*
      :from :labels})

   You can build queries dynamically:

   (reg-query
     :some-labels
     (fn [_ [_ label-ids]]
       {:select :*
        :from :labels
        :where [:in :label-id label-ids))

   You can also use parametrized queryables by returning a vector:

   (reg-query
     :some-labels
     (fn [_ [_ label-ids]]
       [queries/some-labels {:label-ids label-ids}]))

   The normal reg-sub input-fn semantics are still available as well:

   (reg-query
     :some-labels
     :<- [:which-labels]
     (fn [which-labels _]
       [queries/some-labels {:label-ids which-labels}]))

   "
  [query-id & args]
  (let [err-header (str "reg-query " query-id)
        query (last args)
        args (butlast args)
        inputs-fn (derive-inputs-fn err-header query args)]
    (register-handler
     :sub
     query-id
     (fn subs-handler-fn
       ([db query-vec] (subs-handler-fn db query-vec nil))
       ([_db query-vec dynamic-vec]
        (let [results-atom (r/atom nil)
              last-query (atom nil)
              on-success (fn [results]
                           (swap!
                            results-atom
                            queryable/apply-query-results
                            results))]
          (make-reaction
           (fn []
             (let [inputs (inputs-fn query-vec dynamic-vec)
                   signals (when inputs
                             #_{:clj-kondo/ignore [:private-call]}
                             (deref-input-signals inputs query-id))

                   [^Queryable queryable params] (build-queryable
                                                  query-id
                                                  query
                                                  signals
                                                  query-vec)

                   ; Subscribe to tables
                   table-versions (deref-tables (.-tables queryable))

                   [old-query new-query] (reset-vals!
                                          last-query
                                          {:q (.-query queryable)
                                           :params params
                                           :tables table-versions})]

               ; TODO: Debounce, maybe?

               ; NOTE: We only execute the queryable if our "query" has
               ; changed in some way. See above---this encompasses the
               ; actual SQL query of course (which could have inline
               ; params), any explicit params, and the versions of the
               ; tables we depend on.
               ; If none of those have changed, then executing the
               ; queryable would be a waste, so... skip it!
               ; Common reasons for these to be =:
               ; 1. Initial subscription, of course
               ; 2. Executing on-success with initial *or* subsequent
               ;     query results
               ; 3. Some upstream DB change that didn't produce new params
               (when-not (= old-query new-query)
                 (println "[reg-query/load]" query-id)
                 (if (some? params)
                   (queryable on-success params)
                   (queryable on-success))))

             (:results @results-atom)))))))))

