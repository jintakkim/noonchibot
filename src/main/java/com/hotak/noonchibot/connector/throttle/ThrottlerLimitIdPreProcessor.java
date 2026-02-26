package com.hotak.noonchibot.connector.throttle;

import com.hotak.noonchibot.connector.web.RestPreProcessor;
import com.hotak.noonchibot.connector.web.RestRequest;

/**
 * limitId가 Null이 아니라면 그대로 사용, limitId가 null이라면 pathUrl을 limitId로 사용
 */
public class ThrottlerLimitIdPreProcessor implements RestPreProcessor {
    @Override
    public RestRequest process(RestRequest request) {
        if(request.throttlerLimitId() != null) return request;
        return request.toBuilder().throttlerLimitId(request.pathUrl()).build();
    }
}
