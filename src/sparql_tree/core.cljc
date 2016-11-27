(ns sparql-tree.core
  (:require
    [clojure.string :as s]
    #?(:clj  [clojure.java.io :as io])
    #?(:clj  [clojure.data.csv :as csv]
       :cljs [testdouble.cljs.csv :as csv])
    #?(:clj  [clojure.data.json :as json]))
  #?(:clj
     (:import [java.io StringWriter])))

;; CSV/DELIMITTED MANAGEMENT
(defn- read-csv
  "Parses a CSV given its filename. Once parsed, zipmaps through each row using
   headers that are passed through."
  [raw-data? headers data]
  (map (fn [row]
         (zipmap headers row))
       #?(:clj  (if raw-data?
                  (csv/read-csv data)
                  (with-open [in-file (io/reader data)]
                    (doall
                      (csv/read-csv in-file))))
          :cljs (map #(s/split % #",")
                     (s/split data #"\n")))))

(defn- delimitted-string-writer
  "Writes a table to a delimmted string. String is delimitted by a custom separator."
  [table separator]
  #?(:clj  (let [writer (StringWriter.)]
             (csv/write-csv writer table :separator separator)
             (str writer))
     :cljs (str (csv/write-csv table :separator separator) "\n")))

;; JSON MANAGEMENT (CLJS)
#?(:cljs
   (defn clj->json
     [data]
     (.stringify js/JSON (clj->js data))))

#?(:cljs
   (defn json->clj
     [data]
     (js->clj (.parse js/JSON data))))

;; TREE BUILDER
(defn- children
  "Returns children of a given subject."
  [rows subject]
  (filter #(= subject (:parent %)) rows))

(defn rows->tree
  "Recursively builds a tree given a table of parent-children relationships."
  ([rows [car & cdr]]
   (let [children-car (children rows (:subject car))]
     (cons
       (cons car
             (when (not-empty children-car)
               (rows->tree rows children-car)))
       (when (not-empty cdr)
         (rows->tree rows cdr)))))
  ([rows]
   (rows->tree rows (filter (comp s/blank? :parent) rows))))

;; TREE WRITER
(defmulti write-tree
          "Given a conversion format, takes a clojure tree of sparql data,
           converts it to the specified format"
          (fn [format _] format))

(declare tree->text)

(defn- text-node
  [prefix [node & others]]
  (str prefix " " (:label node)
       (when (not-empty others)
         (str "\n" (tree->text (str prefix "-") others)))))

(defn- tree->text
  [prefix tree]
  (s/join "\n"
          (reduce (fn [out root]
                    (conj out (text-node prefix root))) [] tree)))

(defmethod write-tree :text
  [_ tree]
  (tree->text "-" tree))

(defn- table-entry
  "Creates a single table entry. Takes the current index and the item for the entry."
  [idx {:keys [subject label parents]}]
  [idx subject (or (first parents) 0) label "" (s/join ":" (reverse (conj parents idx)))])

(defn- update-parents
  [{p :parents} idx [child & others]]
  (cons (assoc child :parents (conj p idx)) others))

(defn- tree->table
  "Returns a tree table, used for IEDB Finders."
  [tree]
  (loop
    [table []
     idx 1
     [parent & children] (first tree)
     rest-tree (rest tree)]
    (cond
      (nil? parent) table
      (empty? children) (recur
                          (conj table (table-entry idx parent))
                          (inc idx)
                          (first rest-tree)
                          (rest rest-tree))
      :else (let [applied-children (map (partial update-parents parent idx) children)]
              (recur
                (conj table (table-entry idx parent))
                (inc idx)
                (first applied-children)
                (concat (rest applied-children) rest-tree))))))

(defmethod write-tree :csv [method tree] (delimitted-string-writer (tree->table tree) \,))
(defmethod write-tree :tsv [method tree] (delimitted-string-writer (tree->table tree) \tab))

(defn- tree->map-tree
  "Creates a nested map of parents and children"
  [out [{:keys [label subject]} & children]]
  (conj out
        (merge {:text label :iri subject}
               (when children {:children (mapcat (partial tree->map-tree []) children)}))))

(defmethod write-tree :json
  [_ tree]
  (->> tree
       (reduce tree->map-tree [])
       #?(:clj json/write-str
          :cljs clj->json)))

(defn csv->tree
  "Given a csv or a csv file, mode of conversion, and headers for said csv, reads and converts
   a csv file into one of four possible modes: :text, :csv, :tsv, :json. Headers ought to
   contain :parent, :subject, and :label keys, but are open to be modified if needed. In
   clojurescript it will always assume the in-file is a raw csv string, but in clojure
   you can specify whether you are working with a file or raw-data."
  ([in-file out-mode headers raw-data?]
   (->> in-file
        (read-csv raw-data? headers)
        rows->tree
        (write-tree out-mode)))
  ([in-file out-mode headers]
   (csv->tree in-file out-mode headers false)))