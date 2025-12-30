package com.support.customer.controller;

import com.support.customer.mapper.CustomerMapper;
import com.support.customer.model.Customer;
import com.support.customer.model.dto.CustomerCreateDTO;
import com.support.customer.model.dto.CustomerRequestDTO;
import com.support.customer.model.dto.CustomerResponseDTO;
import com.support.customer.service.interfaces.ICustomerService;
import com.support.customer.exception.ResourceNotFoundException;
import com.support.customer.exception.ConflictException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;
    private final ICustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_AGENT', 'ROLE_ADMIN')")
    public ResponseEntity<CustomerResponseDTO> createCustomer(
            @Valid @RequestBody CustomerCreateDTO customerCreateDTO,
            Authentication authentication) {

        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }

        String jwtSub = getExternalIdFromAuthentication(authentication);
        String externalId;

        if (customerCreateDTO.getExternalId() != null && !customerCreateDTO.getExternalId().isBlank()) {
            externalId = customerCreateDTO.getExternalId();

            if (externalId.equals(jwtSub)) {
                throw new IllegalArgumentException("ADMIN/AGENT cannot create themselves as CUSTOMER");
            }

            String lowerExternalId = externalId.toLowerCase();
            if (lowerExternalId.startsWith("admin-") || lowerExternalId.startsWith("agent-")) {
                throw new IllegalArgumentException("ADMIN/AGENT cannot create customers with externalId starting with 'admin-' or 'agent-'");
            }
        } else {
            long randomNum = Math.abs(new Random().nextLong() % 1000000);
            externalId = "customer" + randomNum;
        }

        if (customerService.existsByExternalId(externalId)) {
            throw new ConflictException("Customer with externalId already exists: " + externalId);
        }

        Customer customer = customerMapper.toEntity(customerCreateDTO);
        Customer createdCustomer = customerService.createCustomer(externalId, customer);
        CustomerResponseDTO response = customerMapper.toDTO(createdCustomer);
        log.info("Customer created: externalId={}, createdBy={}", externalId, jwtSub);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("@roles.hasCustomerReadOwnRole(authentication)")
    public ResponseEntity<CustomerResponseDTO> getOwnProfile(Authentication authentication) {
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        String externalId = getExternalIdFromAuthentication(authentication);
        Customer customer = customerService.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        
        CustomerResponseDTO response = customerMapper.toDTO(customer);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @PreAuthorize("@roles.hasCustomerUpdateOwnRole(authentication)")
    public ResponseEntity<CustomerResponseDTO> updateOwnProfile(
            @Valid @RequestBody CustomerRequestDTO customerRequest,
            Authentication authentication) {

        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        String externalId = getExternalIdFromAuthentication(authentication);
        try {
            Customer customerUpdate = customerMapper.toEntity(customerRequest);
            Customer updatedCustomer = customerService.updateCustomer(externalId, customerUpdate);
            CustomerResponseDTO response = customerMapper.toDTO(updatedCustomer);
            log.info("Customer profile updated: externalId={}", externalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("email already exists")) {
                throw new ConflictException(e.getMessage());
            }
            throw new ResourceNotFoundException("Customer not found");
        }
    }

    @GetMapping
    @PreAuthorize("@roles.hasAnyCustomerSearchRole(authentication)")
    public ResponseEntity<List<CustomerResponseDTO>> searchCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String externalId) {

        List<Customer> customers = customerService.searchCustomers(name, email, externalId);
        List<CustomerResponseDTO> response = customers.stream()
                .map(customerMapper::toDTO)
                .collect(Collectors.toList());
        log.info("Customer search performed: results={}, filters: name={}, email={}, externalId={}", 
            customers.size(), name, email, externalId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{externalId}")
    @PreAuthorize("@roles.hasAnyCustomerReadRole(authentication)")
    public ResponseEntity<CustomerResponseDTO> getCustomerByExternalId(@PathVariable String externalId) {
        Customer customer = customerService.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        
        CustomerResponseDTO response = customerMapper.toDTO(customer);
        return ResponseEntity.ok(response);
    }

    private String getExternalIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return authentication.getName();
    }

    private boolean hasAnyAdminOrAgentRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ROLE_AGENT"));
    }
}

