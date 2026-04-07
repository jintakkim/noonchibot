package com.hotak.noonchibot.core.order;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

@RequiredArgsConstructor
@Converter
public class JsonFeeConverter implements AttributeConverter<Map<String, BigDecimal>, String> {
    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(Map<String, BigDecimal> attribute) {
        return objectMapper.writeValueAsString(attribute);

    }

    @Override
    public Map<String, BigDecimal> convertToEntityAttribute(String s) {
        if (s == null) return Map.of();
        return objectMapper.readValue(s, new TypeReference<>() {});

    }


}
