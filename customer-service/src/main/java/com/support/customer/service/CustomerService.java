package com.support.customer.service;

import com.support.customer.model.Customer;
import com.support.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Optional<Customer> findByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId);
    }

    public boolean existsByExternalId(String externalId) {
        return customerRepository.findByExternalId(externalId).isPresent();
    }

    @Transactional
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
}

