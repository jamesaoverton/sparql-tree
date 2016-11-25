(ns sparql-tree.core
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]))

(defn- read-csv
  "Parses a CSV given its filename. Once parsed, zipmaps through each row using
   headers that are passed through."
  [headers filename]
  (map (fn [row]
         (zipmap headers row))
       (with-open [in-file (io/reader filename)]
         (doall
           (csv/read-csv in-file)))))

(defn- get-children
  "Given a root, reduces through each row and adds any children,
   recurring if there additional children for the given child"
  [root rows entry]
  (cons entry
        (sort-by (comp :subject first)
                 (reduce (fn [out {:keys [subject parent] :as row}]
                           (if (= parent root)
                             (cons (get-children subject rows row) out)
                             out))
                         '() rows))))

(defn rows->tree
  "Converts rows of maps with :parent and :subject keys to a tree structure of
   nested lists. Assumes root notes have blank/nil as :parent values."
  [rows]
  (sequence (comp (filter (comp s/blank? :parent))
                  (map #(get-children (:subject %) rows %)))
            rows))

(defmulti write-tree
          "Given a conversion format, takes a clojure tree of sparql data,
           converts it to the specified format"
          (fn [format _] format))

;; TODO
(defmethod write-tree :text
  [_ tree]
  tree)

;; TODO
(defmethod write-tree :table
  [_ tree]
  tree)

(defn- tree->map-tree
  [out [{:keys [label subject]} & children]]
  (conj out
        (merge {:text label :iri subject}
               (when children {:children (mapcat (partial tree->map-tree []) children)}))))

(defmethod write-tree :json
  [_ tree]
  (json/write-str (reduce tree->map-tree [] tree)))

(defn csv->tree
  "Given a csv-file, mode of conversion, and headers for said csv, reads and converts
   a csv file into one of three possible modes: :text, :table, :json. Headers ought to
   contain :parent, :subject, and :label keys, but are open to be modified if needed."
  [in-file out-mode headers]
  (->> in-file
       (read-csv headers)
       rows->tree
       (write-tree out-mode)))