package sk.knizat.tennisclub.mapper;

import org.junit.jupiter.api.Test;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.entity.GameType;
import sk.knizat.tennisclub.entity.Reservation;
import sk.knizat.tennisclub.support.Fixtures;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationMapperTest {

    private final ReservationMapper mapper = new ReservationMapper();

    @Test
    void should_copyGraphFields_when_toResponse() {
        Reservation entity = Fixtures.reservation(
                Fixtures.court(3, Fixtures.surfaceType("Clay", "2.00")),
                Fixtures.user("+421900000001", "Jane"),
                Fixtures.T10, Fixtures.T11);
        entity.setId(42L);
        entity.setGameType(GameType.DOUBLES);
        entity.setPrice(new BigDecimal("180.00"));

        ReservationResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.courtNumber()).isEqualTo(3);
        assertThat(response.courtName()).isEqualTo("Court 3");
        assertThat(response.startTime()).isEqualTo(Fixtures.T10);
        assertThat(response.endTime()).isEqualTo(Fixtures.T11);
        assertThat(response.gameType()).isEqualTo(GameTypeDto.DOUBLES);
        assertThat(response.price()).isEqualTo(new BigDecimal("180.00"));
        assertThat(response.customer().phoneNumber()).isEqualTo("+421900000001");
        assertThat(response.customer().name()).isEqualTo("Jane");
        assertThat(response.createdAt()).isNull();
    }

    @Test
    void should_returnNull_when_entityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void should_convertByName_when_mappingEnums() {
        assertThat(mapper.toEntity(GameTypeDto.SINGLES)).isEqualTo(GameType.SINGLES);
        assertThat(mapper.toEntity(GameTypeDto.DOUBLES)).isEqualTo(GameType.DOUBLES);
        assertThat(mapper.toDto(GameType.SINGLES)).isEqualTo(GameTypeDto.SINGLES);
        assertThat(mapper.toDto(GameType.DOUBLES)).isEqualTo(GameTypeDto.DOUBLES);
    }

    @Test
    void should_returnNull_when_enumIsNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void should_coverEveryConstant_when_enumsCompared() {
        assertThat(GameTypeDto.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(GameType.values()).map(Enum::name)
                        .toArray(String[]::new));
    }
}
