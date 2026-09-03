package sk.knizat.tennisclub.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;

/**
 * Hand-written mapping between {@link Court} and its DTOs. The surface type is resolved by the service and
 * passed in as an entity; the name is trimmed and a blank name becomes {@code null}.
 */
@Component
@RequiredArgsConstructor
public class CourtMapper {

    private final SurfaceTypeMapper surfaceTypeMapper;

    /** Creates a new transient entity from the request and the resolved surface type. */
    public Court toEntity(CourtRequest request, SurfaceType surfaceType) {
        Court entity = new Court();
        updateEntity(entity, request, surfaceType);
        return entity;
    }

    /** Copies the request fields and the resolved surface type onto an existing entity. */
    public void updateEntity(Court entity, CourtRequest request, SurfaceType surfaceType) {
        entity.setCourtNumber(request.courtNumber());
        entity.setName(normaliseName(request.name()));
        entity.setSurfaceType(surfaceType);
    }

    /** Maps an entity (with its surface type) to its response; returns {@code null} for a {@code null} entity. */
    public CourtResponse toResponse(Court entity) {
        if (entity == null) {
            return null;
        }
        return new CourtResponse(
                entity.getId(),
                entity.getCourtNumber(),
                entity.getName(),
                surfaceTypeMapper.toResponse(entity.getSurfaceType()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Trims the name; {@code null} or blank becomes {@code null} (the name is optional). */
    public String normaliseName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
