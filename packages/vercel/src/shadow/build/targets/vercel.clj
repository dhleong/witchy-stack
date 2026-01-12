(ns shadow.build.targets.vercel
  (:require
   [shadow.build :as build]
   [shadow.build.api :as build-api]
   [shadow.build.targets.esm :as esm]
   [clojure.java.io :as io]))

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
      (update-in [::build/config :modules] merge (functions->modules (:functions config)))

      (cond->
       (not (:runtime config))
        (assoc-in [::build/config :runtime] :node)

        (not (:output-dir config))
        (assoc-in [::build/config :runtime] "api")

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
                            (io/file output-dir "entrypoint.js"))]
    (io/make-parents entrypoint-path)
    (with-open [out (io/writer entrypoint-path)]
      (binding [*out* out]
        (println "export const registry = {")
        (doseq [fn-name (->> config :functions keys)]
          (println (str " \"/api/" (name fn-name) "\": () => import(\"./" (name fn-name) ".js\"),")))
        (println "};\n")

        (println "export async function handleRequest(request) {")
        (println "  const url = new URL(request.url);")
        (println "  const fetchHandlers = registry[url.pathname];")
        (println "  if (fetchHandlers == null) return new Response(null, { status: 404 });")
        (println "  const handlers = await fetchHandlers();")
        (println "  const handler = handlers[request.method];")
        (println "  if (handler == null) return new Response(null, { status: 405 });")
        (println "  return handler(request);")
        (println "}"))))
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
