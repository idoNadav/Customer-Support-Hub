package com.support.ticket.service;

import com.support.ticket.service.interfaces.ITicketCreationOrchestrator;
import com.support.ticket.service.interfaces.ITicketRecoveryService;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketRecoveryService implements ITicketRecoveryService {

    private final TicketRepository ticketRepository;
    private final ITicketCreationOrchestrator ticketCreationOrchestrator;

    @Scheduled(fixedDelay = 300000)
    public void recoverFailedTickets() {
        List<Ticket> failedTickets = ticketRepository.findBySyncStatus(SyncStatus.FAILED);
        log.info("Found {} failed tickets to retry recovery", failedTickets.size());

        for (Ticket ticket : failedTickets) {
            try {
                ticketCreationOrchestrator.recoverTicket(ticket);
            } catch (Exception e) {
                log.error("Failed to retry recovery for ticket: {}", ticket.getId(), e);
            }
        }
    }
}

