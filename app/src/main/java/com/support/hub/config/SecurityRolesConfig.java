package com.support.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "security.roles")
public class SecurityRolesConfig {

    private TicketRoles ticket = new TicketRoles();
    private CustomerRoles customer = new CustomerRoles();

    public TicketRoles getTicket() {
        return ticket;
    }

    public void setTicket(TicketRoles ticket) {
        this.ticket = ticket;
    }

    public CustomerRoles getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerRoles customer) {
        this.customer = customer;
    }

    @Data
    public static class TicketRoles {
        private String create;
        private String read;
        private String readAll;
        private String updateStatus;

        public String getCreate() {
            return create;
        }

        public void setCreate(String create) {
            this.create = create;
        }

        public String getRead() {
            return read;
        }

        public void setRead(String read) {
            this.read = read;
        }

        public String getReadAll() {
            return readAll;
        }

        public void setReadAll(String readAll) {
            this.readAll = readAll;
        }

        public String getUpdateStatus() {
            return updateStatus;
        }

        public void setUpdateStatus(String updateStatus) {
            this.updateStatus = updateStatus;
        }
    }

    @Data
    public static class CustomerRoles {
        private String readOwn;
        private String updateOwn;
        private String search;
        private String read;

        public String getReadOwn() {
            return readOwn;
        }

        public void setReadOwn(String readOwn) {
            this.readOwn = readOwn;
        }

        public String getUpdateOwn() {
            return updateOwn;
        }

        public void setUpdateOwn(String updateOwn) {
            this.updateOwn = updateOwn;
        }

        public String getSearch() {
            return search;
        }

        public void setSearch(String search) {
            this.search = search;
        }

        public String getRead() {
            return read;
        }

        public void setRead(String read) {
            this.read = read;
        }
    }
}

