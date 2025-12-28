package com.support.ticket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketComment {

    private String id;
    private String content;
    private String authorExternalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TicketComment(String content, String authorExternalId) {
        this.content = content;
        this.authorExternalId = authorExternalId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

