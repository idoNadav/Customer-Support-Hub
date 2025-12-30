package com.support.ticket.model.dto;

import com.support.ticket.model.TicketComment;
import com.support.ticket.model.TicketEvent;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponseDTO {

    private String id;
    private String customerExternalId;
    private String title;
    private String description;
    private TicketStatus status;
    private Priority priority;
    private SyncStatus syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketComment> comments;
    private List<TicketEvent> events;
}

