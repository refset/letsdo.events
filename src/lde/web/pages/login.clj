(ns lde.web.pages.login
  (:require
    [reitit.core :refer [match->path]]
    [reitit.ring :refer [get-match]]
    [ring.util.response :as response]
    [lde.web :refer [render]]
    [lde.core.user :as user]))

(def login-click
  "var cl = document.getElementById('login-container').classList;
  cl.add('login');
  cl.remove('signup');
  document.querySelector('title').innerHTML = 'Login';
  history.replaceState({}, 'Login', '/login');")

(def signup-click
  "var cl = document.getElementById('login-container').classList;
  cl.add('signup');
  cl.remove('login');
  document.querySelector('title').innerHTML = 'Signup';
  history.replaceState({}, 'Signup', '/signup');")

(defn handler [req]
  (let [path (-> req get-match match->path)]
      (render
        {:title "Login"
         :description "Hi"}
        [:div#login-container {:class (subs path 1)}
         [:h1.f1
          [:a.login-heading
           {:href "/login"
            :onClick login-click}
           "Login"]
          " | "
          [:a.signup-heading
           {:href "/signup"
            :onClick signup-click}
           "Signup"]]
         [:form {:action path :method "post"}
          [:label.name-field "Name: "
           [:input {:type "text"
                    :name "name"
                    :placeholder "Name"}]]
          [:br]
          [:label "Email: "
           [:input {:type "email"
                    :name "email"
                    :required true
                    :placeholder "Email"}]]
          [:br]
          [:label [:i "Optionally"] " password: "
           [:input {:type "password"
                    :name "password"
                    :placeholder "Password"}]]
          [:br]
          [:small "No need to set a password, we will send you a mail"]
          [:br]
          [:label.link-field [:i "Optionally"] " link to your website/social media/...: "
           [:input {:type "text"
                    :name "link"
                    :placeholder "Link"}]]
          [:br]
          [:button.login-button {:type "submit"} "Login"]
          [:button.signup-button {:type "submit"} "Signup"]]])))

(defn post-login [{:keys [ctx params]}]
  (let [password (:password params)]
    (if (empty? password)
      (do (user/send-login-mail ctx (:email params))
          (response/redirect "/login/mail-confirm" :see-other))
      (if-let [user (user/login ctx (:email params) password)]
       (-> (response/redirect "/" :see-other)
           (assoc :session (select-keys user [:id])))
       (response/bad-request "Invalid login")))))

(defn post-signup [{:keys [ctx params]}]
  (let [user (user/create params ctx)]
    (condp = user
      :duplicate-email (response/bad-request "Email already taken")
      (-> (response/redirect "/" :see-other)
          (assoc :session (select-keys user [:id]))))))

(defn logout [req]
  (-> (response/redirect "/" :see-other)
      (assoc :session nil)))

(defn mail [{:keys [ctx parameters]}]
  (if-let [token (-> parameters :query :token)]
    (if-let [user (user/login-with-token ctx token)]
       (-> (response/redirect "/" :see-other)
             (assoc :session (select-keys user [:id])))
       (response/bad-request "Invalid token"))
    (response/bad-request "No token")))

(defn mail-confirm [req]
  (render
    {:title "Login link sent"
     :description "Hi"}
    [:div
     [:p "We sent you a login link. Please check your mails."]]))
