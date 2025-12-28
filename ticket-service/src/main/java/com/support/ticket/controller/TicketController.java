package com.support.ticket.controller;

import com.support.ticket.mapper.TicketMapper;
import com.support.ticket.model.dto.TicketRequestDTO;
import com.support.ticket.model.dto.TicketResponseDTO;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.service.TicketCreationOrchestrator;
import com.support.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketCreationOrchestrator ticketCreationOrchestrator;
    private final TicketService ticketService;
    private final TicketMapper ticketMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'ADMIN')")
    public ResponseEntity<TicketResponseDTO> createTicket(
            @Valid @RequestBody TicketRequestDTO ticketRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            Ticket ticket = ticketMapper.toEntity(ticketRequest);
            Ticket createdTicket = ticketCreationOrchestrator.createTicket(ticket, idempotencyKey);
            TicketResponseDTO response = ticketMapper.toDTO(createdTicket);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String customerExternalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        List<Ticket> tickets = ticketService.findTickets(status, priority, customerExternalId, fromDate, toDate);
        List<TicketResponseDTO> response = tickets.stream()
                .map(ticketMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TicketResponseDTO>> getOwnTickets(
            Authentication authentication,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority) {
        String customerExternalId = getExternalIdFromAuthentication(authentication);
        List<Ticket> tickets = ticketService.findTicketsByCustomer(customerExternalId, status, priority);
        List<TicketResponseDTO> response = tickets.stream()
                .map(ticketMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'ADMIN')")
    public ResponseEntity<TicketResponseDTO> getTicketById(
            @PathVariable String id,
            Authentication authentication) {
        return ticketService.findById(id)
                .map(ticket -> {
                    String externalId = getExternalIdFromAuthentication(authentication);
                    if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
                        if (!ticket.getCustomerExternalId().equals(externalId)) {
                            return ResponseEntity.<TicketResponseDTO>status(HttpStatus.FORBIDDEN).build();
                        }
                    }
                    TicketResponseDTO response = ticketMapper.toDTO(ticket);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'ADMIN')")
    public ResponseEntity<TicketResponseDTO> addComment(
            @PathVariable String id,
            @RequestBody String commentContent,
            Authentication authentication) {
        try {
            String authorExternalId = getExternalIdFromAuthentication(authentication);
            Ticket updatedTicket = ticketService.addComment(id, commentContent, authorExternalId);
            TicketResponseDTO response = ticketMapper.toDTO(updatedTicket);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable String id,
            @RequestBody TicketStatus newStatus,
            Authentication authentication) {
        try {
            String performedBy = getExternalIdFromAuthentication(authentication);
            Ticket updatedTicket = ticketService.updateStatus(id, newStatus, performedBy);
            TicketResponseDTO response = ticketMapper.toDTO(updatedTicket);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private String getExternalIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return authentication.getName();
    }
}

