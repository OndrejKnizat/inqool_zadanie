package sk.knizat.tennisclub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Court surface (clay, grass, ...) with its per-minute price; an API-managed code list. */
@Entity
@Table(name = "surface_type")
@Getter
@Setter
@NoArgsConstructor
public class SurfaceType extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price_per_minute", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerMinute;
}
