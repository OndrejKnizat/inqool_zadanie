package sk.knizat.tennisclub.mapper;

import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.reservation.CustomerResponse;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.entity.GameType;
import sk.knizat.tennisclub.entity.Reservation;

/**
 * Hand-written mapping from {@link Reservation} (with its court and user loaded) to the response DTO, plus
 * the conversion between the persistence enum {@link GameType} and the API enum {@link GameTypeDto}.
 */
@Component
public class ReservationMapper {

    /** Maps an entity and its court/user graph to the response; returns {@code null} for a {@code null} entity. */
    public ReservationResponse toResponse(Reservation entity) {
        if (entity == null) {
            return null;
        }
        return new ReservationResponse(
                entity.getId(),
                entity.getCourt().getCourtNumber(),
                entity.getCourt().getName(),
                entity.getStartTime(),
                entity.getEndTime(),
                toDto(entity.getGameType()),
                entity.getPrice(),
                new CustomerResponse(entity.getUser().getPhoneNumber(), entity.getUser().getName()),
                entity.getCreatedAt());
    }

    /** API enum to persistence enum (by name); {@code null} stays {@code null}. */
    public GameType toEntity(GameTypeDto dto) {
        return dto == null ? null : GameType.valueOf(dto.name());
    }

    /** Persistence enum to API enum (by name); {@code null} stays {@code null}. */
    public GameTypeDto toDto(GameType gameType) {
        return gameType == null ? null : GameTypeDto.valueOf(gameType.name());
    }
}
