package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.RestResponse;

public class RestFixture {
    private final RestRequest when;
    private final RestResponse then;

    public RestFixture(RestRequest when, RestResponse then) {
        this.when = when;
        this.then = then;
    }

    public RestRequest getWhen() {
        return when;
    }

    public RestResponse getThen() {
        return then;
    }

    public boolean isMatch(RestRequest request) {
        return request.equals(when);
    }

    public static class Builder {
        private RestRequest when;
        private RestResponse then;

        public Builder when(RestRequest when) {
            this.when = when;
            return this;
        }

        public Builder then(RestResponse then) {
            this.then = then;
            return this;
        }

        public RestFixture build() {
            if(when == null || then == null) {
                throw new IllegalArgumentException("You must specify both when and then");
            }
            return new RestFixture(when, then);
        }
    }



}
