package sk.knizat.tennisclub.mapper;

import org.junit.jupiter.api.Test;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.entity.SurfaceType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceTypeMapperTest {

    private final SurfaceTypeMapper mapper = new SurfaceTypeMapper();

    @Test
    void should_trimNameAndNormaliseScale_when_toEntity() {
        SurfaceType entity = mapper.toEntity(new SurfaceTypeRequest("  Clay ", new BigDecimal("2.5")));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getName()).isEqualTo("Clay");
        assertThat(entity.getPricePerMinute()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void should_roundHalfUp_when_priceHasMoreThanTwoDecimals() {
        assertThat(mapper.normalisePrice(new BigDecimal("1.005"))).isEqualTo(new BigDecimal("1.01"));
        assertThat(mapper.normalisePrice(new BigDecimal("3"))).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void should_overwriteFields_when_updateEntity() {
        SurfaceType entity = new SurfaceType();
        entity.setName("Old");
        entity.setPricePerMinute(BigDecimal.ONE);

        mapper.updateEntity(entity, new SurfaceTypeRequest("Grass", new BigDecimal("4")));

        assertThat(entity.getName()).isEqualTo("Grass");
        assertThat(entity.getPricePerMinute()).isEqualTo(new BigDecimal("4.00"));
    }

    @Test
    void should_copyAllFields_when_toResponse() {
        SurfaceType entity = new SurfaceType();
        entity.setId(5L);
        entity.setName("Hard");
        entity.setPricePerMinute(new BigDecimal("3.00"));

        SurfaceTypeResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Hard");
        assertThat(response.pricePerMinute()).isEqualTo(new BigDecimal("3.00"));
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void should_returnNull_when_inputIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.normaliseName(null)).isNull();
        assertThat(mapper.normalisePrice(null)).isNull();
    }
}
