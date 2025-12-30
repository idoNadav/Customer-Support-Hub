package com.support.customer.controller;

import com.support.customer.mapper.CustomerMapper;
import com.support.customer.model.Customer;
import com.support.customer.model.dto.CustomerCreateDTO;
import com.support.customer.model.dto.CustomerRequestDTO;
import com.support.customer.model.dto.CustomerResponseDTO;
import com.support.customer.service.interfaces.ICustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerControllerTest {

    private static final ThreadLocal<Authentication> AUTHENTICATION_HOLDER = new ThreadLocal<>();

    @org.springframework.web.bind.annotation.ControllerAdvice
    static class TestExceptionHandler {
        @org.springframework.web.bind.annotation.ExceptionHandler(com.support.customer.exception.ConflictException.class)
        public org.springframework.http.ResponseEntity<Map<String, Object>> handleConflict(com.support.customer.exception.ConflictException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Conflict");
            response.put("error", ex.getMessage());
            response.put("status", org.springframework.http.HttpStatus.CONFLICT.value());
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(response);
        }
        
        @org.springframework.web.bind.annotation.ExceptionHandler(com.support.customer.exception.ResourceNotFoundException.class)
        public org.springframework.http.ResponseEntity<Map<String, Object>> handleNotFound(com.support.customer.exception.ResourceNotFoundException ex) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", ex.getMessage());
            response.put("status", org.springframework.http.HttpStatus.NOT_FOUND.value());
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(response);
        }
    }

    private MockMvc mockMvc;

    private ICustomerService customerService;
    private CustomerMapper customerMapper;
    private CustomerController customerController;

    private ObjectMapper objectMapper;
    private Customer customer;
    private CustomerCreateDTO createDTO;
    private CustomerResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        AUTHENTICATION_HOLDER.remove();
        customerService = mock(ICustomerService.class);
        customerMapper = mock(CustomerMapper.class);
        customerController = new CustomerController(customerMapper, customerService);
        
        objectMapper = new ObjectMapper();
        
        mockMvc = MockMvcBuilders.standaloneSetup(customerController)
                .setControllerAdvice(new TestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(Authentication.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) throws Exception {
                        Authentication auth = AUTHENTICATION_HOLDER.get();
                        if (auth != null) {
                            return auth;
                        }
                        SecurityContext context = SecurityContextHolder.getContext();
                        if (context != null && context.getAuthentication() != null) {
                            return context.getAuthentication();
                        }
                        throw new IllegalStateException("Authentication not found");
                    }
                })
                .addFilter((request, response, chain) -> {
                    Authentication auth = AUTHENTICATION_HOLDER.get();
                    if (auth != null) {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                    }
                    try {
                        chain.doFilter(request, response);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }, "/*")
                .build();

        customer = Customer.builder()
                .id(1L)
                .externalId("customer123")
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .openTicketCount(0)
                .build();

        createDTO = CustomerCreateDTO.builder()
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .build();

        responseDTO = CustomerResponseDTO.builder()
                .id(1L)
                .externalId("customer123")
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .openTicketCount(0)
                .build();
    }

    private Authentication createMockAuthentication(String externalId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", externalId)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private Authentication createMockAdminAuthentication(String externalId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", externalId)
                .claim("roles", Arrays.asList("ADMIN"))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void createCustomer_Success_Returns201() throws Exception {
        when(customerService.existsByExternalId(anyString())).thenReturn(false);
        when(customerMapper.toEntity(any(CustomerCreateDTO.class))).thenReturn(customer);
        when(customerService.createCustomer(anyString(), any(Customer.class))).thenReturn(customer);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(responseDTO);

        Authentication auth = createMockAdminAuthentication("admin-456");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(post("/api/customers")
                            .with(request -> {
                                AUTHENTICATION_HOLDER.set(auth);
                                SecurityContext context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(auth);
                                SecurityContextHolder.setContext(context);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.externalId").exists())
                    .andExpect(jsonPath("$.externalId").isNotEmpty());

            verify(customerService, atLeastOnce()).existsByExternalId(anyString());
            verify(customerService).createCustomer(anyString(), any(Customer.class));
        } finally {
            AUTHENTICATION_HOLDER.remove();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createCustomer_DuplicateExternalId_RetriesUntilUnique() throws Exception {
        when(customerService.existsByExternalId(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);
        when(customerMapper.toEntity(any(CustomerCreateDTO.class))).thenReturn(customer);
        when(customerService.createCustomer(anyString(), any(Customer.class))).thenReturn(customer);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(responseDTO);

        Authentication auth = createMockAdminAuthentication("admin-456");
        
        try {
            AUTHENTICATION_HOLDER.set(auth);
            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDTO)))
                    .andExpect(status().isCreated());

            verify(customerService, atLeast(2)).existsByExternalId(anyString());
            verify(customerService).createCustomer(anyString(), any(Customer.class));
        } finally {
            AUTHENTICATION_HOLDER.remove();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOwnProfile_Success_Returns200() throws Exception {
        when(customerService.findByExternalId("customer123")).thenReturn(Optional.of(customer));
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(responseDTO);

        Authentication auth = createMockAuthentication("customer123");
        
        try {
            AUTHENTICATION_HOLDER.set(auth);
            mockMvc.perform(get("/api/customers/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.externalId").value("customer123"));

            verify(customerService).findByExternalId("customer123");
        } finally {
            AUTHENTICATION_HOLDER.remove();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateOwnProfile_Success_Returns200() throws Exception {
        CustomerRequestDTO updateDTO = CustomerRequestDTO.builder()
                .name("Updated Name")
                .email("updated@example.com")
                .build();

        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .externalId("customer123")
                .name("Updated Name")
                .email("updated@example.com")
                .build();

        CustomerResponseDTO updatedResponse = CustomerResponseDTO.builder()
                .id(1L)
                .externalId("customer123")
                .name("Updated Name")
                .email("updated@example.com")
                .build();

        when(customerMapper.toEntity(any(CustomerRequestDTO.class))).thenReturn(updatedCustomer);
        when(customerService.updateCustomer(anyString(), any(Customer.class))).thenReturn(updatedCustomer);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(updatedResponse);

        Authentication auth = createMockAuthentication("customer123");
        
        try {
            AUTHENTICATION_HOLDER.set(auth);
            mockMvc.perform(put("/api/customers/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"));

            verify(customerService).updateCustomer("customer123", updatedCustomer);
        } finally {
            AUTHENTICATION_HOLDER.remove();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void searchCustomers_Success_Returns200() throws Exception {
        Customer customer1 = Customer.builder().id(1L).name("Dani Cohen").build();
        Customer customer2 = Customer.builder().id(2L).name("Sarah Levi").build();
        CustomerResponseDTO dto1 = CustomerResponseDTO.builder().id(1L).name("Dani Cohen").build();
        CustomerResponseDTO dto2 = CustomerResponseDTO.builder().id(2L).name("Sarah Levi").build();

        when(customerService.searchCustomers("Dani", null, null))
                .thenReturn(Arrays.asList(customer1, customer2));
        when(customerMapper.toDTO(customer1)).thenReturn(dto1);
        when(customerMapper.toDTO(customer2)).thenReturn(dto2);

        Jwt jwtWithRoles = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "agent456")
                .claim("roles", Arrays.asList("AGENT"))
                .build();
        Authentication authWithRoles = new JwtAuthenticationToken(jwtWithRoles);
        
        try {
            AUTHENTICATION_HOLDER.set(authWithRoles);
            mockMvc.perform(get("/api/customers")
                            .param("name", "Dani"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(customerService).searchCustomers("Dani", null, null);
        } finally {
            AUTHENTICATION_HOLDER.remove();
            SecurityContextHolder.clearContext();
        }
    }
}
