package com.itproject.auth.event;

import com.itproject.auth.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event fired when a user registers
 */
@Getter
public class UserRegistrationEvent extends ApplicationEvent {
    
    private final User user;
    
    public UserRegistrationEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
