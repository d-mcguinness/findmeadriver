package com.driverdirect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String email;
    private String roles;
    // Additional fields can be added such as token, expiry, etc.
}
