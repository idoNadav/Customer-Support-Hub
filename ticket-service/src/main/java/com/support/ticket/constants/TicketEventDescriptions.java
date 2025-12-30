package com.support.ticket.constants;

public final class TicketEventDescriptions {

    public static final String TICKET_CREATED = "Ticket created";
    public static final String TICKET_COUNT_INCREMENTED = "Ticket count incremented in MySQL";
    public static final String TICKET_COUNT_INCREMENTED_RECOVERED = "Ticket count incremented in MySQL (recovered)";

    private TicketEventDescriptions() {
        throw new UnsupportedOperationException("Utility class");
    }
}

