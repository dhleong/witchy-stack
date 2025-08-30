(ns witchy.db.internal)

(defonce state (atom {}))

(defn table-schema [table-id]
  (or (get-in @state [:schema :tables table-id])
      (throw (ex-info "Unknown table; did you forget to setup with a schema?"
                      {:table-id table-id
                       :known-tables (keys (get-in @state [:schema]))}))))
