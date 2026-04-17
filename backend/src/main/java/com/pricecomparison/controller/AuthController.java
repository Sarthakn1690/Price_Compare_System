package com.pricecomparison.controller;

import com.pricecomparison.dto.AuthDtos;
import com.pricecomparison.model.AppUser;
import com.pricecomparison.repository.AppUserRepository;
import com.pricecomparison.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));
        }
        AppUser user = appUserRepository.save(AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .plan(AppUser.Plan.FREE)
                .build());

        String token = jwtService.generateToken(user.getEmail(), Map.of("plan", user.getPlan().name()));
        return ResponseEntity.ok(AuthDtos.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .plan(user.getPlan().name())
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        String token = jwtService.generateToken(user.getEmail(), Map.of("plan", user.getPlan().name()));
        return ResponseEntity.ok(AuthDtos.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .plan(user.getPlan().name())
                .build());
    }
}

