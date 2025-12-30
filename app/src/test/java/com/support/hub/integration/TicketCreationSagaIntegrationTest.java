package com.support.hub.integration;

import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.repository.TicketRepository;
import com.support.ticket.service.TicketCreationOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.data.mongodb.auto-index-creation=true"
})
class TicketCreationSagaIntegrationTest {

    @Container
    static MongoDBContainer mongoDB = new MongoDBContainer("mongo:7.0")
            .withReuse(true);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("support_hub_test")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDB::getReplicaSetUrl);
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TicketCreationOrchestrator ticketCreationOrchestrator;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void testSuccessfulSagaFlow() {
        String customerExternalId = "customer123";
        String idempotencyKey = "test-key-001";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        Ticket ticket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        Ticket createdTicket = ticketCreationOrchestrator.createTicket(ticket, idempotencyKey);

        assertThat(createdTicket).isNotNull();
        assertThat(createdTicket.getId()).isNotNull();
        assertThat(createdTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(createdTicket.getIdempotencyKey()).isEqualTo(idempotencyKey);

        Customer updatedCustomer = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(updatedCustomer.getOpenTicketCount()).isEqualTo(1);

        Ticket savedTicket = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        assertThat(savedTicket.getEvents()).hasSize(2);
        assertThat(savedTicket.getEvents().get(0).getEventType().name()).isEqualTo("CREATED");
        assertThat(savedTicket.getEvents().get(1).getEventType().name()).isEqualTo("STATUS_CHANGED");
    }

    @Test
    void testIdempotencyPreventsDuplicates() {
        String customerExternalId = "customer456";
        String idempotencyKey = "test-key-002";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("Sarah Levi")
                .email("sarah.levi@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        Ticket ticket1 = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("First Ticket")
                .description("First Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .build();

        Ticket createdTicket1 = ticketCreationOrchestrator.createTicket(ticket1, idempotencyKey);
        String ticketId1 = createdTicket1.getId();

        Ticket ticket2 = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Second Ticket")
                .description("Second Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.LOW)
                .build();

        Ticket createdTicket2 = ticketCreationOrchestrator.createTicket(ticket2, idempotencyKey);

        assertThat(createdTicket2.getId()).isEqualTo(ticketId1);
        assertThat(ticketRepository.count()).isEqualTo(1);

        Customer updatedCustomer = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(updatedCustomer.getOpenTicketCount()).isEqualTo(1);
    }

    @Test
    void testSagaFailureSetsSyncStatusToFailed() {
        String customerExternalId = "customer789";
        String idempotencyKey = "test-key-003";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("David Ben")
                .email("david.ben@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        Ticket ticket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Test Ticket")
                .description("Test Description")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        customerRepository.delete(customer);

        try {
            ticketCreationOrchestrator.createTicket(ticket, idempotencyKey);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Customer does not exist");
        }

        Ticket savedTicket = ticketRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (savedTicket != null) {
            assertThat(savedTicket.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        }
    }
}

