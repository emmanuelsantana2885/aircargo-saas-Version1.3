package com.aircargo.authservice.dto;

import com.aircargo.common.entity.Airline;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.Site;
import com.aircargo.authservice.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserDTO {

    private UUID id;
    private UUID airlineId;
    private UUID supabaseUid;
    private String email;
    private String fullName;
    private UserRole role;
    private Boolean isActive;
    private Boolean blocked;
    private OffsetDateTime lastLogin;
    private OffsetDateTime createdAt;
    private List<UUID> siteIds;
    private OffsetDateTime updatedAt;
    private Boolean mfaEnabled;
    private Boolean mfaLocked;
    private Boolean mustChangePassword;
    private Boolean passwordSet;

    public static AppUserDTO fromEntity(AppUser entity){
        if(entity == null) return null;
        return AppUserDTO.builder()
                .id(entity.getId())
                .airlineId(entity.getAirline() != null ? entity.getAirline().getId() : null)
                .supabaseUid(entity.getSupabaseUid())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .role(entity.getRole())
                .isActive(entity.getIsActive())
                .blocked(Boolean.TRUE.equals(entity.getBlocked()))
                .siteIds(entity.getSites().stream().map(Site::getId).toList())
                .lastLogin(entity.getLastLogin())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .mfaEnabled(entity.getMfaEnabled())
                .mfaLocked(entity.getMfaLocked())
                .mustChangePassword(entity.getMustChangePassword())
                .passwordSet(entity.getPasswordHash() != null)
                .build();
    }

    public static AppUser toEntity(AppUserDTO dto){
        if(dto == null) return null;
        AppUser entity = new AppUser();
        entity.setId(dto.getId());
        if (dto.getAirlineId() != null) {
            Airline airline = new Airline();
            airline.setId(dto.getAirlineId());
            entity.setAirline(airline);
        }
        entity.setSupabaseUid(dto.getSupabaseUid());
        entity.setEmail(dto.getEmail());
        entity.setFullName(dto.getFullName());
        entity.setRole(dto.getRole());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        entity.setLastLogin(dto.getLastLogin());
        return entity;
    }
}
