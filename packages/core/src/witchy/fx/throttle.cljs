(ns witchy.fx.throttle
  (:require
   [re-frame.core :refer [reg-fx]]
   [witchy.helpers :refer [>evt]]))

(defonce ^:private events (atom nil))

(defn- trigger-throttled [id]
  (let [[{:keys [pending]} _] (swap-vals!
                               events
                               id
                               (fn clear-timeout [existing]
                                 (dissoc existing :timeout :pending)))]
    (when pending
      (>evt pending))))

(reg-fx
 ::dispatch
 (fn dispatch-throttled [{:keys [id event ms leading? trailing?]
                          :or {leading? true
                               trailing? true}}]
   {:pre [(some? id)
          (vector? event)
          (number? ms)]}
   (swap!
    events
    update
    id
    (fn [{:keys [next-window] :as existing}]
      (let [now (js/Date.now)]
        (cond
          (and leading? (>= now next-window))
          (do
            (>evt event)
            {:next-window (+ now ms)})

          (and trailing? (or (nil? next-window)
                             (< now next-window)))
          (cond-> existing
            :always
            (assoc :pending event)

            (nil? (:timeout existing))
            (assoc :timeout (js/setTimeout
                             (partial trigger-throttled id)
                             ms)))))))))

(reg-fx
 ::cancel
 (fn cancel-throttled [{:keys [id]}]
   (swap!
    events
    update
    id
    (fn [{:keys [timeout]}]
      (when timeout
        (js/clearTimeout timeout))
      nil))))
