package com.itproject.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Mail configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "app.mail")
@Data
public class MailProperties {
    
    private String from;
    private String support;
    private long resetPasswordExpiration;
    private long verificationExpiration;
}
