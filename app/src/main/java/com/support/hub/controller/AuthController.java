package com.support.hub.controller;

import com.support.hub.model.dto.LoginRequestDTO;
import com.support.hub.model.dto.LoginResponseDTO;
import com.support.hub.service.JwtTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenService jwtTokenService;

    private static final Set<String> VALID_ROLES = Set.of("CUSTOMER", "AGENT", "ADMIN");

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        String role = loginRequest.getRole().toUpperCase();
        
        if (!VALID_ROLES.contains(role)) {
            log.warn("Invalid role attempted: {}", role);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String token = jwtTokenService.generateToken(loginRequest.getSub(), role);
        Long expiresIn = jwtTokenService.getExpirationTime();

        LoginResponseDTO response = new LoginResponseDTO(token, "Bearer", expiresIn);
        
        log.info("Token generated for sub: {}, role: {}", loginRequest.getSub(), role);
        
        return ResponseEntity.ok(response);
    }
}

