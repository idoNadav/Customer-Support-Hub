package com.support.ticket.model;

import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    private String id;

    @Indexed
    @NotBlank(message = "Customer external ID is required")
    private String customerExternalId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Status is required")
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @NotNull(message = "Priority is required")
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Indexed(unique = true)
    private String idempotencyKey;

    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder.Default
    private List<TicketComment> comments = new ArrayList<>();

    @Builder.Default
    private List<TicketEvent> events = new ArrayList<>();

    public void addComment(TicketComment comment) {
        if (this.comments == null) {
            this.comments = new ArrayList<>();
        }
        this.comments.add(comment);
        this.updatedAt = LocalDateTime.now();
    }

    public void addEvent(TicketEvent event) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(event);
        this.updatedAt = LocalDateTime.now();
    }
}

