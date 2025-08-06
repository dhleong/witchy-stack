(ns witchy.db.observer
  (:require
   [re-frame.core :refer [dispatch reg-event-fx subscribe trim-v]]
   [re-frame.interop :as rei :refer [dispose!]]
   [reagent.core :as r]
   [reagent.ratom :refer [add-on-dispose!]]))

(defonce ^:private tracks (atom nil))

(defonce ^:private ^:dynamic *disposing?* false)

(defn- intentional-dispose! [t]
  (binding [*disposing?* true]
    (dispose! t)))

(defn reg-query-observer
  ([id _<- query-vec handler]
   (reg-query-observer id _<- query-vec [] handler))
  ([id _<- query-vec interceptors handler]
   (reg-event-fx
    id
    (into [trim-v] interceptors)
    (fn [cofx [result]]
      (handler cofx result)))

   ; NOTE: Doing this after-render lets us avoid some thrash with typical
   ; init flows that perform clear-subscription-cache on startup. It may
   ; not be strictly necessary, but does reduce wasted queries...
   (rei/after-render
    (fn recreate []
      (swap!
       tracks update id
       (fn [old]
         (when old
           (intentional-dispose! old))

         (r/track!
          (fn subscribe-to-query []
            (let [sub (subscribe query-vec)]
              (add-on-dispose!
               sub
               (fn handle-dispose []
                     ; NOTE: Hacks? Basically this is
                     ; typically from clear-subscription-cache
                 (when-not *disposing?*
                   (rei/after-render recreate))))
              (when-some [result @sub]
                (dispatch [id result])))))))))))
