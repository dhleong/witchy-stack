(ns witchy.db.shared-test
  (:require [clojure.test :refer [deftest is testing]]
            [witchy.db.shared :refer [format-sql]]
            [clojure.string :as str]))

(deftest create-trigger-test
  (testing "Handle when clauses"
    (let [[sql params] (format-sql
                        {:create-trigger :my-trigger
                         :after [:delete :on :some-table]
                         :when [:> :old/column 0]
                         :begin {:update :other-table}})]
      (is (str/includes? sql "WHEN \"old\".\"column\" > ?"))
      (is (= [0] params)))))

