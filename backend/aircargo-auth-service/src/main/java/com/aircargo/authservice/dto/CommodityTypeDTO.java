package com.aircargo.authservice.dto;

import com.aircargo.authservice.entity.CommodityTypeEntity;

import java.util.UUID;

public record CommodityTypeDTO(
    UUID id,
    String code,
    String label,
    String description,
    String color,
    Integer sortOrder,
    Boolean isActive
) {
    public static CommodityTypeDTO fromEntity(CommodityTypeEntity entity) {
        return new CommodityTypeDTO(
            entity.getId(),
            entity.getCode(),
            entity.getLabel(),
            entity.getDescription(),
            entity.getColor(),
            entity.getSortOrder(),
            entity.getIsActive()
        );
    }
}
