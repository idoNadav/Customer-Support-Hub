package com.support.ticket.service;

import com.support.customer.service.CustomerService;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.TicketEvent;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCreationOrchestrator {

    private final TicketService ticketService;
    private final CustomerService customerService;

    public Ticket createTicket(Ticket ticket, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        Optional<Ticket> existingTicket = ticketService.findByIdempotencyKey(idempotencyKey);
        if (existingTicket.isPresent()) {
            log.info("Ticket already exists with idempotency key: {}", idempotencyKey);
            return existingTicket.get();
        }

        String customerExternalId = ticket.getCustomerExternalId();
        
        if (!customerService.existsByExternalId(customerExternalId)) {
            throw new IllegalArgumentException("Customer does not exist: " + customerExternalId);
        }

        ticket.setIdempotencyKey(idempotencyKey);
        
        TicketEvent createdEvent = new TicketEvent(
                TicketEventType.CREATED,
                "Ticket created",
                customerExternalId
        );
        ticket.addEvent(createdEvent);

        Ticket savedTicket = ticketService.save(ticket);

        try {
            syncTicketToCustomer(savedTicket, "Ticket count incremented in MySQL");
            log.info("Ticket created successfully with idempotency key: {}", idempotencyKey);
        } catch (Exception e) {
            log.error("Failed to increment ticket count for customer: {}", customerExternalId, e);
            savedTicket.setSyncStatus(SyncStatus.FAILED);
            ticketService.save(savedTicket);
            throw new RuntimeException("Failed to complete ticket creation: " + e.getMessage(), e);
        }

        return savedTicket;
    }

    public void recoverTicket(Ticket ticket) {
        String customerExternalId = ticket.getCustomerExternalId();
        
        if (!customerService.existsByExternalId(customerExternalId)) {
            log.warn("Customer {} no longer exists for ticket {}", customerExternalId, ticket.getId());
            return;
        }

        try {
            syncTicketToCustomer(ticket, "Ticket count incremented in MySQL (recovered)");
            log.info("Successfully recovered ticket: {}", ticket.getId());
        } catch (Exception e) {
            log.error("Failed to increment ticket count for customer: {} in ticket: {}", 
                    customerExternalId, ticket.getId(), e);
            ticket.setSyncStatus(SyncStatus.FAILED);
            ticketService.save(ticket);
        }
    }

    private void syncTicketToCustomer(Ticket ticket, String eventDescription) {
        String customerExternalId = ticket.getCustomerExternalId();
        customerService.incrementOpenTicketCount(customerExternalId);
        ticket.setSyncStatus(SyncStatus.SYNCED);
        
        TicketEvent syncedEvent = new TicketEvent(
                TicketEventType.STATUS_CHANGED,
                eventDescription,
                customerExternalId
        );
        ticket.addEvent(syncedEvent);
        
        ticketService.save(ticket);
    }
}

