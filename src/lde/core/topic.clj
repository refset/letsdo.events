(ns lde.core.topic
  (:refer-clojure :exclude [update])
  (:require
    [clojure.set :refer [rename-keys]]
    [lde.core.db :as db]
    [lde.core.util :as util]
    [lde.core.image :as image]
    [lde.core.event :as event]))

(def visibilities (array-map
                    :public
                    {:label "Anyone can see and participate"}
                    :invite
                    {:label "You need to be invited"}))
; :request {:label "You can request to join"}

(def types (array-map
             :activities
             {:singular "Activity"
              :plural "Activities"}
             :talks
             {:singular "Talk"
              :plural "Talks"}
             :meetups
             {:singular "Meetup"
              :plural "Meetups"}
             :events
             {:singular "Event"
              :plural "Events"}))

(defn singular [topic]
  (-> topic :topic/type types :singular))

(defn plural [topic]
  (-> topic :topic/type types :plural))

(defn unique-slug [topic-name ctx]
  (let [base (util/slug topic-name)]
    (if (db/exists-by-attribute ctx :topic/slug base)
      (loop [n 2]
        (let [slug (str base "-" n)]
          (if (db/exists-by-attribute ctx :topic/slug slug)
            (recur (inc n))
            slug)))
      base)))

(def topic-keys {:name :topic/name
                 :creator :topic/creator
                 :type :topic/type
                 :visibility :topic/visibility
                 :description :topic/description})

(def updatable-topic-keys
  (select-keys topic-keys [:name
                           :type
                           :visibility
                           :description]))

(defn create [data ctx]
  (db/tx ctx
         (let [image (image/new-entity-from-data (:image data))
               topic (-> data
                         (select-keys (keys topic-keys))
                         (rename-keys topic-keys)
                         (clojure.core/update :topic/visibility keyword)
                         (clojure.core/update :topic/type keyword)
                         (assoc :id (db/id)
                                :topic/slug (unique-slug (:name data) ctx)
                                :topic/image (:id image)))
               admin {:id (db/id)
                      :admin/topic (:id topic)
                      :admin/user (:creator data)}
               member {:id (db/id)
                       :member/topic (:id topic)
                       :member/user (:creator data)}]
           (->> [topic
                 image
                 admin
                 member]
                (db/save-multi! ctx)
                first))))

(defn update [data topic-id ctx]
  (db/tx ctx
         (when-let [existing-topic (db/get-by-id ctx topic-id)]
           (let [image (image/new-entity-from-data (:image data))
                 delete-image (:delete-image data)
                 new-topic (-> existing-topic
                               (merge (-> data
                                          (select-keys (keys updatable-topic-keys))
                                          (rename-keys updatable-topic-keys)))
                               (clojure.core/update :topic/visibility keyword)
                               (clojure.core/update :topic/type keyword)
                               (clojure.core/update :topic/image  #(cond delete-image nil
                                                                         image (:id image)
                                                                         :else %)))]
             (db/update! new-topic existing-topic ctx)
             (when-not (or delete-image (image/exists-by-hash? (:id image) ctx))
               (db/save! image ctx))
             new-topic))))

(defn get-by-slug [slug ctx]
  (db/get-by-attribute ctx :topic/slug slug))

(defn list-by-user [user-id ctx]
  (db/list-by-attribute ctx :topic/creator user-id))

(defn list-all-public [ctx]
  (db/list-by-attribute ctx :topic/visibility :public))

(defn admin? [ctx topic-id user-id]
  (db/exists-by-attributes ctx {:admin/topic topic-id
                                :admin/user user-id}))

(defn list-attached-ids [ctx topic-id]
  (->> (db/q ctx {:find ['?id]
                   :where '[[?id :admin/topic t]]
                   :args [{'t topic-id}]})
       (map first)))

(defn delete [ctx topic-id]
  (db/tx ctx
         (->> [topic-id]
              (concat (list-attached-ids ctx topic-id))
              (concat (event/list-attached-ids-by-topic ctx topic-id))
              (db/delete-by-ids! ctx))))

(defn is-member? [{:keys [user-id topic-id]} ctx]
  (db/exists-by-attributes ctx {:member/user user-id
                                :member/topic topic-id}))
