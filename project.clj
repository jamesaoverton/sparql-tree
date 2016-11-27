(defproject sparql-tree "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/data.json "0.2.6"]
                 [testdouble/clojurescript.csv "0.2.0"]]

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]]

  :profiles {:provided {:dependencies [[org.clojure/clojurescript "1.9.293"]]}
             :test     {:dependencies [[org.mozilla/rhino "1.7.7"]]}}

  :cljsbuild
  {:builds
   {:test
    {:source-paths ["src" "test"]
     :compiler     {:output-to     "target/main.js"
                    :output-dir    "target"
                    :main          sparql-tree.test-runner
                    :optimizations :simple}}}}

  :doo {:paths {:rhino "lein run -m org.mozilla.javascript.tools.shell.Main"}}

  :aliases {"test-cljs" ["doo" "rhino" "test" "once"]
            "test-all"  ["do" ["test"] ["test-cljs"]]}
  )
