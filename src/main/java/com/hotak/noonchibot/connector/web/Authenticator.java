package com.hotak.noonchibot.connector.web;

public interface Authenticator {
    RestRequest restAuthenticate(RestRequest restRequest);
    WsRequest wsAuthenticate(WsRequest wsRequest);
}
