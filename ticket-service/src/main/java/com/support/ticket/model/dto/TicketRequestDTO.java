package com.support.ticket.model.dto;

import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketRequestDTO {

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
}

