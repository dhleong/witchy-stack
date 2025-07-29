(ns witchy.db.transforms
  (:require
   #?(:cljs [applied-science.js-interop :as j])
   [cognitect.transit :as t]))

(defprotocol IColumnTransform
  (db-column-type [this])
  (db->clj [this db-value])
  (clj->db [this clj-value]))

(deftype TransitTransformer []
  IColumnTransform
  (db-column-type [_] :string)
  (db->clj [_ db-value]
    (t/read (t/reader :json) db-value))
  (clj->db [_ clj-value]
    (t/write (t/writer :json) clj-value)))

(def ^:private shared-transit-transformer (->TransitTransformer))

(deftype BooleanTransformer []
  IColumnTransform
  (db-column-type [_] :integer)
  (db->clj [_ db-value]
    (not= db-value 0))
  (clj->db [_ clj-value]
    clj-value))

(def ^:private shared-boolean-transformer (->BooleanTransformer))

(defn from-column-type [column-type]
  (case column-type
    :transit shared-transit-transformer
    :boolean shared-boolean-transformer

    (when (satisfies? IColumnTransform column-type)
      column-type)))

(defn- perform-transform [f update-fn table value]
  (reduce
   (fn [v' [column transform]]
     (update-fn v' column (partial f transform)))
   value
   (:transforms table)))

(def perform-clj->db
  (partial perform-transform clj->db update))

(def perform-db->clj
  (partial perform-transform db->clj #? (:clj update
                                         :cljs j/update!)))
