(ns witchy.db.observation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [witchy.db.observation :refer [extract-tables extract-trigger-tables]]
   [witchy.db.test-util :refer [with-schema]]))

(deftest extract-tables-test
  (testing "Joins"
    (is (= #{:palismans :witches}
           (extract-tables
            {:select [:*]
             :from [[:palismans :p]]
             :left-join [[:witches :c]
                         [:= :c/pal-id :p/id]]}))))

  (testing "Inserts from triggers"
    (with-schema {:triggers
                  {:do-magic
                   {:after [:insert :on :palismans]
                    :begin {:update :witches
                            :values [{:palisman-id :new/palisman-id}]
                            :on-conflict []}}}}

      (is (= #{:witches}
             (extract-trigger-tables
              {:insert-into :palismans})))))

  (testing "Replace gets both update and insert tables"
    (with-schema {:triggers
                  {:do-magic
                   {:after [:insert :on :palismans]
                    :begin {:update :witches
                            :values [{:palisman-id :new/palisman-id}]
                            :on-conflict []}}
                   :make-friends
                   {:after [:update :on :palismans]
                    :begin {:update :friends
                            :values [{:palisman-id :new/palisman-id}]
                            :on-conflict []}}}}

      (is (= #{:witches :friends}
             (extract-trigger-tables
              {:replace-into :palismans}))))))
