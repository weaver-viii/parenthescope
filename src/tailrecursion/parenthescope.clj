(ns tailrecursion.parenthescope
  (:require [fipp.printer :as p :refer [defprinter]]
            [clojure.zip :as z]
            [tailrecursion.javelin-clj :refer [cell]])
  (:import (javax.swing JFrame JPanel JTextArea)
           (java.awt Font Color)
           javax.swing.text.DefaultHighlighter$DefaultHighlightPainter
           java.io.Writer
           javax.swing.text.DefaultHighlighter
           java.util.WeakHashMap))

(def ^:mutable
  text (doto (JTextArea.)
         (.setEditable false)
         (.setFont (Font. Font/MONOSPACED Font/PLAIN 16))))

(def ^:mutable
  object->idx (WeakHashMap.))

(def ^:mutable
  idx->object (atom {}))

(defn assoc-in! [^WeakHashMap m [k & ks] v]
  (doto m (.put k (assoc-in (.get m k) ks v))))

(defn text-writer [text]
  (proxy [Writer] []
    (write [x]
      (condp = (class x)
        String (.append text x)
        Integer (.append text (str (char x)))
        (throw (UnsupportedOperationException. (str "can't write " (class x))))))
    (flush [])
    (close [])))

(defn show [^JTextArea text]
  (doto (JFrame. "parenthescope")
    (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
    (.add text)
    (.pack)
    (.setLocationByPlatform true)
    (.setVisible true)))

(defmethod fipp.printer/serialize-node :call [[_ f]]
  [{:op :pass, :text (reify Object (toString [_] (f) ""))}])

(defmulti pp first)

(defn start! [obj]
  (let [point (.length (.getText text))]
    (assoc-in! object->idx [obj :start] point)
    point))

(defn end! [start obj]
  (let [point (.length (.getText text))]
    (assoc-in! object->idx [obj :end] point)
    ;; TODO fix idx->object mapping, layering
    (swap! idx->object
           (partial merge-with (fnil conj []))
           (into {} (map vector (range start point) (repeat obj))))))

(defmethod pp 'symbol [[_ s :as obj]]
  (let [start (promise)]
    [:group
     [:call #(deliver start (start! obj))]
     [:text s]
     [:call #(end! @start obj)]]))

(defmethod pp 'long [[_ s :as obj]]
  (let [start (promise)]
    [:group
     [:call #(deliver start (start! obj))]
     [:text s]
     [:call #(end! @start obj)]]))

(defmethod pp 'char [[_ s :as obj]]
  (let [start (promise)]
    [:group
     [:call #(deliver start (start! obj))]
     [:text (str "\\" s)]
     [:call #(end! @start obj)]]))

(defn pp-coll [l r obj contents]
  (let [start (promise)]
    [:group (concat [[:call #(deliver start (start! obj))]
                     [:text l]]
                    (interpose :line (map pp contents))
                    [[:text r]
                     [:call #(end! @start obj)]])]))

(defmethod pp 'list [[_ & contents :as obj]]
  (pp-coll "(" ")" obj contents))

(defmethod pp 'vector [[_ & contents :as obj]]
  (pp-coll "[" "]" obj contents))

(defprinter pprint pp {:width 80})

(defn highlight! [{:keys [start end]}]
  (let [orange (DefaultHighlighter$DefaultHighlightPainter. Color/ORANGE)
        hl (.getHighlighter text)]
    (.removeAllHighlights hl)
    (.addHighlight hl start end orange)))

(defn code-zip
  [root]
  (z/zipper (comp boolean '#{list vector} first)
            rest
            concat
            root))

(def demo-code
  '(list
    (list
     (symbol "defn")
     (symbol "pid!")
     (vector)
     (list
      (symbol "->>")
      (list
       (symbol "..")
       (symbol "java.lang.management.ManagementFactory")
       (symbol "getRuntimeMXBean")
       (symbol "getName"))
      (list
       (symbol "take-while")
       (list
        (symbol "partial")
        (symbol "not=")
        (char "@")))
      (list
       (symbol "apply")
       (symbol "str"))))
    (list
     (symbol "defn")
     (symbol "pid!")
     (vector)
     (list
      (symbol "->>")
      (list
       (symbol "..")
       (symbol "java.lang.management.ManagementFactory")
       (symbol "getRuntimeMXBean")
       (symbol "getName"))
      (list
       (symbol "take-while")
       (list
        (symbol "partial")
        (symbol "not=")
        (char "@")))
      (list
       (symbol "apply")
       (symbol "str"))))
    (list
     (symbol "defn")
     (symbol "pid!")
     (vector)
     (list
      (symbol "->>")
      (list
       (symbol "..")
       (symbol "java.lang.management.ManagementFactory")
       (symbol "getRuntimeMXBean")
       (symbol "getName"))
      (list
       (symbol "take-while")
       (list
        (symbol "partial")
        (symbol "not=")
        (char "@")))
      (list
       (symbol "apply")
       (symbol "str"))))))

(def code
  (atom (code-zip demo-code)))

(defn nav! [f]
  (highlight! (get object->idx (z/node (swap! code f)))))

(defn demo []
  (show text)
  (let [tw (text-writer text)]
    (binding [*out* tw] (pprint (z/root @code)))
    (highlight! (get object->idx (z/root @code)))
    (doseq [f [z/down z/rightmost z/left z/down z/right z/right z/right z/up z/up]]
      (Thread/sleep 400)
      (nav! f))))
