package com.support.hub.integration;

import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import com.support.ticket.model.Ticket;
import com.support.ticket.model.enums.Priority;
import com.support.ticket.model.enums.SyncStatus;
import com.support.ticket.model.enums.TicketStatus;
import com.support.ticket.repository.TicketRepository;
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
class TicketRecoveryIntegrationTest {

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
    void testRecoveryServiceCompensatesFailedTickets() {
        String customerExternalId = "customer-recovery-001";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("Michael Rosen")
                .email("michael.rosen@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        int initialCount = customer.getOpenTicketCount();

        Ticket failedTicket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Failed Ticket")
                .description("This ticket failed to sync")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .syncStatus(SyncStatus.FAILED)
                .idempotencyKey("recovery-key-001")
                .build();
        ticketRepository.save(failedTicket);

        ticketRecoveryService.recoverFailedTickets();

        Ticket recoveredTicket = ticketRepository.findById(failedTicket.getId()).orElseThrow();
        assertThat(recoveredTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        Customer updatedCustomer = customerRepository.findByExternalId(customerExternalId).orElseThrow();
        assertThat(updatedCustomer.getOpenTicketCount()).isEqualTo(initialCount + 1);

        assertThat(recoveredTicket.getEvents()).isNotEmpty();
    }

    @Test
    void testRecoveryServiceHandlesMultipleFailedTickets() {
        String customerExternalId1 = "customer-recovery-002";
        String customerExternalId2 = "customer-recovery-003";

        Customer customer1 = Customer.builder()
                .externalId(customerExternalId1)
                .name("Yael Shalom")
                .email("yael.shalom@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer1);

        Customer customer2 = Customer.builder()
                .externalId(customerExternalId2)
                .name("Noam Bar")
                .email("noam.bar@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer2);

        Ticket failedTicket1 = Ticket.builder()
                .customerExternalId(customerExternalId1)
                .title("Failed Ticket 1")
                .description("First failed ticket")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .syncStatus(SyncStatus.FAILED)
                .idempotencyKey("recovery-key-002")
                .build();
        ticketRepository.save(failedTicket1);

        Ticket failedTicket2 = Ticket.builder()
                .customerExternalId(customerExternalId2)
                .title("Failed Ticket 2")
                .description("Second failed ticket")
                .status(TicketStatus.OPEN)
                .priority(Priority.LOW)
                .syncStatus(SyncStatus.FAILED)
                .idempotencyKey("recovery-key-003")
                .build();
        ticketRepository.save(failedTicket2);

        ticketRecoveryService.recoverFailedTickets();

        Ticket recovered1 = ticketRepository.findById(failedTicket1.getId()).orElseThrow();
        Ticket recovered2 = ticketRepository.findById(failedTicket2.getId()).orElseThrow();

        assertThat(recovered1.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(recovered2.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);

        Customer updatedCustomer1 = customerRepository.findByExternalId(customerExternalId1).orElseThrow();
        Customer updatedCustomer2 = customerRepository.findByExternalId(customerExternalId2).orElseThrow();

        assertThat(updatedCustomer1.getOpenTicketCount()).isEqualTo(1);
        assertThat(updatedCustomer2.getOpenTicketCount()).isEqualTo(1);
    }

    @Test
    void testRecoveryServiceHandlesNonExistentCustomer() {
        String customerExternalId = "non-existent-customer";

        Ticket failedTicket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("Orphaned Ticket")
                .description("Ticket for non-existent customer")
                .status(TicketStatus.OPEN)
                .priority(Priority.MEDIUM)
                .syncStatus(SyncStatus.FAILED)
                .idempotencyKey("recovery-key-004")
                .build();
        ticketRepository.save(failedTicket);

        ticketRecoveryService.recoverFailedTickets();

        Ticket ticket = ticketRepository.findById(failedTicket.getId()).orElseThrow();
        assertThat(ticket.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    void testMongoDBRetryMechanism() {
        String customerExternalId = "customer-mongodb-retry-001";

        Customer customer = Customer.builder()
                .externalId(customerExternalId)
                .name("MongoDB Retry Test")
                .email("mongodb.retry@example.com")
                .openTicketCount(0)
                .build();
        customerRepository.save(customer);

        Ticket ticket = Ticket.builder()
                .customerExternalId(customerExternalId)
                .title("MongoDB Retry Test Ticket")
                .description("Testing MongoDB retry mechanism with Testcontainers")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .syncStatus(SyncStatus.SYNCED)
                .idempotencyKey("mongodb-retry-key-001")
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        assertThat(savedTicket).isNotNull();
        assertThat(savedTicket.getId()).isNotNull();
        assertThat(savedTicket.getTitle()).isEqualTo("MongoDB Retry Test Ticket");

        Ticket retrievedTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
        assertThat(retrievedTicket.getTitle()).isEqualTo("MongoDB Retry Test Ticket");
        assertThat(retrievedTicket.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(retrievedTicket.getCustomerExternalId()).isEqualTo(customerExternalId);

        Ticket updatedTicket = Ticket.builder()
                .id(savedTicket.getId())
                .customerExternalId(customerExternalId)
                .title("Updated Title")
                .description("Updated description")
                .status(TicketStatus.IN_PROGRESS)
                .priority(Priority.HIGH)
                .syncStatus(SyncStatus.SYNCED)
                .idempotencyKey("mongodb-retry-key-001")
                .build();

        Ticket savedUpdatedTicket = ticketRepository.save(updatedTicket);
        assertThat(savedUpdatedTicket.getTitle()).isEqualTo("Updated Title");
        assertThat(savedUpdatedTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        Ticket finalTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
        assertThat(finalTicket.getTitle()).isEqualTo("Updated Title");
    }
}

