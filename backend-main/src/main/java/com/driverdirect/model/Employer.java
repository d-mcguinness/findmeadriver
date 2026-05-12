package com.driverdirect.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Entity
@Table(name = "employers")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@PrimaryKeyJoinColumn(name = "user_id")
public class Employer extends User {

    @NotBlank
    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_website")
    private String companyWebsite;

    @Enumerated(EnumType.STRING)
    @Column(name = "industry")
    private Industry industry;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "company_size")
    private Integer companySize; // number of employees

    @Column(name = "headquarters_location")
    private String headquartersLocation;

    @Column(length = 2000)
    private String companyDescription;

    // ISO-3166-1 alpha-2 country code (e.g. IE, GB, FR). Used to scope
    // address autocomplete, licence categories, and currency defaults.
    @Column(length = 2, nullable = false)
    private String country = "IE";

    // ISO-4217 currency code (e.g. EUR, GBP, USD). Defaults to the
    // currency of the employer's country.
    @Column(length = 3, nullable = false)
    private String currency = "EUR";

    public enum Industry {
        LOGISTICS,
        TRANSPORTATION,
        MANUFACTURING,
        RETAIL,
        CONSTRUCTION,
        AGRICULTURE,
        FOOD_SERVICE,
        ENERGY,
        OTHER
    }

    // Constructor with required fields
    public Employer(String email, String password, String companyName) {
        super(email, password);
        this.companyName = companyName;
    }
}
