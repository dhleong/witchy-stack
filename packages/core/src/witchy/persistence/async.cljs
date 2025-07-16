(ns witchy.persistence.async
  "Simple, serialized storage based on react-native-async-storage"
  (:require
    ["@react-native-async-storage/async-storage" :default AsyncStorage]
    [witchy.persistence.kv :as kv]))

(def build-dao
  (kv/create-async-kv-storage-dao-builder
    :get-item (.-getItem AsyncStorage)
    :set-item (.-setItem AsyncStorage)
    :delete-item (.-removeItem AsyncStorage)))
