package com.support.hub.integration;

import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.repository.TicketRepository;
import com.support.ticket.service.TicketCreationOrchestrator;
import com.support.ticket.service.TicketRecoveryService;
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
class CompleteSagaFlowIntegrationTest {

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
    private TicketRecoveryService ticketRecoveryService;

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
    void testCompleteFlowWithFailureAndRecovery() {
        String customerExternalId = "customer-complete-001";
        String idempotencyKey = "complete-flow-key-001";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("Rachel Avraham")
                .email("rachel.avraham@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        int initialCount = customer.getOpenTicketCount();

        Ticket ticket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Complete Flow Ticket")
                .description("Testing complete saga flow")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        Ticket createdTicket = ticketCreationOrchestrator.createTicket(ticket, idempotencyKey);

        assertThat(createdTicket).isNotNull();
        assertThat(createdTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        Customer afterCreation = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(afterCreation.getOpenTicketCount()).isEqualTo(initialCount + 1);

        createdTicket.setSyncStatus(SyncStatus.FAILED);
        ticketRepository.save(createdTicket);

        customer.setOpenTicketCount(initialCount);
        customerRepository.save(customer);

        ticketRecoveryService.recoverFailedTickets();

        Ticket recoveredTicket = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        assertThat(recoveredTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        Customer afterRecovery = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(afterRecovery.getOpenTicketCount()).isEqualTo(initialCount + 1);
    }

    @Test
    void testMultipleTicketsForSameCustomer() {
        String customerExternalId = "customer-multiple-001";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("Tom Cohen")
                .email("tom.cohen@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        Ticket ticket1 = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Ticket 1")
                .description("First ticket")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .build();

        Ticket ticket2 = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Ticket 2")
                .description("Second ticket")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .build();

        Ticket created1 = ticketCreationOrchestrator.createTicket(ticket1, "multi-key-001");
        Ticket created2 = ticketCreationOrchestrator.createTicket(ticket2, "multi-key-002");

        assertThat(created1.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(created2.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        Customer updatedCustomer = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(updatedCustomer.getOpenTicketCount()).isEqualTo(2);
    }
}

