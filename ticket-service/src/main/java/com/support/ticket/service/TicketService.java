package com.support.ticket.service;

import com.support.ticket.service.interfaces.ITicketService;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.TicketComment;
import com.support.ticket.model.TicketEvent;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.TicketEventType;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService implements ITicketService {

    private final TicketRepository ticketRepository;

    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public Ticket save(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    public Optional<Ticket> findByIdempotencyKey(String idempotencyKey) {
        return ticketRepository.findByIdempotencyKey(idempotencyKey);
    }

    public Optional<Ticket> findById(String id) {
        return ticketRepository.findById(id);
    }

    public Ticket addComment(String ticketId, String commentContent, String authorExternalId) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        
        TicketComment comment = new TicketComment(commentContent, authorExternalId);
        ticket.addComment(comment);
        
        TicketEvent commentEvent = createTicketEvent(
                TicketEventType.COMMENT_ADDED,
                "Comment added: " + commentContent,
                authorExternalId
        );

        ticket.addEvent(commentEvent);
        return ticketRepository.save(ticket);
    }

    public Ticket updateStatus(String ticketId, TicketStatus newStatus, String performedBy) {
        
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
        
        TicketStatus oldStatus = ticket.getStatus();
        if (oldStatus == newStatus) {
            return ticket;
        }
        
        ticket.setStatus(newStatus);
        
        TicketEvent statusEvent = createTicketEvent(
                TicketEventType.STATUS_CHANGED,
                "Status changed from " + oldStatus + " to " + newStatus,
                performedBy
        );

        ticket.addEvent(statusEvent);
        
        if (newStatus == TicketStatus.CLOSED || newStatus == TicketStatus.CANCELLED) {
            TicketEvent closedEvent = createTicketEvent(
                    TicketEventType.CLOSED,
                    "Ticket " + newStatus.name().toLowerCase(),
                    performedBy
            );
            ticket.addEvent(closedEvent);
        }
        
        return ticketRepository.save(ticket);
    }

    public List<Ticket> findTickets(TicketStatus status, Priority priority, String customerExternalId, 
                                    LocalDateTime fromDate, LocalDateTime toDate) {
                                        
        if (customerExternalId != null && !customerExternalId.isBlank()) {
            if (status != null && priority != null) {
                return ticketRepository.findByCustomerExternalIdAndStatusAndPriority(customerExternalId, status, priority);
            } else if (status != null) {
                return ticketRepository.findByCustomerExternalIdAndStatus(customerExternalId, status);
            } else if (priority != null) {
                return ticketRepository.findByCustomerExternalIdAndPriority(customerExternalId, priority);
            } else {
                return ticketRepository.findByCustomerExternalId(customerExternalId);
            }
        }
        
        if (status != null && priority != null) {
            List<Ticket> allTickets = ticketRepository.findAll();
            return allTickets.stream()
                    .filter(t -> t.getStatus() == status && t.getPriority() == priority)
                    .filter(t -> fromDate == null || !t.getCreatedAt().isBefore(fromDate))
                    .filter(t -> toDate == null || !t.getCreatedAt().isAfter(toDate))
                    .collect(Collectors.toList());
                    
        } else if (status != null) {
            List<Ticket> tickets = ticketRepository.findByStatus(status);
            return filterByDateRange(tickets, fromDate, toDate);
        } else if (priority != null) {
            List<Ticket> tickets = ticketRepository.findByPriority(priority);
            return filterByDateRange(tickets, fromDate, toDate);
        }
        
        List<Ticket> allTickets = ticketRepository.findAll();
        return filterByDateRange(allTickets, fromDate, toDate);
    }

    public List<Ticket> findTicketsByCustomer(String customerExternalId, TicketStatus status, Priority priority) {

        if (status != null && priority != null) {
            return ticketRepository.findByCustomerExternalIdAndStatusAndPriority(customerExternalId, status, priority);
        } else if (status != null) {
            return ticketRepository.findByCustomerExternalIdAndStatus(customerExternalId, status);
        } else if (priority != null) {
            return ticketRepository.findByCustomerExternalIdAndPriority(customerExternalId, priority);
        } else {
            return ticketRepository.findByCustomerExternalId(customerExternalId);
        }
    }

    private List<Ticket> filterByDateRange(List<Ticket> tickets, LocalDateTime fromDate, LocalDateTime toDate) {

        if (fromDate == null && toDate == null) {
            return tickets;
        }
        
        List<Ticket> filtered = new ArrayList<>();
        for (Ticket ticket : tickets) {
            LocalDateTime createdAt = ticket.getCreatedAt();
            if (fromDate != null && createdAt.isBefore(fromDate)) {
                continue;
            }
            if (toDate != null && createdAt.isAfter(toDate)) {
                continue;
            }
            filtered.add(ticket);
        }
        return filtered;
    }

    private TicketEvent createTicketEvent(TicketEventType eventType, String description, String performedBy) {
        return new TicketEvent(eventType, description, performedBy);
    }
}

