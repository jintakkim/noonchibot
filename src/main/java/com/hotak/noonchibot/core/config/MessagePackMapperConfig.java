package com.hotak.noonchibot.core.config;

import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagePackMapperConfig {
    @Bean
    public MessagePackMapper messagePackMapper() {
        return new MessagePackMapper();
    }
}
