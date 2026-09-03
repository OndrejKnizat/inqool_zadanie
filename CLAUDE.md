# CLAUDE.md – pravidlá projektu (platia pre všetkých agentov)

Rezervačný systém tenisového klubu (zadanie InQool). Záväzná špecifikácia je `docs/ARCHITECTURE.md`
(všetky otvorené otázky v sekcii 9 sú rozhodnuté tak, ako je tam napísané). Zadanie: `docs/zadanie.md`.
Čítaj iba tie sekcie ARCHITECTURE.md, ktoré tvoja úloha potrebuje.

## Tvrdé pravidlá (porušenie = neakceptovaný výsledok)

1. **Spring Data JPA je zakázané.** Žiadny `spring-boot-starter-data-jpa`, žiadny import z `org.springframework.data..`
   (ani `Page`, `Sort`, `Pageable`, auditing). DAO vrstva používa výhradne `jakarta.persistence.EntityManager` + JPQL,
   cez generický `AbstractDao<T extends BaseEntity>`. Maven enforcer aj ArchUnit test to vynucujú.
2. **Vrstvy:** `controller` → `service` → `dao`. Controller nikdy nesiaha na `dao` ani `entity`, nikdy nevracia ani
   neprijíma entitu (iba DTO `record`y). `@Transactional` iba v `service`. `EntityManager` iba v `dao`.
3. **Soft delete:** nikdy `em.remove()`. Všetky entity dedia `BaseEntity` (`deleted`, `deletedAt`). Každý JPQL dotaz
   explicitne filtruje `e.deleted = false`. Žiadne Hibernate `@SQLDelete`/`@Where`/`@SoftDelete`.
4. **Testy sú súčasťou každej úlohy**, nie samostatný krok. JaCoCo limit 90 % (INSTRUCTION aj LINE) beží vo
   `./mvnw verify` a musí prejsť. Vylúčená je iba main trieda `TennisClubApplication`.
5. Všetky endpointy pod `/api`. Chyby ako RFC 7807 `ProblemDetail`: 400 validácia (aj prekrývanie rezervácie),
   404 neexistuje/zmazané, 409 konflikt stavu, 401 chýbajúci/neplatný token, 403 nedostatočná rola.

## Stack

Java 21, Spring Boot 3.5.x, Maven (`./mvnw`), Hibernate 6 (cez `spring-boot-starter-jdbc` + `hibernate-core` +
`spring-orm`), H2 in-memory, Liquibase (schéma výhradne z changelogu, `ddl-auto=validate`), Lombok
(entity iba `@Getter/@Setter/@NoArgsConstructor`, nikdy `@Data` na entite; `lombok.config` má
`lombok.addLombokGeneratedAnnotation=true`), Spring Security 6 + `spring-boot-starter-oauth2-resource-server`
(Nimbus JWT, HS256), springdoc-openapi, JUnit 5, Mockito, AssertJ, ArchUnit.

## Konvencie

- Koreňový balík `sk.knizat.tennisclub`; balíky podľa vrstiev: `config`, `controller`, `dto`, `mapper`, `service`
  (+ `service.impl`), `dao` (+ `dao.impl`), `entity`, `exception`, `security`. Štruktúra je v ARCHITECTURE.md §4.
- DTO sú Java `record`y s Bean Validation anotáciami. Mapovanie entita↔DTO ručne v `mapper` (bez MapStruct).
- Service aj DAO majú interface + impl. Konštruktorová injekcia (`@RequiredArgsConstructor`), žiadny `@Autowired` na poliach.
- Čas: `Instant` v API (ISO-8601) aj v entitách; „teraz“ vždy cez injektovaný `java.time.Clock` (bean v configu), nikdy `Instant.now()` v biznis kóde.
- Peniaze: `BigDecimal`, scale 2, `HALF_UP`.
- Telefónne číslo: pred validáciou aj uložením odstrániť medzery a pomlčky, potom regex `^\+?[0-9]{7,15}$`.
- Konfigurácia: prefix `app.*` cez `@ConfigurationProperties` record `AppProperties` (viď ARCHITECTURE.md §6).
- Javadoc na verejných triedach a netriviálnych metódach, stručný. Komentáre a dokumentácia v angličtine, kód v angličtine.
- Žiadne `System.out`, používaj SLF4J.
- Testy: názvy metód `should_<expected>_when_<condition>`; unit testy s Mockito bez Spring kontextu, kde sa dá.
  Testovacie profily: `src/test/resources/application-test.yml` (aktivovaný `@ActiveProfiles("test")`).
- Nemeň `docs/ARCHITECTURE.md` bez explicitného pokynu. Ak narazíš na nejasnosť, rozhodni sa podľa ARCHITECTURE.md
  a napíš to do svojej záverečnej správy.

## Overenie práce

Pred odovzdaním vždy spusti `./mvnw -q verify` (kompilácia, testy, ArchUnit, JaCoCo 90 %) a odovzdaj až keď prejde.
Nekomituj – commit robí hlavný agent po revízii.
