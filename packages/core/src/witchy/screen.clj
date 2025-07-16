(ns witchy.screen
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn- install-chunk-component [props]
  (let [component-name (name (:name props))]
    (assoc props :chunk-component
           `(fn []
              (js/require
               ~(str "./" component-name ".js"))))))

(defn- resolve-component-sym [shadow-config component-kw]
  (or
   (loop [builds (:builds shadow-config)]
      ; FIXME: Can we determine the correct build from env somehow?
     (let [[_build-id build] (first builds)]
       (if-some [comp-sym (get-in build [:chunks component-kw])]
         comp-sym
         (when (seq builds)
           (recur (next builds))))))

    ; TODO ?
   (do
     (println "[WARNING] Could not resolve :chunk for screen " component-kw)
     nil)))

(defn- install-resolve [props _env]
  (let [shadow-cljs-file (io/file (System/getProperty "user.dir")
                                  "shadow-cljs.edn")
        shadow-config (try
                        (with-open [r (io/reader shadow-cljs-file)]
                          (edn/read (java.io.PushbackReader. r)))
                        (catch Throwable e
                          e))
        comp-sym (resolve-component-sym
                  shadow-config
                  (:name props))
        quoted (when comp-sym
                 `(quote ~comp-sym))]
    (cond-> props
      comp-sym (assoc :resolve `(fn []
                                  (resolve ~quoted))))))

(defmacro chunk-screen [screen-component props]
  (let [props (cond-> props
                :always
                (install-chunk-component)

                (= :dev (:shadow.build/mode &env))
                (install-resolve &env))]
    `(~screen-component ~props)))
