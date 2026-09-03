package sk.knizat.tennisclub.mapper;

import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.entity.SurfaceType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Hand-written mapping between {@link SurfaceType} and its DTOs. The name is trimmed and the price is
 * normalised to scale 2 ({@code HALF_UP}) on the way in.
 */
@Component
public class SurfaceTypeMapper {

    /** Creates a new transient entity from the request. */
    public SurfaceType toEntity(SurfaceTypeRequest request) {
        SurfaceType entity = new SurfaceType();
        updateEntity(entity, request);
        return entity;
    }

    /** Copies the request fields onto an existing entity. */
    public void updateEntity(SurfaceType entity, SurfaceTypeRequest request) {
        entity.setName(normaliseName(request.name()));
        entity.setPricePerMinute(normalisePrice(request.pricePerMinute()));
    }

    /** Maps an entity to its response; returns {@code null} for a {@code null} entity. */
    public SurfaceTypeResponse toResponse(SurfaceType entity) {
        if (entity == null) {
            return null;
        }
        return new SurfaceTypeResponse(
                entity.getId(),
                entity.getName(),
                entity.getPricePerMinute(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Trims the name; {@code null} stays {@code null} (Bean Validation rejects it earlier). */
    public String normaliseName(String name) {
        return name == null ? null : name.trim();
    }

    /** Sets scale 2 with {@code HALF_UP}; {@code null} stays {@code null}. */
    public BigDecimal normalisePrice(BigDecimal price) {
        return price == null ? null : price.setScale(2, RoundingMode.HALF_UP);
    }
}
