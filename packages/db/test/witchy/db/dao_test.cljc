(ns witchy.db.dao-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [witchy.db.dao :refer [->clj]]
   [witchy.db.test-util :refer [with-tables]]))

(deftest ->clj-test
  (testing "Don't double transform"
    (with-tables {:first {:columns [[:bool-column :boolean]]}
                  :second {:columns [[:bool-column :boolean]]}}
      (is (= {:bool-column false}
             (->clj [:first :second] {:bool-column 0}))))))
