package com.clubflow.club;

import java.util.UUID;

public record DepartmentDto(UUID id, String name, UUID clubId) {
    public static DepartmentDto from(Department d) {
        return new DepartmentDto(d.getId(), d.getName(), d.getClub().getId());
    }
}
