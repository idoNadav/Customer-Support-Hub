package com.support.ticket.mapper;

import com.support.ticket.model.Ticket;
import com.support.ticket.model.dto.TicketRequestDTO;
import com.support.ticket.model.dto.TicketResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "syncStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "events", ignore = true)
    Ticket toEntity(TicketRequestDTO dto);

    TicketResponseDTO toDTO(Ticket ticket);
}

