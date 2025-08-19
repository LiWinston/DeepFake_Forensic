package com.itproject.auth.repository;

import com.itproject.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by username
     * @param username the username
     * @return Optional<User>
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email
     * @param email the email
     * @return Optional<User>
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if username exists
     * @param username the username
     * @return boolean
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     * @param email the email
     * @return boolean
     */
    boolean existsByEmail(String email);
}
