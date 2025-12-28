package com.support.ticket.model;

import com.support.ticket.model.enums.TicketEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEvent {

    private TicketEventType eventType;
    private String description;
    private String performedBy;
    private LocalDateTime timestamp;

    public TicketEvent(TicketEventType eventType, String description, String performedBy) {
        this.eventType = eventType;
        this.description = description;
        this.performedBy = performedBy;
        this.timestamp = LocalDateTime.now();
    }
}

