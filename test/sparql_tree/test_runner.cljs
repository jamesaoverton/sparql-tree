(ns sparql-tree.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [sparql-tree.core-test]))

(doo-tests 'sparql-tree.core-test)