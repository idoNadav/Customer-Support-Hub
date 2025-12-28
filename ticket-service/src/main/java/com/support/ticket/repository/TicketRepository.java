package com.support.ticket.repository;

import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends MongoRepository<Ticket, String> {
    Optional<Ticket> findByIdempotencyKey(String idempotencyKey);
    List<Ticket> findBySyncStatus(SyncStatus syncStatus);
    List<Ticket> findByCustomerExternalId(String customerExternalId);
    List<Ticket> findByStatus(TicketStatus status);
    List<Ticket> findByPriority(Priority priority);
    List<Ticket> findByStatusAndPriority(TicketStatus status, Priority priority);
    List<Ticket> findByCustomerExternalIdAndStatus(String customerExternalId, TicketStatus status);
    List<Ticket> findByCustomerExternalIdAndPriority(String customerExternalId, Priority priority);
    List<Ticket> findByCustomerExternalIdAndStatusAndPriority(String customerExternalId, TicketStatus status, Priority priority);
    List<Ticket> findByCreatedAtBetween(LocalDateTime fromDate, LocalDateTime toDate);
}

