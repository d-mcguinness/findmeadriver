package com.driverdirect.controller;

import com.driverdirect.dto.DriverRegistrationRequest;
import com.driverdirect.dto.EmployerRegistrationRequest;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.User;
import com.driverdirect.security.util.JwtUtil;
import com.driverdirect.service.DriverService;
import com.driverdirect.service.EmployerService;
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
    private final DriverService driverService;
    private final EmployerService employerService;
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

    @PostMapping("/register/driver")
    public ResponseEntity<?> registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        Driver registeredDriver = driverService.createDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredDriver);
    }

    @PostMapping("/register/employer")
    public ResponseEntity<?> registerEmployer(@Valid @RequestBody EmployerRegistrationRequest request) {
        Employer registeredEmployer = employerService.createEmployer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredEmployer);
    }
}
