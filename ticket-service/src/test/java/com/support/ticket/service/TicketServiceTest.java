package com.support.ticket.service;

import com.support.ticket.model.Ticket;
import com.support.ticket.model.TicketComment;
import com.support.ticket.model.TicketEvent;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.TicketEventType;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketService ticketService;

    private Ticket ticket;
    private String ticketId;
    private String customerExternalId;

    @BeforeEach
    void setUp() {
        ticketId = "ticket123";
        customerExternalId = "customer123";
        ticket = Ticket.builder()
                .id(ticketId)
                .customerExternalId(customerExternalId)
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();
    }

    @Test
    void addComment_Success() {
        String commentContent = "This is a comment";
        String authorExternalId = "agent456";

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        Ticket result = ticketService.addComment(ticketId, commentContent, authorExternalId);

        assertThat(result.getComments()).hasSize(1);
        assertThat(result.getComments().get(0).getContent()).isEqualTo(commentContent);
        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getEvents().get(0).getEventType()).isEqualTo(TicketEventType.COMMENT_ADDED);
        verify(ticketRepository).findById(ticketId);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void addComment_TicketNotFound_ThrowsException() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.addComment(ticketId, "comment", "author"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(ticketRepository).findById(ticketId);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateStatus_Success() {
        TicketStatus newStatus = TicketStatus.IN_PROGRESS;
        String performedBy = "agent456";

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        Ticket result = ticketService.updateStatus(ticketId, newStatus, performedBy);

        assertThat(result.getStatus()).isEqualTo(newStatus);
        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getEvents().get(0).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        verify(ticketRepository).findById(ticketId);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void updateStatus_ToClosed_CreatesClosedEvent() {
        TicketStatus newStatus = TicketStatus.CLOSED;
        String performedBy = "agent456";

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        Ticket result = ticketService.updateStatus(ticketId, newStatus, performedBy);

        assertThat(result.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getEvents().get(0).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(result.getEvents().get(1).getEventType()).isEqualTo(TicketEventType.CLOSED);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void findTickets_WithFilters_ReturnsFilteredResults() {
        Ticket ticket1 = Ticket.builder().id("t1").status(TicketStatus.OPEN).priority(Priority.HIGH).build();
        Ticket ticket2 = Ticket.builder().id("t2").status(TicketStatus.OPEN).priority(Priority.MEDIUM).build();

        when(ticketRepository.findAll()).thenReturn(Arrays.asList(ticket1, ticket2));

        List<Ticket> result = ticketService.findTickets(TicketStatus.OPEN, Priority.HIGH, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("t1");
    }

    @Test
    void findById_TicketNotFound_ReturnsEmpty() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        Optional<Ticket> result = ticketService.findById(ticketId);

        assertThat(result).isEmpty();
        verify(ticketRepository).findById(ticketId);
    }
}

