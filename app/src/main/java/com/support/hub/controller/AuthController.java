package com.support.hub.controller;

import com.support.hub.config.SecurityRolesConfig;
import com.support.hub.model.dto.LoginRequestDTO;
import com.support.hub.model.dto.LoginResponseDTO;
import com.support.hub.service.JwtTokenService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final SecurityRolesConfig securityRolesConfig;

    private Set<String> validRoles;

    @PostConstruct
    public void initValidRoles() {
        validRoles = extractValidRoles();
        log.debug("Valid roles for login: {}", validRoles);
    }

    private Set<String> extractValidRoles() {
        Set<String> roles = new HashSet<>();
        
        SecurityRolesConfig.TicketRoles ticketRoles = securityRolesConfig.getTicket();
        SecurityRolesConfig.CustomerRoles customerRoles = securityRolesConfig.getCustomer();
        
        Stream.of(
                ticketRoles.getCreate(),
                ticketRoles.getRead(),
                ticketRoles.getReadAll(),
                ticketRoles.getUpdateStatus(),
                customerRoles.getReadOwn(),
                customerRoles.getUpdateOwn(),
                customerRoles.getSearch(),
                customerRoles.getRead()
        )
        .filter(roleString -> roleString != null && !roleString.isBlank())
        .flatMap(roleString -> Arrays.stream(roleString.split(",")))
        .map(String::trim)
        .map(String::toUpperCase)
        .forEach(roles::add);
        
        return roles;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        String role = loginRequest.getRole().toUpperCase();
        
        if (!validRoles.contains(role)) {
            log.warn("Invalid role attempted: {}. Valid roles: {}", role, validRoles);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String token = jwtTokenService.generateToken(loginRequest.getSub(), role);
        Long expiresIn = jwtTokenService.getExpirationTime();

        LoginResponseDTO response = new LoginResponseDTO(token, "Bearer", expiresIn);
        
        log.info("Token generated for sub: {}, role: {}", loginRequest.getSub(), role);
        
        return ResponseEntity.ok(response);
    }
}

