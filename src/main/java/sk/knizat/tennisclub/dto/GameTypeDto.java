package sk.knizat.tennisclub.dto;

/**
 * Game type as exposed by the API. Mirrors the persistence enum {@code entity.GameType} so that the API
 * contract does not depend on the entity package; the mapper converts by name.
 */
public enum GameTypeDto {
    SINGLES,
    DOUBLES
}
