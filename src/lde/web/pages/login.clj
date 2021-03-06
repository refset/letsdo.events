(ns lde.web.pages.login
  (:require
    [reitit.core :refer [match->path]]
    [reitit.ring :refer [get-match]]
    [ring.util.response :as response]
    [lde.web.util :refer [render goto-url]]
    [lde.core.user :as user]))

(defn handler [{:as req
                {:as ctx
                 {pw-auth-enabled :enable-password-authentication} :config} :ctx
                {user-id :id} :session
                {{:keys [goto]} :query} :parameters}]
  (let [path (-> req get-match match->path)]
    (if user-id
      (response/redirect (if goto goto "/"))
      (render
        ctx
        {:title "Login"}
        [:div.login-container {:class (subs path 1)}
         [:h1.f1
          [:a.login-heading
           {:href (goto-url "/login" goto)}
           "Login"]
          " | "
          [:a.signup-heading
           {:href (goto-url "/signup" goto)}
           "Signup"]]
         [:form {:action (goto-url path goto) :method "post"}
          [:div.form-field.name-field
           [:label [:div "Name"]
            [:input.input-field {:type "text"
                                 :name "name"}]]]
          [:div.form-field
           [:label
            [:div "Email" [:sup " *"]]
            [:input.input-field {:type "email"
                                 :name "email"
                                 :required true}]]]
          (when pw-auth-enabled
            [:div.form-field
             [:label
              [:div "Password"]
              [:div
               [:input.input-field {:type "password"
                                    :name "password"}]]
              [:small "No need for a password, you get an email instead"]]])
          [:div.form-field.link-field
           [:label
            [:div "Link to your website / social media / ..."]
            [:input.input-field {:type "text"
                                 :name "link"}]]]
          [:div.form-field
           [:button.btn.login-button {:type "submit"} "Login"]
           [:button.btn.signup-button {:type "submit"} "Signup"]]]]))))

(defn post-login [{{:as ctx
                    {pw-auth-enabled :enable-password-authentication} :config} :ctx
                   {{:keys [email password]} :form
                    {:keys [goto]} :query} :parameters}]
  (if (or (not pw-auth-enabled) (empty? password))
    (do (user/send-login-email ctx email goto)
        (response/redirect "/login/email-confirm" :see-other))
    (if-let [user (user/login ctx email password)]
      (-> (response/redirect (if goto goto "/") :see-other)
          (assoc :session (select-keys user [:id])))
      (response/bad-request "Invalid login"))))

(defn post-signup [{:keys [ctx]
                    {form-params :form
                     {:keys [goto]} :query} :parameters}]
  (let [user (user/create form-params ctx)]
    (condp = user
      :duplicate-email (response/bad-request "Email already taken")
      (-> (response/redirect (if goto goto "/") :see-other)
          (assoc :session (select-keys user [:id]))))))

(defn logout [{{{:keys [goto]} :query} :parameters}]
  (-> (response/redirect (if goto goto "/"))
      (assoc :session nil)))

(defn email [{:keys [ctx]
             {{:keys [token goto]} :query} :parameters}]
  (if token
    (if-let [user (user/login-with-token ctx token)]
      (-> (response/redirect (if goto goto "/") :see-other)
          (assoc :session (select-keys user [:id])))
      (response/bad-request "Invalid token"))
    (response/bad-request "No token")))

(defn email-confirm [{:keys [ctx]}]
  (render
    ctx
    {:title "Login link sent"}
    [:div
     [:h1 "Let's do events!"]
     [:h2 "We sent you a login link"]
     [:div
      [:p "Please check your email."]]]))
