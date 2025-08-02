(ns witchy.db.observation
  (:require
   [medley.core :refer [map-vals]]
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

(defn notify-updates-from-query [query]
  (when-not (:select query)
    (doseq [table (extract-tables query)]
      (notify-table-updated table))))
