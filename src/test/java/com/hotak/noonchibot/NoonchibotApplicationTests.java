package com.hotak.noonchibot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "binance.api-key=test-api-key",
        "binance.secret-key=test-secret-key"
})
class NoonchibotApplicationTests {

    @Test
    void contextLoads() {
    }

}
