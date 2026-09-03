package sk.knizat.tennisclub.dto;

/**
 * User role as exposed by the API. Mirrors the persistence enum {@code entity.Role} so that the API contract
 * does not depend on the entity package; the mapper converts by name.
 */
public enum RoleDto {
    USER,
    ADMIN
}
