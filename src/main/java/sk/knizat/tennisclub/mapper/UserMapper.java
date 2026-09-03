package sk.knizat.tennisclub.mapper;

import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;

/**
 * Hand-written mapping from {@link User} to its DTOs, plus the conversion between the persistence enum
 * {@link Role} and the API enum {@link RoleDto}.
 */
@Component
public class UserMapper {

    /** Maps an entity to the public response (no password hash); returns {@code null} for a {@code null} entity. */
    public UserResponse toResponse(User entity) {
        if (entity == null) {
            return null;
        }
        return new UserResponse(
                entity.getId(),
                entity.getPhoneNumber(),
                entity.getName(),
                toDto(entity.getRole()),
                entity.getPasswordHash() != null,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Maps an entity to the internal authentication view; returns {@code null} for a {@code null} entity. */
    public AuthUser toAuthUser(User entity) {
        if (entity == null) {
            return null;
        }
        return new AuthUser(entity.getId(), entity.getPhoneNumber(), entity.getPasswordHash(), toDto(entity.getRole()));
    }

    /** API enum to persistence enum (by name); {@code null} stays {@code null}. */
    public Role toEntity(RoleDto dto) {
        return dto == null ? null : Role.valueOf(dto.name());
    }

    /** Persistence enum to API enum (by name); {@code null} stays {@code null}. */
    public RoleDto toDto(Role role) {
        return role == null ? null : RoleDto.valueOf(role.name());
    }
}
