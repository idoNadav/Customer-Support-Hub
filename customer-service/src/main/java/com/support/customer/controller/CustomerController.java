package com.support.customer.controller;

import com.support.customer.mapper.CustomerMapper;
import com.support.customer.model.Customer;
import com.support.customer.model.dto.CustomerRequestDTO;
import com.support.customer.model.dto.CustomerResponseDTO;
import com.support.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;
    private final CustomerService customerService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponseDTO> getOwnProfile(Authentication authentication) {
        String externalId = getExternalIdFromAuthentication(authentication);
        return customerService.findByExternalId(externalId)
                .map(customer -> {
                    CustomerResponseDTO response = customerMapper.toDTO(customer);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponseDTO> updateOwnProfile(
            @Valid @RequestBody CustomerRequestDTO customerRequest,
            Authentication authentication) {
        String externalId = getExternalIdFromAuthentication(authentication);
        try {
            Customer customerUpdate = customerMapper.toEntity(customerRequest);
            Customer updatedCustomer = customerService.updateCustomer(externalId, customerUpdate);
            CustomerResponseDTO response = customerMapper.toDTO(updatedCustomer);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<CustomerResponseDTO>> searchCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String externalId) {
        List<Customer> customers = customerService.searchCustomers(name, email, externalId);
        List<CustomerResponseDTO> response = customers.stream()
                .map(customerMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<CustomerResponseDTO> getCustomerByExternalId(@PathVariable String externalId) {
        return customerService.findByExternalId(externalId)
                .map(customer -> {
                    CustomerResponseDTO response = customerMapper.toDTO(customer);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/{externalId}/increment-ticket-count")
    public ResponseEntity<Void> incrementTicketCount(@PathVariable String externalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{externalId}/verify")
    public ResponseEntity<Boolean> verifyCustomerExists(@PathVariable String externalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private String getExternalIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return authentication.getName();
    }
}

