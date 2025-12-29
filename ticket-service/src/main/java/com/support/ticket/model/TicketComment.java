package com.support.ticket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketComment {

    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String content;
    private String authorExternalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TicketComment(String content, String authorExternalId) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.authorExternalId = authorExternalId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

