package com.support.ticket.service.interfaces;

import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ITicketService {

    Ticket save(Ticket ticket);

    Optional<Ticket> findByIdempotencyKey(String idempotencyKey);

    Optional<Ticket> findById(String id);

    Ticket addComment(String ticketId, String commentContent, String authorExternalId);

    Ticket updateStatus(String ticketId, TicketStatus newStatus, String performedBy);

    List<Ticket> findTickets(TicketStatus status, Priority priority, String customerExternalId,
                             LocalDateTime fromDate, LocalDateTime toDate);

    List<Ticket> findTicketsByCustomer(String customerExternalId, TicketStatus status, Priority priority);
}

