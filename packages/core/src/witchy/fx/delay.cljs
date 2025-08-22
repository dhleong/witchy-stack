(ns witchy.fx.delay
  (:require
   [re-frame.core :refer [reg-fx]]
   [witchy.helpers :refer [>evt]]))

(defonce ^:private events (atom nil))

(reg-fx
 ::dispatch
 (fn dispatch-delayed [{:keys [id event ms unique?]
                        :or {unique? false}}]
   {:pre [(some? id)
          (vector? event)
          (number? ms)]}
   (swap!
    events
    update
    id
    (fn [existing]
      (if (and existing unique?)
        ; One's already scheduled with this ID and we want unique; ignore
        existing

        (do
          (when existing
            ; Replace the existing one (debounce)
            (js/clearTimeout existing))

          (js/setTimeout
           (fn trigger-delayed []
             (swap! events dissoc id)
             (>evt event))
           ms)))))))

(reg-fx
 ::cancel
 (fn cancel-delayed [{:keys [id]}]
   (swap!
    events
    update
    id
    (fn [existing]
      (when existing
        (js/clearTimeout existing))
      nil))))
