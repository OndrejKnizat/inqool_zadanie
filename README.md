# Tennis Club Reservations

[![CI](https://github.com/OndrejKnizat/inqool_zadanie/actions/workflows/ci.yml/badge.svg)](https://github.com/OndrejKnizat/inqool_zadanie/actions/workflows/ci.yml)

REST API for managing a tennis club (InQool assignment, see [docs/zadanie.md](docs/zadanie.md)): a code list of
surface types with a price per minute, courts, court reservations with price calculation and overlap validation,
and JWT-secured user management. Built with Spring Boot 3.5 and Hibernate 6 through plain JPA (`EntityManager` +
JPQL, **no Spring Data**), H2 in-memory database and Liquibase migrations.

- Design specification: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (data model, layers, endpoints, decisions)
- UML: [class diagram](docs/uml/class-diagram.svg), [sequence diagram of creating a reservation](docs/uml/sequence-create-reservation.svg),
  [package diagram](docs/uml/package-diagram.svg) (PlantUML sources in [docs/uml/](docs/uml/))
- AI-assisted development: [docs/ai/README.md](docs/ai/README.md) (workflow, files given to the agent, chat history)

## Assignment checklist

| Requirement / bonus | Where it is fulfilled |
|---------------------|-----------------------|
| Spring Boot | `pom.xml` (parent `spring-boot-starter-parent` 3.5), `TennisClubApplication` |
| JPA / Hibernate as ORM | `hibernate-core` + `spring-orm` + `spring-boot-starter-jdbc`; entities in `entity/`, `ddl-auto=validate` |
| In-memory database | H2 (`jdbc:h2:mem:tennis`), `application.yml` |
| DAO **without** Spring Data JPA | `dao/GenericDao`, `dao/AbstractDao` (`EntityManager` + JPQL) and `dao/impl/*`; enforced by the Maven enforcer rule (`org.springframework.data:*` banned) and the ArchUnit rule `no_spring_data_dependency` in `ArchitectureTest` |
| Unit tests covering at least 90 % | JaCoCo `check` (INSTRUCTION and LINE ≥ 90 %) runs in `./mvnw verify`; currently 462 tests, 99.9 % instruction and 100 % line coverage (`target/site/jacoco/index.html`) |
| Every endpoint under `/api` | all controllers in `controller/`, see [Endpoint reference](#endpoint-reference) |
| Surface type code list managed by the user | `SurfaceTypeController` CRUD (`/api/surface-types`) |
| CRUD courts, RUD reservations | `CourtController`, `ReservationController` |
| Reservations by court number ordered by creation date | `GET /api/reservations?courtNumber=` (`ReservationDaoImpl.findByCourtNumberOrderByCreatedAt`, `ORDER BY createdAt`) |
| Reservations by phone number, optionally future only | `GET /api/reservations?phoneNumber=&futureOnly=true` |
| Create reservation with court, game type, phone, name; returns price; overlap validation | `POST /api/reservations` → `ReservationServiceImpl.create` + `PriceCalculator` (doubles ×1.5); overlap → `400` |
| Customer created on first reservation, same name per phone | `ReservationServiceImpl.findOrCreateCustomer` |
| Soft delete over all entities | `BaseEntity.markDeleted`, every JPQL filters `deleted = false`, `em.remove` is never called (ArchUnit rule `entity_manager_remove_is_never_called`; behaviour covered by the DAO tests) |
| Data initialisation switch in external configuration | `app.data-init.enabled` → `config/DataInitializer` (2 surface types, 4 courts) |
| UML and class diagram in the repository | `docs/uml/` |
| AI chat history and files given to the agent | `docs/ai/`, `CLAUDE.md`, `docs/ARCHITECTURE.md` |
| Bonus: Liquibase | `src/main/resources/db/changelog/` (schema exclusively from changelogs) |
| Bonus: Lombok | `@Getter/@Setter/@NoArgsConstructor` on entities, `@RequiredArgsConstructor`, `@Slf4j`; `lombok.config` |
| Bonus: 400 on bad requests (e.g. invalid reservation) | Bean Validation + `GlobalExceptionHandler` (RFC 7807), `ValidationException` for interval/overlap rules |
| Bonus: user management | `UserController` (`/api/users`), bootstrap admin (`config/AdminInitializer`) |
| Bonus: roles USER (read + create reservation) / ADMIN (everything) | `config/SecurityConfig` matcher, `security/AccessTokenAuthenticationConverter` |
| Bonus: JWT on all endpoints except `/api/auth/*` | `SecurityConfig` (OAuth2 resource server, HS256), `security/JwtTokenService` |
| Bonus: login via Basic auth on `POST /api/auth/login`, token in the `Authorization` header | `AuthController.login` (`Authorization: Bearer <access>` header + body) |
| Bonus: JWT secret, access and refresh validity in external configuration | `app.security.jwt.*` (`APP_JWT_SECRET`, ...), see [Configuration](#configuration) |
| Bonus: 401 for missing/expired/invalid token, 403 for insufficient role | `security/RestAuthenticationEntryPoint`, `security/RestAccessDeniedHandler`; `AuthApiTest`, `H2ConsoleSecurityTest`, controller API tests |

## Tech stack

Java 21 · Spring Boot 3.5 (Web, Validation, Security, OAuth2 resource server) · Hibernate 6 (JPA) · H2 · Liquibase ·
Lombok · springdoc-openapi (Swagger UI) · JUnit 5, Mockito, AssertJ, ArchUnit, JaCoCo · Maven wrapper.

## Prerequisites

- JDK 21 or newer (verified with 21 and 25; annotation processors are configured explicitly, as JDK 23+ no longer
  picks them up from the classpath). Maven is bundled via the wrapper `./mvnw`; on Windows use `mvnw.cmd`.

## Build, test, run

```bash
./mvnw verify                                             # compile, all tests, ArchUnit, JaCoCo 90 % gate
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run on :8080 with dev defaults (see Profiles)
./mvnw package -DskipTests && java -jar target/tennis-club-reservations-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

After start:

- Swagger UI: <http://localhost:8080/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`). Click **Authorize**:
  `basicAuth` for the login operation, `bearerAuth` (paste the access token) for everything else.
- H2 console (`dev` profile only): <http://localhost:8080/h2-console>, JDBC URL `jdbc:h2:mem:tennis`, user `sa`,
  empty password.
- JaCoCo report after `verify`: `target/site/jacoco/index.html`.

### Profiles

| Profile | Behaviour |
|---------|-----------|
| *(default)* | production-like: `APP_JWT_SECRET` (≥ 32 characters) **must** be set or the application fails fast at startup; data initialisation off; H2 console off |
| `dev` | built-in development JWT secret, `app.data-init.enabled=true`, H2 console enabled |
| `test` | used by the test suite (`src/test/resources/application-test.yml`) |

## Configuration

All `app.*` properties (bound to `config/AppProperties`) can be overridden with environment variables.

| Property | Env variable | Default | Description |
|----------|--------------|---------|-------------|
| `app.data-init.enabled` | `APP_DATA_INIT_ENABLED` | `false` (`true` in `dev`) | create default surface types and courts at startup |
| `app.security.jwt.secret` | `APP_JWT_SECRET` | none (required); `dev` profile has a built-in dev secret | HS256 secret, at least 32 characters |
| `app.security.jwt.access-token-validity` | `APP_SECURITY_JWT_ACCESS_TOKEN_VALIDITY` | `PT15M` | access token lifetime (ISO-8601 duration) |
| `app.security.jwt.refresh-token-validity` | `APP_SECURITY_JWT_REFRESH_TOKEN_VALIDITY` | `P7D` | refresh token lifetime |
| `app.security.admin.enabled` | `APP_SECURITY_ADMIN_ENABLED` | `true` | create bootstrap ADMIN account |
| `app.security.admin.phone-number` | `APP_SECURITY_ADMIN_PHONE_NUMBER` | `+420000000000` | admin login |
| `app.security.admin.name` | `APP_SECURITY_ADMIN_NAME` | `Administrator` | admin display name |
| `app.security.admin.password` | `APP_ADMIN_PASSWORD` | `admin` | admin password |
| `app.reservation.min-duration` | `APP_RESERVATION_MIN_DURATION` | `PT15M` | shortest allowed reservation (ISO-8601 duration) |
| `app.reservation.max-duration` | `APP_RESERVATION_MAX_DURATION` | `PT4H` | longest allowed reservation |

## Initial data

With `app.data-init.enabled=true` (env `APP_DATA_INIT_ENABLED=true`, or simply the `dev` profile) the application
seeds two surface types, "Antuka" (2.50/min) and "Umelá tráva" (3.00/min), and four courts: "Court 1" and
"Court 2" on Antuka, "Court 3" and "Court 4" on Umelá tráva. The seeding goes through the service layer and is
idempotent: if any court already exists, nothing is created. Otherwise an existing surface type with the same name
is reused (so a partially completed previous run can finish) and only the missing surface types and the courts are created.

## Domain rules

- Times are ISO-8601 instants in UTC; a reservation is the half-open interval `[startTime, endTime)` on whole
  minutes, not in the past, between `min-duration` and `max-duration`, and must not overlap another non-deleted
  reservation on the same court (`400`). Creation and update lock the court row, so concurrent requests cannot
  produce overlaps.
- Price = whole minutes × `pricePerMinute` of the court surface, ×1.5 for `DOUBLES`, `HALF_UP` to two decimals,
  stored as a snapshot (a later surface price change does not touch existing reservations). No currency is stored.
- Phone numbers are normalised before validation and storage (spaces and hyphens removed) and must match
  `^\+?[0-9]{7,15}$`; a reservation for an unknown phone number creates the customer (role `USER`, no password).
- Deletes are soft: deleted rows keep their data, disappear from every query and free their business key (surface
  name, court number, phone number) for reuse. A surface type used by a court, a court or a user with unfinished
  reservations, your own account and the last administrator cannot be deleted (`409`).

## Security

Roles: `USER` (every `GET /api/**` except `/api/users/**`, plus `GET /api/users/me` and `POST /api/reservations`) and
`ADMIN` (everything under `/api/**`, including all `POST`/`PUT`/`DELETE` and the whole `/api/users/**`). Customers created implicitly by a reservation
have role `USER` but no password, so they cannot log in until an ADMIN sets one. The auth endpoints, Swagger UI
(`/swagger-ui.html`, `/v3/api-docs`) and the H2 console (`dev` profile) are public.

1. **Login** – `POST /api/auth/login` with HTTP Basic (username = phone number, password). The response carries the
   access token both in the `Authorization: Bearer <access>` header and in the body together with a refresh token
   and both expiry times. Wrong credentials, an unknown phone number or an account without a password give
   `401` (`application/problem+json`).
2. **Authenticated calls** – send `Authorization: Bearer <access>`; a missing, malformed or expired token gives
   `401`, a valid token with an insufficient role gives `403`. Access tokens live 15 minutes by default.
3. **Refresh** – `POST /api/auth/refresh` with `{"refreshToken": "..."}` returns a new pair (same header + body).
   Refresh tokens live 7 days and are accepted only by this endpoint; an access token sent here, or a refresh token
   sent as a bearer token, is rejected with `401`.

Tokens are HS256 JWTs signed with `APP_JWT_SECRET`. With `app.security.admin.enabled=true` (the default) a bootstrap
ADMIN is created at startup when no user with the configured phone number exists: phone `+420000000000`, name
`Administrator`, password `APP_ADMIN_PASSWORD` (default `admin`). Change the password for anything but local development.

Passwords are 8 to 72 characters and are stored as BCrypt hashes; responses never contain the hash, only
`canLogin`. The resource server authorises from the token's `role` claim, so a role change or deletion affects
already issued access tokens only when they expire (15 minutes by default); every account lookup
(`/api/users/me`, login, refresh) of a deleted user fails immediately.

## Endpoint reference

All errors are RFC 7807 `application/problem+json` (`400` validation, `401` no/invalid/expired token,
`403` insufficient role, `404` unknown or deleted, `409` state conflict). Validation problems carry an `errors` map.

| Method | Path | Role | Success | Errors / notes |
|--------|------|------|---------|----------------|
| `POST` | `/api/auth/login` | public (HTTP Basic) | `200` + `Authorization` header, `{accessToken, refreshToken, accessExpiresAt, refreshExpiresAt}` | `401` bad credentials |
| `POST` | `/api/auth/refresh` | public | `200` + `Authorization` header, same body | `401` invalid/expired refresh token |
| `GET` | `/api/surface-types` | USER | `200` list | |
| `GET` | `/api/surface-types/{id}` | USER | `200` | `404` |
| `POST` | `/api/surface-types` | ADMIN | `201` + `Location`, `{name, pricePerMinute}` | `400`, `409` duplicate name |
| `PUT` | `/api/surface-types/{id}` | ADMIN | `200` | `400`, `404`, `409`; price change does not affect existing reservations |
| `DELETE` | `/api/surface-types/{id}` | ADMIN | `204` | `404`, `409` used by a court |
| `GET` | `/api/courts` | USER | `200` list | |
| `GET` | `/api/courts/{id}` | USER | `200` | `404` |
| `POST` | `/api/courts` | ADMIN | `201` + `Location`, `{courtNumber, name?, surfaceTypeId}` | `400` (also unknown `surfaceTypeId`), `409` duplicate court number |
| `PUT` | `/api/courts/{id}` | ADMIN | `200` | `400`, `404`, `409` |
| `DELETE` | `/api/courts/{id}` | ADMIN | `204` | `404`, `409` unfinished reservations |
| `GET` | `/api/reservations` | USER | `200` list; filters `courtNumber` (ordered by `createdAt`), `phoneNumber`, `futureOnly` (default `false`); no filter → all, ordered by `startTime` | `400` bad parameter |
| `GET` | `/api/reservations/{id}` | USER | `200` | `404` |
| `POST` | `/api/reservations` | USER | `201` + `Location`, `{courtNumber, startTime, endTime, gameType, phoneNumber, customerName}` → response includes `price` | `400` invalid interval **or overlap**, `404` court number, `409` court lock timeout or concurrent customer creation (retry) |
| `PUT` | `/api/reservations/{id}` | ADMIN | `200`, `{courtNumber, startTime, endTime, gameType}`; price recalculated, customer unchanged | `400` (incl. overlap), `404`, `409` court lock timeout |
| `DELETE` | `/api/reservations/{id}` | ADMIN | `204` | `404` |
| `GET` | `/api/users` | ADMIN | `200` list | |
| `GET` | `/api/users/me` | USER | `200` own account (token subject) | `404` when the account was deleted after the token was issued |
| `GET` | `/api/users/{id}` | ADMIN | `200` | `404` |
| `POST` | `/api/users` | ADMIN | `201` + `Location`, `{phoneNumber, name, password, role}` | `400`, `409` duplicate phone |
| `PUT` | `/api/users/{id}` | ADMIN | `200`, `{name, role, password?}` (omitted password is kept) | `400`, `404`, `409` demoting the last admin |
| `DELETE` | `/api/users/{id}` | ADMIN | `204` | `404`, `409` own account, last admin, unfinished reservations |

`gameType` is `SINGLES` or `DOUBLES`; `role` is `USER` or `ADMIN`. Anything not listed above under `/api/**`
requires ADMIN by default.

## curl walkthrough

Start the app with the `dev` profile (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`); the commands use
`curl` and `jq`. Reservations must start in the future, so the example date is tomorrow (on macOS use
`date -u -v+1d +%F`).

```bash
BASE=http://localhost:8080
DAY=$(date -u -d '+1 day' +%F)

# 1. log in as the bootstrap admin; the access token is also in the Authorization response header
curl -s -D - -o /dev/null -u +420000000000:admin -X POST $BASE/api/auth/login | grep -i '^Authorization'
TOKEN=$(curl -s -u +420000000000:admin -X POST $BASE/api/auth/login | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# 2. seeded surface types and courts
curl -s -H "$AUTH" $BASE/api/surface-types | jq .
curl -s -H "$AUTH" $BASE/api/courts | jq .

# 3. create a surface type (ADMIN) -> 201, id 3 in a fresh dev database
curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" \
     -d '{"name":"Hard court","pricePerMinute":4.00}' $BASE/api/surface-types | jq .

# 4. create a court on it -> 201
curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" \
     -d '{"courtNumber":5,"name":"Centre court","surfaceTypeId":3}' $BASE/api/courts | jq .

# 5. create a reservation (doubles, 60 min x 4.00 x 1.5) -> 201 with "price": 360.00;
#    the phone number is normalised and the customer "Jane Doe" is created
curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" \
     -d "{\"courtNumber\":5,\"startTime\":\"${DAY}T10:00:00Z\",\"endTime\":\"${DAY}T11:00:00Z\",
          \"gameType\":\"DOUBLES\",\"phoneNumber\":\"+421 905 123 456\",\"customerName\":\"Jane Doe\"}" \
     $BASE/api/reservations | jq .

# 6. overlapping interval on the same court -> 400 problem detail
curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" \
     -d "{\"courtNumber\":5,\"startTime\":\"${DAY}T10:30:00Z\",\"endTime\":\"${DAY}T11:30:00Z\",
          \"gameType\":\"SINGLES\",\"phoneNumber\":\"+421905123456\",\"customerName\":\"Jane Doe\"}" \
     $BASE/api/reservations | jq .

# 7. reservations of court 5 (ordered by creation) and of a phone number, future ones only
curl -s -H "$AUTH" "$BASE/api/reservations?courtNumber=5" | jq .
curl -s -H "$AUTH" "$BASE/api/reservations?phoneNumber=%2B421905123456&futureOnly=true" | jq .

# 8. who am I; without a token -> 401
curl -s -H "$AUTH" $BASE/api/users/me | jq .
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/courts

# 8b. a USER account may read and reserve, but an ADMIN endpoint answers 403
curl -s -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"phoneNumber":"+421905999999","name":"Bob","password":"bob-secret","role":"USER"}' $BASE/api/users | jq .id
USER_AUTH="Authorization: $(curl -s -i -u +421905999999:bob-secret -X POST $BASE/api/auth/login | grep -i '^authorization:' | cut -d' ' -f2- | tr -d '\r')"
curl -s -o /dev/null -w '%{http_code}\n' -H "$USER_AUTH" $BASE/api/courts                 # 200
curl -s -o /dev/null -w '%{http_code}\n' -H "$USER_AUTH" -X DELETE $BASE/api/courts/5     # 403

# 9. refresh the token pair (new Authorization header + body)
REFRESH=$(curl -s -u +420000000000:admin -X POST $BASE/api/auth/login | jq -r .refreshToken)
curl -s -X POST -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}" $BASE/api/auth/refresh | jq .
```

Example of step 5 and step 6 responses:

```json
{"id":1,"courtNumber":5,"courtName":"Centre court","startTime":"2026-09-10T10:00:00Z","endTime":"2026-09-10T11:00:00Z",
 "gameType":"DOUBLES","price":360.00,"customer":{"phoneNumber":"+421905123456","name":"Jane Doe"},"createdAt":"2026-09-03T20:08:18.289033Z"}

{"type":"about:blank","title":"Validation failed","status":400,
 "detail":"Reservation overlaps with an existing reservation on court 5","instance":"/api/reservations"}
```

## Project layout

`sk.knizat.tennisclub` → `config` (properties, security, OpenAPI, initialisers) · `controller` (REST, DTOs only,
`GlobalExceptionHandler`) · `dto` (records with Bean Validation) · `mapper` (manual entity↔DTO) · `service` /
`service.impl` (business rules, `@Transactional`) · `dao` / `dao.impl` (`EntityManager` + JPQL) · `entity`
(`BaseEntity` soft delete + audit) · `exception` · `security` (JWT, user details, 401/403 handlers).
The layering (controller → service → dao, no entities in controllers, `@Transactional` only in services,
`EntityManager` only in DAOs) is verified by `ArchitectureTest`. Continuous integration runs `./mvnw verify` on
every push and pull request ([.github/workflows/ci.yml](.github/workflows/ci.yml)) and uploads the JaCoCo report.
