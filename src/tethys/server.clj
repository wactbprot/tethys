(ns tethys.server
    ^{:author "wactbprot"
      :doc "Provides a frontend."}
  (:require [com.brunobonacci.mulog :as µ]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :as res]
            [compojure.route :as route]))

(defn gen-routes [images]
  (defroutes app-routes
    (GET "/" [] (res/response {:ok true}))
    (route/resources "/")
    (route/not-found (res/response {:error "not found"}))))
