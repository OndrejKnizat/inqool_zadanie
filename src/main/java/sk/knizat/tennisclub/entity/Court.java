package sk.knizat.tennisclub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A tennis court identified by its business court number, played on one {@link SurfaceType}. */
@Entity
@Table(name = "court")
@Getter
@Setter
@NoArgsConstructor
public class Court extends BaseEntity {

    @Column(name = "court_number", nullable = false)
    private Integer courtNumber;

    @Column(name = "name", length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "surface_type_id", nullable = false)
    private SurfaceType surfaceType;
}
