(defproject tuffet "0.1.0-SNAPSHOT"
  :description "tuffet: couchdb->couchbase"
  :url "http://github.com/apage43/tuffet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main tuffet.app
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [apage43/cbdrawer "0.1.0"]
                 [clj-http "0.6.0"]
                 [overtone/at-at "1.0.0"]
                 [org.clojure/tools.cli "0.2.2"]])
