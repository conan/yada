;; Copyright © 2014-2017, JUXT LTD.

(ns yada.authorization-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.core :as s]
   [schema.test :refer [deftest]]
   [yada.authorization :refer [allowed?]]
   [yada.boolean :as b]
   [yada.security :refer [verify]]
   [yada.test :refer [response-for]]
   [yada.yada :as yada]))

(deftest schema-test
  (is
   (nil?
    (s/check b/BooleanExpression [:and true false true]))))

(deftest composite-boolean-logic-test
  (let [a? (fn [pred] (allowed? pred nil nil))]
    (testing "identity"
      (is (true? (a? [:and])))
      (is (not (true? (a? [:or])))))
    (is (not (true? (a? [:and true false true]))))
    (is (true? (a? [:and true true])))
    (is (not (true? (a? [:or false false false]))))
    (is (true? (a? [:or false true])))
    (is (true? (a? [:not false])))
    (is (not (true? (a? [:not true]))))
    (is (true? (a? [:and [:or true false] [:and true true]])))
    (is (not (true? (a? [:and [:or false false] [:and true true]]))))))


(defmethod verify ::test
  [ctx authn]
  (::creds authn))

;; The difference between a 401 and 403 is whether the authentication
;; scheme has returned any creds at all. If no credentials have been
;; established by the authentication (perhaps because the credentials
;; aren't valid, like a wrong password), then 401 Unauthorized
;; indicates that the user should try again with some creds. If there
;; are some credentials, then 403 indicates that the credentials
;; aren't sufficient. The difference is important, because a 401 is
;; accompanied by a challenge to the user-agent to retry with
;; different credentials via the WWW-Authenticate header. The 403 does
;; not cause the WWW-Authenticate to be returned in the response.

(deftest default-authorization-test
  (testing "200 with no creds, allowed access"
    (is
     (= 200 (:status
             (response-for
              {:access-control
               {:realms
                {"default" {:authentication-schemes
                            [{:scheme ::test}]
                            :authorization {:methods {:get true}}}}}
               :methods {:get ""}})))))

  (testing "401 with no or bad creds, no access"
    (is
     (= 401 (:status
             (response-for
              {:access-control
               {:realms
                {"default" {:authentication-schemes
                            [{:scheme ::test}]
                            :authorization {:methods {}}}}}
               :methods {:get ""}})))))

  (testing "403 with good creds, but not sufficient for access"
    (is
     (= 403 (:status
             (response-for
              {:access-control
               {:realms
                {"default" {:authentication-schemes
                            [{:scheme ::test
                              ::creds {:roles #{:manager}}}]
                            :authorization {:methods {:get :superuser}}}}}
               :methods {:get ""}})))))

  (testing "200 with allowed access, some creds"
    (is
     (= 200 (:status
             (response-for
              {:access-control
               {:realms
                {"default" {:authentication-schemes
                            [{:scheme ::test
                              ::creds {:roles #{:superuser}}}]
                            :authorization {:methods {:get :superuser}}}}}
               :methods {:get ""}}))))))


(deftest function-authorization-test
  (testing "200 with authentication and authorization"
    (is
     (= 200 (:status
             (response-for
              {:access-control
               {:realms
                {"default"
                 {:authentication-schemes
                  [{:authenticate (fn [ctx] {:user "george"})}]
                  :authorization {:validate
                                  (fn [ctx creds]
                                    (when (= "george" (:user creds))
                                      ctx))}}}}
               :methods {:get ""}})))))

  (testing "401 with no creds, access not authorized"
    (is
     (= 401 (:status
             (response-for
              {:access-control
               {:realms
                {"default"
                 {:authentication-schemes
                  [{:authenticate (fn [ctx] nil)}]
                  :authorization {:validate
                                  (fn [ctx creds]
                                    (when (= "george" (:user creds))
                                      ctx))}}}}
               :methods {:get ""}})))))

  (testing "403 with creds, access not authorized"
    (is
     (= 403 (:status
             (response-for
              {:access-control
               {:realms
                {"default"
                 {:authentication-schemes
                  [{:authenticate (fn [ctx] {:user "not-george"})}]
                  :authorization {:validate
                                  (fn [ctx creds]
                                    (when (= "george" (:user creds))
                                      ctx))}}}}
               :methods {:get ""}}))))))
