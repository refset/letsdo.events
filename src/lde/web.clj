(ns lde.web
  (:require [clojure.string :as str]
            [hiccup.core :refer [html h]]
            [hiccup.page :refer [include-css]]))

(defn render [options content]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (html {:mode :html}
               [:html
                [:head
                 [:title (:title options)]
                 [:meta {:charset "utf-8"}]
                 [:meta {:content (:description options) :name "description"}]
                 [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                 (comment include-css
                                    "https://unpkg.com/tachyons@4.9.0/css/tachyons.min.css")
                 (include-css
                   "/css/main.css")]
                [:body {}
                 content]])})

(defn escape-with-br [s]
  (str/replace (h s) #"(\r\n|\r|\n)" "<br>"))
