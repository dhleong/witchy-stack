(ns shadow.build.targets.vercel
  (:require
   [shadow.build :as build]
   [shadow.build.api :as build-api]
   [shadow.build.targets.esm :as esm]
   [clojure.java.io :as io]))

(def default-valid-methods
  #{:DELETE :GET :HEAD :PATCH :POST :PUT :OPTIONS})

(defmulti unpack-config type)
(defmethod unpack-config clojure.lang.Symbol
  [config]
  (if (namespace config)
    ; Easy case: fully qualified named method
    {(symbol (name config)) config}

    (throw (ex-info "Invalid function config: extracting all methods from a namespace is not yet supported" {:config config}))))

(defmethod unpack-config clojure.lang.PersistentVector
  [config]
  (->> config
       (map unpack-config)
       (reduce merge {})))

(defn- functions->modules [functions-map]
  (reduce-kv
   (fn [m f config]
     (assoc m f {:exports (unpack-config config)
                 :depends-on #{:shared}}))
   {:shared {}}
   functions-map))

(defn configure [{::build/keys [config] :as state}]
  (-> state
      (update-in [::build/config :modules] build-api/deep-merge (functions->modules (:functions config)))

      (cond->
       (not (:runtime config))
        (assoc-in [::build/config :runtime] :node)

        (not (:output-dir config))
        (build-api/with-build-options {:output-dir (io/file "api")})

        (not (get-in config [:js-options :js-provider]))
        ; NOTE: It's not immediately clear to me why we need both, but 
        ; if we omit the second we get complaints about:
        ;   JS dependency "process" is not available
        ; and if we omit the first we get complaints about "require is not
        ; defined in ES module scope"
        (-> (build-api/with-js-options {:js-provider :import})
            (update ::build/config build-api/with-js-options {:js-provider :import})))))

(defn flush-entrypoint [{{:keys [output-dir]} :build-options
                         ::build/keys [config]
                         :as state}]
  (let [entrypoint-path (or (:entrypoint config)
                            (io/file output-dir "entrypoint.js"))
        boilerplate (slurp (io/resource "witchy/vercel/entrypoint-boilerplate.js"))
        valid-methods (:valid-methods config default-valid-methods)]
    (io/make-parents entrypoint-path)
    (with-open [out (io/writer entrypoint-path)]
      (binding [*out* out]
        (println "export const registry = {")
        (doseq [fn-name (->> config :functions keys)]
          (println (str " \"/api/" (name fn-name) "\": () => import(\"./" (name fn-name) ".js\"),")))
        (println "};\n")

        (println "export const validMethods = {")
        (doseq [method-name valid-methods]
          (println (str " \"" (name method-name) "\": true,")))
        (println "};\n")

        (println boilerplate))))
  state)

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn process
  [{::build/keys [stage _config] :as state}]
  (-> state
      (cond->
       (= stage :configure)
        (configure)

        (= stage :flush)
        (flush-entrypoint))

      (esm/process)))
