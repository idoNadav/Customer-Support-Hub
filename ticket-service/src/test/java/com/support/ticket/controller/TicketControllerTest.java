package com.support.ticket.controller;

import com.support.ticket.mapper.TicketMapper;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.dto.TicketRequestDTO;
import com.support.ticket.model.dto.TicketResponseDTO;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.service.interfaces.ITicketCreationOrchestrator;
import com.support.ticket.service.interfaces.ITicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketControllerTest {

    private static final ThreadLocal<Authentication> AUTHENTICATION_HOLDER = new ThreadLocal<>();

    private MockMvc mockMvc;

    private ITicketCreationOrchestrator ticketCreationOrchestrator;
    private ITicketService ticketService;
    private TicketMapper ticketMapper;
    private TicketController ticketController;

    private ObjectMapper objectMapper;
    private Ticket ticket;
    private TicketRequestDTO requestDTO;
    private TicketResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        AUTHENTICATION_HOLDER.remove();
        ticketCreationOrchestrator = mock(ITicketCreationOrchestrator.class);
        ticketService = mock(ITicketService.class);
        ticketMapper = mock(TicketMapper.class);
        ticketController = new TicketController(ticketCreationOrchestrator, ticketService, ticketMapper);
        
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(ticketController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
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
                        Authentication contextAuth = SecurityContextHolder.getContext().getAuthentication();
                        if (contextAuth != null) {
                            return contextAuth;
                        }
                        throw new IllegalStateException("Authentication not found in ThreadLocal or SecurityContext");
                    }
                })
                .build();

        ticket = Ticket.builder()
                .id("ticket123")
                .customerExternalId("customer123")
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        requestDTO = TicketRequestDTO.builder()
                .customerExternalId("customer123")
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        responseDTO = TicketResponseDTO.builder()
                .id("ticket123")
                .customerExternalId("customer123")
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();
    }

    private Authentication createMockAuthentication(String externalId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", externalId)
                .claim("roles", Arrays.asList(roles))
                .build();
        
        org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter authoritiesConverter = 
            new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        
        java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities = 
            authoritiesConverter.convert(jwt);
        
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @Test
    void createTicket_Success_Returns201() throws Exception {
        when(ticketMapper.toEntity(any(TicketRequestDTO.class))).thenReturn(ticket);
        when(ticketCreationOrchestrator.createTicket(any(Ticket.class), anyString())).thenReturn(ticket);
        when(ticketMapper.toDTO(any(Ticket.class))).thenReturn(responseDTO);

        Authentication auth = createMockAuthentication("customer123", "CUSTOMER");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(post("/api/tickets")
                            .header("Idempotency-Key", "test-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("ticket123"));

            verify(ticketCreationOrchestrator).createTicket(any(Ticket.class), eq("test-key-123"));
        } finally {
            AUTHENTICATION_HOLDER.remove();
        }
    }

    @Test
    void createTicket_Idempotency_ReturnsExistingTicket() throws Exception {
        when(ticketMapper.toEntity(any(TicketRequestDTO.class))).thenReturn(ticket);
        when(ticketCreationOrchestrator.createTicket(any(Ticket.class), eq("existing-key")))
                .thenReturn(ticket);
        when(ticketMapper.toDTO(any(Ticket.class))).thenReturn(responseDTO);

        Authentication auth = createMockAuthentication("customer123", "CUSTOMER");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(post("/api/tickets")
                            .header("Idempotency-Key", "existing-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isCreated());

            verify(ticketCreationOrchestrator).createTicket(any(Ticket.class), eq("existing-key"));
        } finally {
            AUTHENTICATION_HOLDER.remove();
        }
    }

    @Test
    void createTicket_CustomerCreatingForDifferentCustomer_Returns403() throws Exception {
        TicketRequestDTO differentCustomerDTO = TicketRequestDTO.builder()
                .customerExternalId("different-customer")
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        Authentication auth = createMockAuthentication("customer123", "CUSTOMER");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(post("/api/tickets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(differentCustomerDTO)))
                    .andExpect(status().isForbidden());

            verify(ticketCreationOrchestrator, never()).createTicket(any(Ticket.class), anyString());
        } finally {
            AUTHENTICATION_HOLDER.remove();
        }
    }

    @Test
    void addComment_Success_Returns200() throws Exception {
        when(ticketService.findById("ticket123")).thenReturn(Optional.of(ticket));
        when(ticketService.addComment("ticket123", "New comment", "customer123")).thenReturn(ticket);
        when(ticketMapper.toDTO(any(Ticket.class))).thenReturn(responseDTO);

        Authentication auth = createMockAuthentication("customer123", "CUSTOMER");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(post("/api/tickets/ticket123/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("\"New comment\""))
                    .andExpect(status().isOk());

            verify(ticketService).addComment("ticket123", "New comment", "customer123");
        } finally {
            AUTHENTICATION_HOLDER.remove();
        }
    }

    @Test
    void updateStatus_Success_Returns200() throws Exception {
        when(ticketService.updateStatus("ticket123", TicketStatus.IN_PROGRESS, "agent456"))
                .thenReturn(ticket);
        when(ticketMapper.toDTO(any(Ticket.class))).thenReturn(responseDTO);

        Authentication auth = createMockAuthentication("agent456", "AGENT");
        AUTHENTICATION_HOLDER.set(auth);

        try {
            mockMvc.perform(put("/api/tickets/ticket123/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("\"IN_PROGRESS\""))
                    .andExpect(status().isOk());

            verify(ticketService).updateStatus("ticket123", TicketStatus.IN_PROGRESS, "agent456");
        } finally {
            AUTHENTICATION_HOLDER.remove();
        }
    }

}
