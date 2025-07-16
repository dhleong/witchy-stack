(ns witchy.persistence.kv
  "Helper for building async kv stores"
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.transit :as t]
    [promesa.core :as p]))

(defn- normalize-storage-key [k]
  (cond
    (keyword? k) (subs (str k) 1) ; lazy way to include namespace
    (string? k) k
    :else (throw (ex-info "Invalid secure storage key" {:key k}))))

(defn create-async-kv-storage-dao-builder [& {:keys [get-item
                                                     set-item
                                                     delete-item]}]
  (fn build-dao [{provided-storage-key :storage-key, :keys [valid? spec]}]
    (let [valid? (cond
                   valid? valid?
                   spec (partial s/valid? spec)
                   :else (constantly true))]
      (cond->
        {:load (fn load [storage-key]
                 (p/let [stringified (get-item (normalize-storage-key storage-key))]
                   (when stringified
                     (when-let [read-value (t/read (t/reader :json) stringified)]
                       (if (valid? read-value)
                         read-value

                         (js/console.warn
                           "Read invalid prefs: " (str read-value)
                           "\n Explanation:" (s/explain-str spec read-value)))))))

         :save (fn save [storage-key new-value]
                 ; NOTE: We *shouldn't* need to re-validate prefs, as that should've been done by
                 ; the interceptors already.
                 (if (nil? new-value)
                   (delete-item (normalize-storage-key storage-key))

                   (let [serialized (t/write (t/writer :json) new-value)]
                     (set-item (normalize-storage-key storage-key) serialized))))}

        ; If we were given a storage key, always use it!
        provided-storage-key (update-vals (fn [f]
                                            (partial f provided-storage-key)))))))
