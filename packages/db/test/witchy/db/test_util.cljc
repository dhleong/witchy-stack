(ns witchy.db.test-util
  (:require
   [witchy.db.internal :refer [state]]
   [witchy.db.schema :as schema]))

(defmacro with-tables [tables & body]
  `(with-redefs [state (atom {:schema (schema/expand-schema {:tables ~tables})})]
     ~@body))
