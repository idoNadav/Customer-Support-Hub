package com.support.customer.mapper;

import com.support.customer.model.Customer;
import com.support.customer.model.dto.CustomerCreateDTO;
import com.support.customer.model.dto.CustomerRequestDTO;
import com.support.customer.model.dto.CustomerResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "openTicketCount", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    Customer toEntity(CustomerCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "openTicketCount", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    Customer toEntity(CustomerRequestDTO dto);

    CustomerResponseDTO toDTO(Customer customer);
}

