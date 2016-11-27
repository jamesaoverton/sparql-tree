(ns sparql-tree.core-test
  (:require [clojure.test :refer :all]
            [sparql-tree.core :refer :all]
            [clojure.data.json :as json]))

(def example-rows
  [{:subject "d" :parent "c" :label "D"}
   {:subject "b" :parent "a" :label "B"}
   {:subject "c" :parent "f" :label "C"}
   {:subject "f" :parent "a" :label "F"}
   {:subject "c" :parent "b" :label "C"}
   {:subject "e" :parent "c" :label "E"}
   {:subject "a" :parent "" :label "A"}])

(def example-data
  '(({:subject "a", :parent "", :label "A"}
      ({:subject "b", :parent "a", :label "B"}
        ({:subject "c", :parent "b", :label "C"}
          ({:subject "d", :parent "c", :label "D"})
          ({:subject "e", :parent "c", :label "E"})))
      ({:subject "f", :parent "a", :label "F"}
        ({:subject "c", :parent "f", :label "C"}
          ({:subject "d", :parent "c", :label "D"})
          ({:subject "e", :parent "c", :label "E"}))))))

(deftest test-rows->tree
  (is (= example-data (rows->tree example-rows))))

(def text-output "- A
-- B
--- C
---- D
---- E
-- F
--- C
---- D
---- E
")

(def csv-output "1,a,0,A,,1
2,b,1,B,,1:2
3,c,2,C,,1:2:3
4,d,3,D,,1:2:3:4
5,e,3,E,,1:2:3:5
6,f,1,F,,1:6
7,c,6,C,,1:6:7
8,d,7,D,,1:6:7:8
9,e,7,E,,1:6:7:9
")

(def json-output
  (json/read-str "[
 {\"text\": \"A\",
  \"iri\": \"a\",
  \"children\": [
  {\"text\": \"B\",
   \"iri\": \"b\",
   \"children\": [
   {\"text\": \"C\",
    \"iri\": \"c\",
    \"children\": [
    {\"text\": \"D\",
     \"iri\": \"d\"},
    {\"text\": \"E\",
     \"iri\": \"e\"}
   ]}
  ]},
  {\"text\": \"F\",
   \"iri\": \"f\",
   \"children\": [
   {\"text\": \"C\",
    \"iri\": \"c\",
    \"children\": [
    {\"text\": \"D\",
     \"iri\": \"d\"},
    {\"text\": \"E\",
     \"iri\": \"e\"}
   ]}
  ]}
 ]}
]
"))

(deftest test-write-tree
  (testing "JSON output"
    (is (= json-output
           (json/read-str (csv->tree "resources/test-file.csv" :json [:subject :parent :label])))))
  (testing "Table output"
    (is (= csv-output
           (csv->tree "resources/test-file.csv" :csv [:subject :parent :label]))))
  (testing "Text output"
    (is (= text-output
           (csv->tree "resources/test-file.csv" :text [:subject :parent :label])))))