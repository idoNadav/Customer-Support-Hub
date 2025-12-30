package com.support.ticket.service.interfaces;

import com.support.ticket.model.Ticket;

public interface ITicketCreationOrchestrator {

    Ticket createTicket(Ticket ticket, String idempotencyKey);

    void recoverTicket(Ticket ticket);
}

