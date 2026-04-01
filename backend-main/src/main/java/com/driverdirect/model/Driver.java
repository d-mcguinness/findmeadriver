package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "drivers")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@PrimaryKeyJoinColumn(name = "user_id")
public class Driver extends User {

    @NotBlank
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @NotNull
    @Column(name = "license_expiration")
    private LocalDate licenseExpiration;

    @Enumerated(EnumType.STRING)
    @Column(name = "cdl_type")
    private CDLType cdlType;

    @PositiveOrZero
    @Column(name = "years_experience")
    private Integer yearsExperience = 0;

    @Column(name = "preferred_radius")
    private Integer preferredRadius; // in miles

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @ElementCollection
    @CollectionTable(
        name = "driver_endorsements",
        joinColumns = @JoinColumn(name = "driver_id")
    )
    @Column(name = "endorsement")
    private Set<String> endorsements = new HashSet<>();

    @Column(length = 2000)
    private String bio;
    private String licenseState;
    private Integer experienceYears;

    public enum CDLType {
        CLASS_A,
        CLASS_B,
        CLASS_C,
        NON_CDL
    }

    // Constructor with required fields
    public Driver(String email, String password, String licenseNumber, LocalDate licenseExpiration) {
        super(email, password);
        this.licenseNumber = licenseNumber;
        this.licenseExpiration = licenseExpiration;
    }
}
