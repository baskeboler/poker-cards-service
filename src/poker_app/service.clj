(ns poker-app.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [hiccup.page :as hiccup]
            [clojure.java.io :as io]
            [ring.middleware.session :as sess]
            [ring.middleware.session.cookie :as cookie]
            [poker-app.service :as service]
            [io.pedestal.http :as server]))
(def card-templates
  {:heart    "hearts.svg"
   :diamonds "diamonds.svg"
   :clover   "clover.svg"
   :spades   "spades.svg"})

(defn get-template [file-name]
  (org.stringtemplate.v4.ST. (slurp (clojure.java.io/resource file-name)) \{ \}))

(defn get-card [kind value]
  (assert (contains? (set (keys card-templates)) kind))
  (let [f (get card-templates kind)
        t (get-template f)]
    (-> t
        (.add "value" value)
        (.render))))

(defn all-cards-page
  [request]
  (let [cards
        (if-not (-> request
                    :session
                    :cards
                    first
                    nil?)
          (-> request :session :cards)
          (->>
           (for [kind  [:heart :clover :diamonds :spades]
                 value (concat (range 2 11) ["J" "K" "Q" "A"])]
             [kind value])
           shuffle))]

    (->
     (ring-resp/response
      (hiccup/html5
       [:body
        [:h1 "poker cards"]
        [:div.poker-cards
         (->> cards
              (map (fn [[kind value]]
                     [:div.poker-card {:style "display: inline-block; margin: 0.2em; position: relative; height: 200px; width: auto;"}
                      [:img {:src   (str "/poker-cards/" (name kind) "/" value)
                             :style "height: 100%;"}]])))]]))
     (assoc :session  {:cards cards}))))
    ;; request))
(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(defn card-request
  [request]
  (let [card-kind  (-> request :path-params :card-kind)
        card-value (-> request :path-params :card-value)]
    (-> (get-card (keyword card-kind) card-value)
        ring-resp/response
        (ring-resp/content-type "image/svg+xml")
        (assoc-in [:session :cards] (-> request :session :cards)))))
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).


(defn get-next-card
  [request]
  ;; (let [{cards :cards} (-> request :session)]
  (if-let [[kind value] (-> request :session :cards first)]
    (->
      (ring-resp/response
        (get-card kind value))
     (ring-resp/content-type "image/svg+xml")
     (assoc-in [:session :cards] (-> request :session :cards rest)))
    (all-cards-page request)))

(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/poker-cards" :get (conj common-interceptors `all-cards-page)]
              ["/poker-cards-next" :get (conj common-interceptors `get-next-card)]
              ["/poker-cards/:card-kind/:card-value" :get (conj common-interceptors `card-request)]})

;; Map-based routes
                                        ;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
                                        ;                   :get home-page
                                        ;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
                                        ;(def routes
                                        ;  `[[["/" {:get home-page}
                                        ;      ^:interceptors [(body-params/body-params) http/html-body]
                                        ;      ["/about" {:get about-page}]]]])


;; Consumed by poker-app.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes
              :session {:store (cookie/cookie-store {:key (bytes
                                                           (byte-array
                                                            (repeatedly 16 #(byte (rand-int 128)))))})}
              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8081
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)

