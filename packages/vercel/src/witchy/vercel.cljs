(ns witchy.vercel
  (:require
   [promesa.core :as p]))

(deftype Headers [^js headers]
  ILookup
  (-lookup
    [_ k]
    (.get headers (cond
                    (keyword? k) (name k)
                    :else k)))
  (-lookup
    [o k not-found]
    (or (-lookup o k) not-found))

  ISeqable
  (-seq [_]
    ; NOTE: NOT lazy
    (->> (js/Array.from (.entries headers))
         (map (fn [[k v]]
                (MapEntry. k v nil))))))

(declare ->Req)
(deftype Req [^js req body json extras]
  ILookup
  (-lookup
    [_ k]
    (case k
      :headers (->Headers (.-headers req))
      :body body
      :json @json
      (get extras k)))
  (-lookup
    [o k not-found]
    (case k
      (:headers :body :json) (-lookup o k not-found)
      (get extras k not-found)))

  IAssociative
  (-contains-key? [_ k]
    (or (#{:body :json :headers} k)
        (contains? extras k)))

  (-assoc [_ k v]
    ; TODO: Should we support overwriting the builtin keys?
    (->Req req body json (assoc extras k v)))

  IMap
  (-dissoc [_ k]
    ; TODO: Should we support overwriting the builtin keys?
    (->Req req body json (dissoc extras k))))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn json
  "Convenience wrapper around a json request handler function for
  compatibility with Vercel's functions. Your handler will receive a Req
  object, which looks like a clojure map containing {:headers, :body, :json}
 
  Unpacking :json is lazy, in case you need access to the raw request
  :body. You can also `assoc` and `dissoc` additional keys on Req
  for middleware use.
  
  By default, JSON keys are keywordized, but this can be disabled by
  passing `{:keywordize-keys false}` to the optional `opts` param.

  The handler is assumed to return a Promise, which may reject with an `ex-info` exception. If `:code` is a keyword in the `ex-data`, it will be
  converted to an appropriate HTTP status code. The response object will
  be converted via clj->js before returning as a `Response.json`.
  "
  ([handler ^js req] (json handler {:keywordize-keys true} req))
  ([handler {:keys [keywordize-keys]} ^js req]
   ; NOTE: We have to return a js/Promise instance or else vercel will not
   ; treat it like an async handler
   (js/Promise.resolve
    (-> (p/let [body (.arrayBuffer req)
                json (delay (-> (.decode (js/TextDecoder. "utf-8") body)
                                (js/JSON.parse)
                                (js->clj :keywordize-keys keywordize-keys)))
                response (handler (->Req req body json {}))]
          (if (instance? js/Response response)
            response
            (js/Response.json (clj->js response))))
        (p/catch (fn [e]
                   (let [code (:code (ex-data e))]
                     (if (keyword? code)
                       (js/Response.
                        (ex-message e)
                        #js {:status (case code
                                       :unauthorized 401
                                       :not-found 404)})

                       (do
                         (println "[ERROR]"  e)
                         (throw e))))))))))

