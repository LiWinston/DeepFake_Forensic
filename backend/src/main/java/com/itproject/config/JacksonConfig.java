package com.itproject.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson configuration for proper Hibernate lazy loading handling
 */
@Configuration
public class JacksonConfig {
    
    /**
     * Configure ObjectMapper to handle Hibernate proxies and lazy loading
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(
                        new JavaTimeModule(),
                        hibernateModule()
                )
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_EMPTY_BEANS
                )
                .build();
    }
    
    /**
     * Hibernate6Module to handle Hibernate proxy objects
     */
    @Bean
    public Hibernate6Module hibernateModule() {
        Hibernate6Module module = new Hibernate6Module();
        // Force lazy loading instead of serializing proxy
        module.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, false);
        // Serialize identifier for lazy loaded objects
        module.configure(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS, true);
        // Replace lazy loading proxies with null
        module.configure(Hibernate6Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL, true);
        return module;
    }
}
