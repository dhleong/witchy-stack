(ns witchy.db.observation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [witchy.db.observation :refer [extract-tables]]))

(deftest extract-tables-test
  (testing "Joins"
    (is (= #{:palismans :witches}
           (extract-tables
            {:select [:*]
             :from [[:palismans :p]]
             :left-join [[:witches :c]
                         [:= :c/pal-id :p/id]]})))))
