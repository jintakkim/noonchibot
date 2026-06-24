package com.hotak.noonchibot.testutils;

import com.hotak.noonchibot.connector.web.RestResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public final class FixtureUtils {
    private FixtureUtils() {}

    public static RestResponse ok(String json) {
        return new RestResponse(HttpStatus.OK, new HttpHeaders(), json);
    }

    public static RestResponse badRequest(String json) {
        return new RestResponse(HttpStatus.BAD_REQUEST, new HttpHeaders(), json);
    }
}
