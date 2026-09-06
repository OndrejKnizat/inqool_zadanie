# UML diagrams

| Diagram | Source | Rendered |
|---------|--------|----------|
| Class diagram: domain model (JPA entities, enums, audit listener) | [class-domain.puml](class-domain.puml) | [SVG](class-domain.svg) · [PNG](class-domain.png) |
| Class diagram: layers and dependencies (controller → service → dao, security, generic DAO) | [class-layers.puml](class-layers.puml) | [SVG](class-layers.svg) · [PNG](class-layers.png) |
| Sequence diagram: create reservation (`POST /api/reservations`) | [sequence-create-reservation.puml](sequence-create-reservation.puml) | [SVG](sequence-create-reservation.svg) · [PNG](sequence-create-reservation.png) |
| Package / layer diagram (dependencies enforced by ArchUnit) | [package-diagram.puml](package-diagram.puml) | [SVG](package-diagram.svg) · [PNG](package-diagram.png) |

Rendered with PlantUML 1.2026.7 using the built-in `smetana` layout engine (no Graphviz needed):

```bash
java -jar plantuml.jar -tsvg -tpng -o . docs/uml/*.puml
```
