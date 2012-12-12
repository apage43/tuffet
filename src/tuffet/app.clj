; vim: lispwords+=cli
(ns tuffet.app
  (:gen-class)
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [cbdrawer.client :as cb]
            [cbdrawer.transcoders :refer [json-transcoder]]
            [overtone.at-at :as at-]
            [clojure.tools.cli :refer [cli]]))

(defn uri-resolve [base path]
  (str (.resolve (java.net.URI. base) path)))

(defn strip-old-metas [doc]
  (into {}
        (filter (fn [[k v]]
                  ((complement #{\_ \$}) (first (name k)))) doc)))

(defn do-change [cbcli {:keys [id deleted doc] :as change}]
  (try @(if-not deleted
     (cb/force! cbcli id (strip-old-metas doc))
     (cb/delete! cbcli id))
    (catch Exception e (println "BROKE:" change))))

(defn syncup [cfg]
  (cb/set-transcoder! json-transcoder)
  (let [{:keys [bucket password couchbase db]} cfg
        cbcli (cb/client bucket password couchbase)
        checkpointid (str "tuffet-slurp::" db "," bucket)
        last-seen-seq (atom (:sequence (cb/get cbcli checkpointid) 0)) 
        aapool (at-/mk-pool)]
    (at-/every (* 1000 (:checkpoint-every cfg)) 
               (fn []
                 (println "Checkpointing around" @last-seen-seq)
                 (cb/force! cbcli checkpointid {:type ::checkpoint
                                                :sequence @last-seen-seq})) aapool)
    (try (with-open [changes-stream
                     (:body (http/get (uri-resolve (:couchdb cfg) (str "/" (:db cfg) "/_changes"))
                                      (merge {:as :stream
                                              :query-params (merge {:include_docs true
                                                                    :since @last-seen-seq}
                                                                   (when (:forever cfg)
                                                                     {:feed "continuous"
                                                                      :heartbeat 60000}))}
                                             (when (:couchdbauth cfg)
                                               {:basic-auth (:couchdbauth cfg)}))))]
           (let [changes 
                 (for [line (->>
                              (io/reader changes-stream)
                              line-seq
                              (filter #(.startsWith % "{\"seq")))]
                   (json/parse-string line true))]
             (when (seq changes) 
               (loop [[{seqnum :seq id :id :as change} & others] changes]
                 (do-change cbcli change)
                 (reset! last-seen-seq seqnum)
                 (when (seq others) (recur others)))) 
             ;checkpoint at the end when not running forever
             (at-/stop-and-reset-pool! aapool :strategy :kill)
             (println "Finished sync, checkpointing at " @last-seen-seq)
             (cb/force! cbcli checkpointid
                        {:type ::checkpoint :sequence @last-seen-seq})))
      (finally
        (at-/stop-and-reset-pool! aapool :strategy :kill)
        (cb/shutdown cbcli)
        (shutdown-agents)))))

(defn -main
  [& args]
  (let [[opts _extras usage] 
        (cli args
          ["-w" "--bucket-password" "Password for Couchbase bucket" :default ""]
          ["-p" "--couchdb-password" "HTTP auth password for CouchDB" :default ""]
          ["-u" "--couchdb-user" "HTTP auth username for CouchDB" :default ""]
          ["-b" "--bucket" "Couchbase bucket to sync to" :default "default"]
          ["-c" "--couchbase" "HTTP url for Couchbase"]
          ["-o" "--couchdb" "HTTP url for CouchDB"]
          ["-d" "--db" "CouchDB database to sync from"]
          ["-f" "--[no-]forever" "Sync forever (barring connection failure)" :default true]
          ["-q" "--checkpoint-period" "Checkpoint every N seconds" :default "10"]
          ["-h" "--help" "Display usage message" :flag true :default false])]
    (when (or (:help opts) (some nil? ((juxt :couchbase :couchdb :db) opts))) 
      (println usage)
      (System/exit 1))
    (syncup
      (merge
        (select-keys opts [:couchbase :couchdb :db :bucket :forever])
        {:checkpoint-every (read-string (:checkpoint-period opts))
         :password (:bucket-password opts)}
        (when (:couchdb-user opts)
          {:couchdbauth [(:couchdb-user opts) (:couchdb-password opts)]})))))

