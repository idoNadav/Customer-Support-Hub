package com.support.ticket.service;

import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.service.interfaces.ITicketCreationOrchestrator;
import com.support.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketRecoveryServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ITicketCreationOrchestrator ticketCreationOrchestrator;

    @InjectMocks
    private TicketRecoveryService recoveryService;

    private Ticket failedTicket1;
    private Ticket failedTicket2;

    @BeforeEach
    void setUp() {
        failedTicket1 = Ticket.builder()
                .id("ticket1")
                .customerExternalId("customer1")
                .syncStatus(SyncStatus.FAILED)
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .build();

        failedTicket2 = Ticket.builder()
                .id("ticket2")
                .customerExternalId("customer2")
                .syncStatus(SyncStatus.FAILED)
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();
    }

    @Test
    void recoverFailedTickets_ProcessesFailedTickets() {
        List<Ticket> failedTickets = Arrays.asList(failedTicket1, failedTicket2);

        when(ticketRepository.findBySyncStatus(SyncStatus.FAILED)).thenReturn(failedTickets);
        doNothing().when(ticketCreationOrchestrator).recoverTicket(any(Ticket.class));

        recoveryService.recoverFailedTickets();

        verify(ticketRepository).findBySyncStatus(SyncStatus.FAILED);
        verify(ticketCreationOrchestrator).recoverTicket(failedTicket1);
        verify(ticketCreationOrchestrator).recoverTicket(failedTicket2);
    }

    @Test
    void recoverFailedTickets_EmptyList_NoProcessing() {
        when(ticketRepository.findBySyncStatus(SyncStatus.FAILED)).thenReturn(Collections.emptyList());

        recoveryService.recoverFailedTickets();

        verify(ticketRepository).findBySyncStatus(SyncStatus.FAILED);
        verify(ticketCreationOrchestrator, never()).recoverTicket(any(Ticket.class));
    }
}

