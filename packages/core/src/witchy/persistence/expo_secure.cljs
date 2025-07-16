(ns witchy.persistence.expo-secure
  "Secure storage based on expo-secure-store"
  (:require
   ["expo-secure-store" :as SecureStore]
   [witchy.persistence.kv :as kv]))

(def build-dao
  (kv/create-async-kv-storage-dao-builder
    :get-item SecureStore/getItemAsync
    :set-item SecureStore/setItemAsync
    :delete-item SecureStore/deleteItemAsync))
