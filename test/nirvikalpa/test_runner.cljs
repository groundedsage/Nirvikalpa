(ns nirvikalpa.test-runner
  (:require [cljs.test :refer [run-tests]]
            [nirvikalpa.shader.ast-test]
            [nirvikalpa.projection-test]))

(defn -main []
  (run-tests 'nirvikalpa.shader.ast-test
             'nirvikalpa.projection-test))
