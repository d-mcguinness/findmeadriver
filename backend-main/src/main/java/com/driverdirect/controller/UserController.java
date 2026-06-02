package com.driverdirect.controller;

import com.driverdirect.dto.CarrierRegistrationRequest;
import com.driverdirect.dto.ShipperRegistrationRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.User;
import com.driverdirect.security.util.JwtUtil;
import com.driverdirect.service.CarrierService;
import com.driverdirect.service.ShipperService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final CarrierService carrierService;
    private final ShipperService shipperService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            User user = (User) authentication.getPrincipal();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful");
            response.put("user", user);
            response.put("token", jwtUtil.generateToken(user));

            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @PostMapping("/register/carrier")
    public ResponseEntity<?> registerCarrier(@Valid @RequestBody CarrierRegistrationRequest request) {
        Carrier registeredCarrier = carrierService.createCarrier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredCarrier);
    }

    @PostMapping("/register/shipper")
    public ResponseEntity<?> registerShipper(@Valid @RequestBody ShipperRegistrationRequest request) {
        Shipper registeredShipper = shipperService.createShipper(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredShipper);
    }
}
