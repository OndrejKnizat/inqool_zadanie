package sk.knizat.tennisclub.mapper;

import org.junit.jupiter.api.Test;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CourtMapperTest {

    private final CourtMapper mapper = new CourtMapper(new SurfaceTypeMapper());

    private static SurfaceType clay() {
        SurfaceType surface = new SurfaceType();
        surface.setId(3L);
        surface.setName("Clay");
        surface.setPricePerMinute(new BigDecimal("2.00"));
        return surface;
    }

    @Test
    void should_trimNameAndSetSurface_when_toEntity() {
        SurfaceType clay = clay();

        Court entity = mapper.toEntity(new CourtRequest(4, "  Centre court ", 3L), clay);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCourtNumber()).isEqualTo(4);
        assertThat(entity.getName()).isEqualTo("Centre court");
        assertThat(entity.getSurfaceType()).isSameAs(clay);
    }

    @Test
    void should_overwriteFields_when_updateEntity() {
        Court entity = new Court();
        entity.setCourtNumber(1);
        entity.setName("Old");
        entity.setSurfaceType(clay());
        SurfaceType grass = new SurfaceType();
        grass.setId(9L);

        mapper.updateEntity(entity, new CourtRequest(2, "New", 9L), grass);

        assertThat(entity.getCourtNumber()).isEqualTo(2);
        assertThat(entity.getName()).isEqualTo("New");
        assertThat(entity.getSurfaceType()).isSameAs(grass);
    }

    @Test
    void should_copyAllFieldsWithNestedSurface_when_toResponse() {
        Court entity = new Court();
        entity.setId(5L);
        entity.setCourtNumber(7);
        entity.setName("Court 7");
        entity.setSurfaceType(clay());

        CourtResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.courtNumber()).isEqualTo(7);
        assertThat(response.name()).isEqualTo("Court 7");
        assertThat(response.surfaceType().id()).isEqualTo(3L);
        assertThat(response.surfaceType().name()).isEqualTo("Clay");
        assertThat(response.surfaceType().pricePerMinute()).isEqualTo(new BigDecimal("2.00"));
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void should_returnNullName_when_nameIsBlankOrNull() {
        assertThat(mapper.normaliseName(null)).isNull();
        assertThat(mapper.normaliseName("")).isNull();
        assertThat(mapper.normaliseName("   ")).isNull();
        assertThat(mapper.normaliseName(" x ")).isEqualTo("x");
    }

    @Test
    void should_returnNull_when_entityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
