(ns witchy.db.test-util
  (:require
   [witchy.db.internal :refer [state]]
   [witchy.db.schema :as schema]))

(defmacro with-schema [schema & body]
  `(with-redefs [state (atom {:schema (schema/expand-schema ~schema)})]
     ~@body))

(defmacro with-tables [tables & body]
  `(with-schema {:tables ~tables} ~@body))
