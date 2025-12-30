package com.support.ticket.service;

import com.support.customer.service.interfaces.ICustomerService;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.TicketEvent;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketEventType;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.service.interfaces.ITicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCreationOrchestratorTest {

    @Mock
    private ITicketService ticketService;

    @Mock
    private ICustomerService customerService;

    @InjectMocks
    private TicketCreationOrchestrator orchestrator;

    private Ticket ticket;
    private String customerExternalId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        customerExternalId = "customer123";
        idempotencyKey = "idempotency-key-123";
        ticket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();
    }

    @Test
    void createTicket_Success() {
        when(ticketService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(customerService.existsByExternalId(customerExternalId)).thenReturn(true);
        when(ticketService.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId("ticket123");
            return saved;
        });
        doNothing().when(customerService).incrementOpenTicketCount(customerExternalId);

        Ticket result = orchestrator.createTicket(ticket, idempotencyKey);

        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(result.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getEvents().get(0).getEventType()).isEqualTo(TicketEventType.CREATED);
        verify(ticketService).findByIdempotencyKey(idempotencyKey);
        verify(customerService).existsByExternalId(customerExternalId);
        verify(customerService).incrementOpenTicketCount(customerExternalId);
        verify(ticketService, atLeast(2)).save(any(Ticket.class));
    }

    @Test
    void createTicket_Idempotency_ReturnsExistingTicket() {
        Ticket existingTicket = Ticket.builder()
                .id("existing-ticket-123")
                .idempotencyKey(idempotencyKey)
                .customerExternalId(customerExternalId)
                .build();

        when(ticketService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTicket));

        Ticket result = orchestrator.createTicket(ticket, idempotencyKey);

        assertThat(result.getId()).isEqualTo("existing-ticket-123");
        verify(ticketService).findByIdempotencyKey(idempotencyKey);
        verify(customerService, never()).existsByExternalId(anyString());
        verify(ticketService, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_CustomerNotFound_ThrowsException() {
        when(ticketService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(customerService.existsByExternalId(customerExternalId)).thenReturn(false);

        assertThatThrownBy(() -> orchestrator.createTicket(ticket, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer does not exist");

        verify(ticketService).findByIdempotencyKey(idempotencyKey);
        verify(customerService).existsByExternalId(customerExternalId);
        verify(ticketService, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_AutoGeneratesIdempotencyKey() {
        when(ticketService.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(customerService.existsByExternalId(customerExternalId)).thenReturn(true);
        when(ticketService.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId("ticket123");
            return saved;
        });
        doNothing().when(customerService).incrementOpenTicketCount(customerExternalId);

        Ticket result = orchestrator.createTicket(ticket, null);

        assertThat(result.getIdempotencyKey()).isNotNull();
        assertThat(result.getIdempotencyKey()).isNotEmpty();
        verify(ticketService).findByIdempotencyKey(anyString());
    }

    @Test
    void createTicket_SyncFailure_SetsFailedStatus() {
        when(ticketService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(customerService.existsByExternalId(customerExternalId)).thenReturn(true);
        when(ticketService.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId("ticket123");
            return saved;
        });
        doThrow(new RuntimeException("MySQL connection failed"))
                .when(customerService).incrementOpenTicketCount(customerExternalId);

        assertThatThrownBy(() -> orchestrator.createTicket(ticket, idempotencyKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to complete ticket creation");

        verify(ticketService, atLeastOnce()).save(argThat(t -> 
            t.getSyncStatus() == SyncStatus.FAILED));
    }

    @Test
    void recoverTicket_Success() {
        Ticket failedTicket = Ticket.builder()
                .id("ticket123")
                .customerExternalId(customerExternalId)
                .syncStatus(SyncStatus.FAILED)
                .build();

        when(customerService.existsByExternalId(customerExternalId)).thenReturn(true);
        when(ticketService.save(any(Ticket.class))).thenReturn(failedTicket);
        doNothing().when(customerService).incrementOpenTicketCount(customerExternalId);

        orchestrator.recoverTicket(failedTicket);

        assertThat(failedTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        verify(customerService).existsByExternalId(customerExternalId);
        verify(customerService).incrementOpenTicketCount(customerExternalId);
        verify(ticketService).save(failedTicket);
    }

    @Test
    void recoverTicket_CustomerNotFound_LogsWarning() {
        Ticket failedTicket = Ticket.builder()
                .id("ticket123")
                .customerExternalId(customerExternalId)
                .syncStatus(SyncStatus.FAILED)
                .build();

        when(customerService.existsByExternalId(customerExternalId)).thenReturn(false);

        orchestrator.recoverTicket(failedTicket);

        verify(customerService).existsByExternalId(customerExternalId);
        verify(customerService, never()).incrementOpenTicketCount(anyString());
        verify(ticketService, never()).save(any(Ticket.class));
    }
}

