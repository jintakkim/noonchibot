package com.hotak.noonchibot.connector.web.testutils;

import java.util.List;

public class RestClientTest {
    public final MockRestAssistant restAssistant = new MockRestAssistant();

    public void runWith(List<RestFixture> restFixtures, Runnable runnable) {
        try {
            restAssistant.addFixture(restFixtures);
            runnable.run();
        } finally {
            restAssistant.clearFixtures();
        }
    }

    public void runWith(RestFixture restFixture, Runnable runnable) {
        try {
            restAssistant.addFixture(restFixture);
            runnable.run();
        }finally {
            restAssistant.clearFixtures();
        }
    }
}
