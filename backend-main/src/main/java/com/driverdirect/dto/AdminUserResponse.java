package com.driverdirect.dto;

import com.driverdirect.model.*;
import lombok.Data;

import java.util.Set;
import java.util.stream.Collectors;

@Data
public class AdminUserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean enabled;
    private Set<String> roles;
    private String userType; // DRIVER, EMPLOYER, or ADMIN

    // Driver-specific
    private String licenseNumber;
    /** @deprecated kept for backwards compatibility; use {@link #licenceCategory}. */
    @Deprecated
    private String cdlType;
    private String licenceCategory;
    private Integer yearsExperience;

    // Employer-specific
    private String companyName;
    private String industry;

    public static AdminUserResponse from(User user) {
        AdminUserResponse r = new AdminUserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setPhone(user.getPhone());
        r.setEnabled(user.isEnabled());
        r.setRoles(user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet()));

        if (user instanceof Driver driver) {
            r.setUserType("DRIVER");
            r.setLicenseNumber(driver.getLicenseNumber());
            r.setLicenceCategory(driver.getLicenceCategory());
            // Keep the legacy field populated so existing frontend code keeps working.
            r.setCdlType(driver.getLicenceCategory());
            r.setYearsExperience(driver.getYearsExperience());
        } else if (user instanceof Employer employer) {
            r.setUserType("EMPLOYER");
            r.setCompanyName(employer.getCompanyName());
            r.setIndustry(employer.getIndustry() != null ? employer.getIndustry().name() : null);
        } else {
            r.setUserType("ADMIN");
        }

        return r;
    }
}
