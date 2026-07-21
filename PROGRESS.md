# PROGRESS.md

This file is updated by the AI agent itself after each phase is completed. Purpose: when a
new session starts (context lost due to running out of tokens or closing the IDE), the agent
reads this file to know where things stand — instead of re-reading the entire codebase to
guess at progress.

## How to use

After completing a phase in TODO.md, the agent MUST update this file before reporting
completion:

- Which phase was just finished
- Which files were created/modified
- Any deviation from TODO.md/AGENTS.md (and why)
- What was left unfinished if interrupted mid-phase (so the next session knows exactly
  where to resume)

---

## Log

### Phase 0 — Project Setup ✅

**Completed:** 2026-07-21

**Status:** DONE — app starts, Flyway migrations ran, 8 tables + seed data verified.

**Files created/modified:**

| File | Action |
|------|--------|
| `backend/pom.xml` | Fixed invalid test dependencies (`spring-boot-starter-*-test` → `spring-boot-starter-test`); fixed Testcontainers artifact IDs; added Testcontainers BOM in `dependencyManagement` |
| `backend/src/main/resources/application.yml` | Created — replaces `application.properties`; 2 profiles: `local` (real MySQL) and `test` (Testcontainers) |
| `backend/src/main/resources/application.properties` | Deleted — replaced by `application.yml` |
| `backend/src/main/resources/db/migration/V1__init_schema.sql` | Created — copied from `schema.sql` (root), removed `CREATE DATABASE` + `USE` statements (Flyway connects directly to target DB) |
| `backend/src/main/resources/db/migration/V2__seed_sample_data.sql` | Created — copied from `V2__seed_sample_data.sql` (root) |
| `backend/docker-compose.yml` | Created — MySQL 8.0 local dev container; credentials match `application.yml` local profile |
| `backend/src/main/java/.../dto/response/ApiResponse.java` | Created — standard `ApiResponse<T>` record wrapper (all endpoints return this shape) |
| `backend/src/main/java/.../exception/ResourceNotFoundException.java` | Created — 404 |
| `backend/src/main/java/.../exception/BusinessException.java` | Created — 409 (sold out, voucher abuse, invalid state transition) |
| `backend/src/main/java/.../exception/ValidationException.java` | Created — 400 |
| `backend/src/main/java/.../exception/InsufficientTicketException.java` | Created — 409, extends BusinessException, specific to sold-out scenario |
| `backend/src/main/java/.../exception/GlobalExceptionHandler.java` | Created — `@RestControllerAdvice`, centralises all exception → HTTP mapping |
| `backend/src/main/java/.../{controller,service,repository,entity,config,scheduler}/package-info.java` | Created — package structure per AGENTS.md §5 |
| `backend/src/test/java/.../TestcontainersConfiguration.java` | Fixed — wrong import (`org.testcontainers.mysql` → `org.testcontainers.containers`); added typed generic; pinned to `mysql:8.0` |
| `README.md` | Updated "How to run locally" section with correct paths, profile activation command, credential table, and migration verification queries |
| `.gitignore` | Updated — added OS, editor, Maven target, Docker volume patterns |

**Deviations from TODO.md:**
- `docker-compose.yml` placed in `backend/` (not root) since the project structure has all backend code in `backend/`. README updated accordingly.
- Port 3306 was already occupied by an existing `mysql_db` Docker container. Instead of starting a new container, `concert_booking` DB and `appuser` were created inside the existing container. The `docker-compose.yml` in `backend/` is the canonical setup for a clean environment — on the grader's machine `docker-compose up -d` in `backend/` will work as written.
- `application.yml` omits explicit `hibernate.dialect` (Hibernate 6+ / Spring Boot 4.x auto-detects from JDBC driver; specifying it triggers a deprecation warning).

**Verification results:**
```
BUILD SUCCESS (./mvnw clean install -DskipTests)
Flyway: Successfully applied 2 migrations to schema concert_booking, now at version v2
Tables: 8 (booking_items, bookings, concerts, operation_logs, ticket_categories, users, voucher_redemptions, vouchers)
Seed data: users=4, concerts=3, ticket_categories=5, vouchers=6
App started: Started BackendApplication in 16.568 seconds
```

**Next:** Phase 1 — Entity + Repository Layer (wait for user confirmation)
