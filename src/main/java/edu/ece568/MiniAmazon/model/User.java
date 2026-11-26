package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User extends AbstractPersistable<Long> {
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    private String firstName;
    private String lastName;

    @Column(nullable = false)
    private boolean accountEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
