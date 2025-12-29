package com.support.customer.service.interfaces;

import com.support.customer.model.Customer;

import java.util.List;
import java.util.Optional;

public interface ICustomerService {

    Optional<Customer> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    void incrementOpenTicketCount(String externalId);

    Customer updateCustomer(String externalId, Customer customerUpdate);

    List<Customer> searchCustomers(String name, String email, String externalId);
}

