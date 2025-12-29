package com.support.customer.service;

import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer customer;
    private String externalId;

    @BeforeEach
    void setUp() {
        externalId = "customer123";
        customer = Customer.builder()
                .id(1L)
                .externalId(externalId)
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .openTicketCount(0)
                .build();
    }

    @Test
    void createCustomer_Success() {
        Customer newCustomer = Customer.builder()
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .build();

        when(customerRepository.findByExternalId(externalId)).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        Customer result = customerService.createCustomer(externalId, newCustomer);

        assertThat(result.getExternalId()).isEqualTo(externalId);
        assertThat(result.getOpenTicketCount()).isEqualTo(0);
        verify(customerRepository).findByExternalId(externalId);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomer_DuplicateExternalId_ThrowsException() {
        Customer newCustomer = Customer.builder()
                .name("Dani Cohen")
                .email("dani.cohen@example.com")
                .build();

        when(customerRepository.findByExternalId(externalId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.createCustomer(externalId, newCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(customerRepository).findByExternalId(externalId);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void incrementOpenTicketCount_Success() {
        when(customerRepository.findByExternalId(externalId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        customerService.incrementOpenTicketCount(externalId);

        assertThat(customer.getOpenTicketCount()).isEqualTo(1);
        verify(customerRepository).findByExternalId(externalId);
        verify(customerRepository).save(customer);
    }

    @Test
    void incrementOpenTicketCount_CustomerNotFound_ThrowsException() {
        when(customerRepository.findByExternalId(externalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.incrementOpenTicketCount(externalId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(customerRepository).findByExternalId(externalId);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomer_Success() {
        Customer updateData = Customer.builder()
                .name("Updated Name")
                .email("updated@example.com")
                .build();

        when(customerRepository.findByExternalId(externalId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        Customer result = customerService.updateCustomer(externalId, updateData);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getEmail()).isEqualTo("updated@example.com");
        verify(customerRepository).findByExternalId(externalId);
        verify(customerRepository).save(customer);
    }

    @Test
    void searchCustomers_WithFilters_ReturnsFilteredResults() {
        Customer customer1 = Customer.builder().name("Dani Cohen").email("dani@example.com").build();
        Customer customer2 = Customer.builder().name("Sarah Levi").email("sarah@example.com").build();

        when(customerRepository.findByNameContainingIgnoreCase("Dani")).thenReturn(Arrays.asList(customer1));
        when(customerRepository.findByEmailContainingIgnoreCase("dani@example.com")).thenReturn(Arrays.asList(customer1));

        List<Customer> result = customerService.searchCustomers("Dani", "dani@example.com", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Dani Cohen");
    }
}

