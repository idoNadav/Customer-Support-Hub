package com.support.hub.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component("roles")
public class SecurityRolesHelper {

    private final SecurityRolesConfig rolesConfig;

    public SecurityRolesHelper(SecurityRolesConfig rolesConfig) {
        this.rolesConfig = rolesConfig;
    }

    public boolean hasAnyTicketCreateRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getTicket().getCreate());
    }

    public boolean hasAnyTicketReadRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getTicket().getRead());
    }

    public boolean hasAnyTicketReadAllRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getTicket().getReadAll());
    }

    public boolean hasAnyTicketUpdateStatusRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getTicket().getUpdateStatus());
    }

    public boolean hasCustomerReadOwnRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getCustomer().getReadOwn());
    }

    public boolean hasCustomerUpdateOwnRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getCustomer().getUpdateOwn());
    }

    public boolean hasAnyCustomerSearchRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getCustomer().getSearch());
    }

    public boolean hasAnyCustomerReadRole(Authentication authentication) {
        return hasAnyRole(authentication, rolesConfig.getCustomer().getRead());
    }

    public boolean hasAnyAdminOrAgentRole(Authentication authentication) {
        return hasAnyRole(authentication, "AGENT,ADMIN");
    }

    private boolean hasAnyRole(Authentication authentication, String rolesString) {

        if (authentication == null || rolesString == null || rolesString.isBlank()) {
            return false;
        }

        Set<String> requiredRoles = Arrays.stream(rolesString.split(","))
                .map(String::trim)
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toSet());

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredRoles::contains);
    }
}

