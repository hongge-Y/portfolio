package com.campus.portfolio.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一时间格式：LocalDateTime ↔ "yyyy-MM-dd HH:mm:ss"，LocalDate ↔ "yyyy-MM-dd"。
 * 不配这段时 Jackson 只认 ISO 的 "2026-11-20T08:00:00"，前端按文档传空格格式会 400。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DATE));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE));
        };
    }
}
