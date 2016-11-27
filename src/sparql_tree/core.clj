(ns sparql-tree.core
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]))

(def not-blank? (comp not s/blank?))

(defn- read-csv
  "Parses a CSV given its filename. Once parsed, zipmaps through each row using
   headers that are passed through."
  [headers filename]
  (map (fn [row]
         (zipmap headers row))
       (with-open [in-file (io/reader filename)]
         (doall
           (csv/read-csv in-file)))))

(defn conj-and-sort
  "Sequentially conj's an item to a collection then sorts it"
  [coll item]
  (-> coll (conj item) sort))

(defn rows->node-map
  "Takes a list of map entries with :subject, :parent, and :label keys and returns
   a map with the key of each element being the subject, and value being a map
   indicating the entries parents, children, and label"
  [rows]
  (reduce (fn [out {:keys [subject parent label]}]
            (cond-> out
                    true (assoc-in [subject :label] label)
                    (not-blank? parent) (update-in [subject :parents] conj-and-sort parent)
                    (not-blank? parent) (update-in [parent :children] conj-and-sort subject)))
          {} rows))

(defn children
  [rows subject]
  (filter #(= subject (:parent %)) rows))

(defn rows->tree
  ([rows [car & cdr :as elements]]
   (let [children-car (children rows (:subject car))]
     (cons
       (cons car
             (when (not-empty children-car)
               (rows->tree rows children-car)))
       (when (not-empty cdr)
         (rows->tree rows (seq cdr))))))
  ([rows]
    (rows->tree rows (filter (comp s/blank? :parent) rows))))

(defmulti write-tree
          "Given a conversion format, takes a clojure tree of sparql data,
           converts it to the specified format"
          (fn [format _] format))

;; TODO
(defmethod write-tree :text
  [_ node-map]
  )

(defn node-map->ordered-subjects
  [elements node-map]
  (reduce (fn [out [e {:keys [children] :as entry}]]
            (if children
              (conj out (assoc entry :iri e) (when children (node-map->ordered-subjects (select-keys node-map children) node-map)))
              (conj out (assoc entry :iri e))))
          [] elements))

(defn- node-map->table-old
  [roots node-map]
  (loop
    [i 1
     elements (flatten (node-map->ordered-subjects roots node-map))
     rows []]
    (if (empty? elements)
      rows
      (let [{:keys [iri label parents]} (first elements)]
        (recur (inc i)
               (rest elements)
               (conj rows [i (first elements)]))))))

(defn- node-map->table
  [tree]
  (loop
    [table []
     idx 1
     current-parent nil
     ancestry []
     [parent & children] (first tree)
     rest-tree (rest tree)]
    (cond
      (nil? parent) table
      (empty? children) (recur
                          (conj table [idx
                                       (:subject parent)
                                       (or current-parent 0)
                                       (:label parent)
                                       ""
                                       (s/join ":" (conj ancestry idx))])
                          (inc idx)
                          (last ancestry)
                          (pop ancestry)
                          (first rest-tree)
                          (rest rest-tree))

      :else (recur
              (conj table [idx
                           (:subject parent)
                           (or current-parent 0)
                           (:label parent)
                           ""
                           (s/join ":" (conj ancestry idx))])
              (inc idx)
              idx
              (conj ancestry idx)
              (first children)
              (concat (rest children) rest-tree)))))

(defmethod write-tree :table
  [_ node-map]
  node-map)

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