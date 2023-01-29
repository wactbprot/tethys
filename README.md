<img src="logo.png" alt="tethys" id="logo" width="300px">



# system

* the system maintained by
  [integrant](https://github.com/weavejester/integrant)

* ui 
  * https://fomantic-ui.com/
  * var ws = new WebSocket("ws://127.0.0.1:8010/ws")
  * [org.httpkit.server :refer [with-channel on-receive on-close  send!]]

# development

## launch clj and compile

Ensure classes folder exist:
```shell
mkdir -p target/classes
```

```shell
clj -A:dev
```

```clojure
(set! *compile-path* "target/classes")
;; => "target/classes"
(compile 'tethys.cli) 
(start)
;; => {:ctrl :run}
(c-run :mpd-ref 0)
;; => ":mpd-ref.:conts.0.0.0"
;;    ":mpd-ref.:conts.0.0.1"
;;    ":mpd-ref.:conts.0.1.0"
;;    ":mpd-ref.:conts.0.1.1"
;;    ":mpd-ref.:conts.0.1.2"
;;    ":mpd-ref.:conts.0.1.3"
;;    ":mpd-ref.:conts.0.2.0"
```


## spawn nrepl and connect from somewhere

```shell
clj -A:dev -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"
```
The connection port is stored in the file `.nrepl-port` in pwd. 
To connect to system with  **CIDER** (Emacs) use  `M-x` cider-connect `RET`

> CIDER will prompt you for the host and port information,...

[see cider manual](https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server)

## generate documentation

```shell
clojure -M:docs
```

## find outdated deps

```shell
clojure -M:outdated
```

# generate namespace graph

```shell
clj -X:hiera
```

<img src="namespaces.png" alt="tethys ns" id="ns" width="800px">


# ELS

kibana > Management > DevTools

```
PUT /tethys_log
```

## show in kibana

Management > Kibana > Index Patterns
`http://<host>:5601//app/management/kibana/indexPatterns`
--> create index pattern


## logging order accuracy

https://github.com/BrunoBonacci/mulog/issues/76

**Q:** Is it possible to configure higher timestamp accuracy to maintain logging order?

**A:** If you are interested in the relative order of events within the same JVM, then the easiest option is to sort by :mulog/trace-id

**Here is how:** Add field to index with script:

```
emit(doc['mulog/trace-id.keyword'].value)
```
