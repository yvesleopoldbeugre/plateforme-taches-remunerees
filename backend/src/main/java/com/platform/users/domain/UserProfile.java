package com.platform.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Maps to {@code user_profiles} (DAT §4.2) — shares its primary key with {@link User}. */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 16)
    private String gender;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(length = 128)
    private String city;

    @Column(nullable = false, length = 8)
    private String language = "fr";

    @Column(name = "reputation_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal reputationScore = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserProfile() {
        // JPA
    }

    public UserProfile(UUID userId, String fullName, String country) {
        this.userId = userId;
        this.fullName = fullName;
        this.country = country;
    }

    public void updateProfile(String fullName, String country, String city, LocalDate birthDate, String language) {
        if (fullName != null) this.fullName = fullName;
        if (country != null) this.country = country;
        if (city != null) this.city = city;
        if (birthDate != null) this.birthDate = birthDate;
        if (language != null) this.language = language;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getGender() {
        return gender;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getLanguage() {
        return language;
    }

    public BigDecimal getReputationScore() {
        return reputationScore;
    }
}
