(ns witchy.persistence.local-storage
  "Simple, serialized storage based on window.localStorage"
  (:require
   [witchy.persistence.kv :as kv]))

(def build-dao
  (kv/create-async-kv-storage-dao-builder
   :get-item (fn get-item [k]
               (js/window.localStorage.getItem k))
   :set-item (fn set-item! [k v]
               (js/window.localStorage.setItem k v))
   :delete-item (fn delete-item! [k]
                  (js/window.localStorage.removeItem k))))
