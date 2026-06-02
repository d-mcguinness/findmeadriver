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
    private String userType; // CARRIER, SHIPPER, or ADMIN

    // Carrier-specific
    private String licenseNumber;
    /** @deprecated kept for backwards compatibility; use {@link #licenceCategory}. */
    @Deprecated
    private String cdlType;
    private String licenceCategory;
    private Integer yearsExperience;

    // Shipper-specific
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

        if (user instanceof Carrier carrier) {
            r.setUserType("CARRIER");
            r.setLicenseNumber(carrier.getLicenseNumber());
            r.setLicenceCategory(carrier.getLicenceCategory());
            // Keep the legacy field populated so existing frontend code keeps working.
            r.setCdlType(carrier.getLicenceCategory());
            r.setYearsExperience(carrier.getYearsExperience());
        } else if (user instanceof Shipper shipper) {
            r.setUserType("SHIPPER");
            r.setCompanyName(shipper.getCompanyName());
            r.setIndustry(shipper.getIndustry() != null ? shipper.getIndustry().name() : null);
        } else {
            r.setUserType("ADMIN");
        }

        return r;
    }
}
