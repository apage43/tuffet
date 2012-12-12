# tuffet

tuffet is one-way, but continuous CouchDB to Couchbase syncing

    Switches                     Default  Desc
    --------                     -------  ----
    -w, --bucket-password                 Password for Couchbase bucket
    -p, --couchdb-password                HTTP auth password for CouchDB
    -u, --couchdb-user                    HTTP auth username for CouchDB
    -b, --bucket                 default  Couchbase bucket to sync to
    -c, --couchbase                       HTTP url for Couchbase
    -o, --couchdb                         HTTP url for CouchDB
    -d, --db                              CouchDB database to sync from
    -f, --no-forever, --forever  true     Sync forever (barring connection failure)
    -q, --checkpoint-period      10       Checkpoint every N seconds
    -h, --no-help, --help        false    Display usage message

    $ java -jar tuffet.jar <options>

Download a current jar:
[tuffet-current.jar](http://s3.crate.im/tuffet-current.jar)
