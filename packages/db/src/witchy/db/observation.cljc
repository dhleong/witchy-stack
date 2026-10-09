(ns witchy.db.observation
  (:require
   [medley.core :refer [map-vals]]
   [witchy.db.internal :refer [state]]
   [witchy.db.interop :as i]))

; map of table-name -> (r/atom version)
(defonce ^:private ^:dynamic *table-versions* (atom {}))

(defn- select-versions [m table-names]
  (let [not-initialized (remove
                         (fn [n]
                           (contains? m n))
                         table-names)]
    (merge
     m
     (zipmap not-initialized
             (map (fn [_] (i/ratom 0)) not-initialized)))))

(defn deref-table-versions [table-names]
  (->> (-> (swap! *table-versions* select-versions table-names)
           (select-keys table-names))
       (map-vals deref)))

(defn- swap-or-ratom! [ratom f default-ratom-value]
  (if ratom
    ; We don't want to return the result of swap!
    ; We want to keep the ratom in place
    (do (swap! ratom f)
        ratom)
    (i/ratom default-ratom-value)))

(defn notify-table-updated [table-name]
  (swap!
   *table-versions*
   update
   table-name
   swap-or-ratom!
   inc 0))

(defn extract-tables [query]
  (letfn [(dealias [maybe-aliased]
            (cond
              (vector? maybe-aliased) (first maybe-aliased)
              (keyword? maybe-aliased) maybe-aliased))]
    (into
     #{}
     (concat
      (when-let [from (:from query)]
        (cond
          (keyword? from) [from]
          (vector? from) (keep dealias from)))

      (mapcat
       (fn [join-kind]
         (when-let [[table _cond] (join-kind query)]
           [(dealias table)]))
       #{:join :left-join :right-join
         :inner-join :outer-join :full-join})

      (when-let [join-by (:join-by query)]
        ; eg: :join-by [:join [[:thread-labels :tl]
        ;                      [:= :condition true]]]
        (->> join-by
             (partition 2)
             (map (comp dealias first second))))

      (keep
       (fn [clause]
         (clause query))
       [:delete-from :update :replace-into :insert-into])

      (when-let [with (:with query)]
        (mapcat
         extract-tables
         (vals with)))))))

(defn extract-trigger-tables
  "Given a mutating query, return a set of tables that might
  be mutated as a side effect due to registered triggers"
  [query]
  (when-some [triggers (get-in @state [:schema :triggers])]
    (into
     #{}
     (mapcat
      (fn [{:keys [after] :as trigger-body}]
        (when
         (or (and (= (:delete-from query) (last after))
                  (= :delete (first after)))
             (and (= (:insert-into query) (last after))
                  (= :insert (first after)))
             (and (= (:update query) (last after))
                  (= :update (first after)))
             (and (= (:replace-into query) (last after))
                  (#{:insert :update} (first after))))
          (extract-tables (:begin trigger-body))))

      (vals triggers)))))

(defn notify-updates-from-query [query]
  (when-not (:select query)
    (doseq [table (into (extract-tables query)
                        (extract-trigger-tables query))]
      (notify-table-updated table))))
