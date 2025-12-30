package com.support.customer.service;

import com.support.customer.service.interfaces.ICustomerService;
import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerService implements ICustomerService {

    private final CustomerRepository customerRepository;

    public Optional<Customer> findByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId);
    }

    public boolean existsByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId).isPresent();
    }

    @Transactional
    public Customer createCustomer(String externalId, Customer customer) {
        if (customerRepository.findByExternalId(externalId).isPresent()) {
            throw new IllegalArgumentException("Customer with externalId already exists: " + externalId);
        }
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException("Customer with email already exists: " + customer.getEmail());
        }
        customer.setExternalId(externalId);
        customer.setOpenTicketCount(0);
        return customerRepository.save(customer);
    }

    @Transactional
    @CircuitBreaker(name = "mysqlService")
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void incrementOpenTicketCount(String externalId) {

        Customer customer = customerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + externalId));
        
        customer.setOpenTicketCount(customer.getOpenTicketCount() + 1);
        customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(String externalId, Customer customerUpdate) {
        Customer customer = customerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + externalId));
        
        if (customerUpdate.getName() != null) {
            customer.setName(customerUpdate.getName());
        }
        if (customerUpdate.getEmail() != null) {
            String newEmail = customerUpdate.getEmail();
            if (!newEmail.equals(customer.getEmail()) && customerRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Customer with email already exists: " + newEmail);
            }
            customer.setEmail(newEmail);
        }
        
        return customerRepository.save(customer);
    }

    public List<Customer> searchCustomers(String name, String email, String externalId) {
        Set<Customer> resultSet = new HashSet<>();
        
        if (name != null && !name.isBlank()) {
            resultSet.addAll(customerRepository.findByNameContainingIgnoreCase(name));
        }
        if (email != null && !email.isBlank()) {
            resultSet.addAll(customerRepository.findByEmailContainingIgnoreCase(email));
        }
        if (externalId != null && !externalId.isBlank()) {
            resultSet.addAll(customerRepository.findByExternalIdContaining(externalId));
        }
        
        if (name == null && email == null && externalId == null) {
            return customerRepository.findAll();
        }
        
        return new ArrayList<>(resultSet);
    }
}

