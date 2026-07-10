package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.core.BootStrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootStrapConfig {
    @Bean(destroyMethod = "shutdown")
    public BootStrap bootStrap() {
        return new BootStrap();
    }

    @Bean
    public ApplicationRunner bootStrapRunner(BootStrap bootStrap) {
        return arguments -> bootStrap.start();
    }
}
