# Tennis Club Reservations

REST API for managing a tennis club: surface types, courts and court reservations with price calculation,
plus JWT-secured user management. Built with Spring Boot 3.5, Hibernate 6 (plain JPA, no Spring Data),
H2 in-memory database and Liquibase migrations. Full design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Requirements

- JDK 21 (Maven is bundled via the wrapper `./mvnw`)

## Build, test, run

```bash
./mvnw verify                                             # compile, tests, ArchUnit, JaCoCo (90 %) check
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run with H2 console and initial data enabled
```

Without the `dev` profile the application requires `APP_JWT_SECRET` to be set (fail-fast, see below).

## Configuration

All `app.*` properties can be overridden with environment variables.

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
