# TODO.md

Working rule: EACH PHASE IS A SEPARATE RUN. Do not merge multiple phases into one response.
After finishing a phase, stop, report progress, and wait for confirmation before moving to
the next one. If a phase is too large (you predict running out of tokens mid-way), split it
into smaller sub-tasks yourself, but NEVER stop in the middle of a method/class leaving code
that doesn't compile.

---

## Phase 0: Project Setup (foundation, no business logic yet)

- [x] The Spring Boot project skeleton is already generated manually via start.spring.io
      (Maven, Java 17, Spring Boot 4.0.7, dependencies: Web, Data JPA, MySQL Driver,
      Validation, Lombok) and placed in this folder. DO NOT regenerate or overwrite it —
      just verify it builds (`./mvnw clean install`).
- [x] Configure `application.yml` with 2 profiles: `local` (real MySQL) and `test`
      (Testcontainers)
- [x] Set up the package structure per AGENTS.md section 5
- [x] Set up the standard `ApiResponse<T>` wrapper for all responses
- [x] Set up `GlobalExceptionHandler` (`@RestControllerAdvice`) with base exceptions:
      `ResourceNotFoundException`, `BusinessException`, `ValidationException`
- [x] Run `schema.sql` against local MySQL, confirm tables are created correctly
- [x] Set up Flyway to version the schema — copy schema.sql into migration V1
- [x] Write the README "How to run locally" section (docker-compose for MySQL if needed)

**Expected output:** the project runs via `./mvnw spring-boot:run`, connects to MySQL
successfully. No APIs needed yet.

---

## Phase 1: Entity + Repository Layer

- [x] Create 8 entities matching the 8 tables in schema.sql (map column names exactly
      via `@Column`)
- [x] Create a Repository interface for each entity (`extends JpaRepository`)
- [x] Implement `TicketCategoryRepository.reserveTickets()` — native/JPQL update per
      AGENTS.md section 2.1
- [x] Implement `VoucherRepository.findByCodeForUpdate()` — pessimistic lock per
      AGENTS.md section 2.2
- [x] Light unit test: load context, confirm entity mapping has no errors (`@DataJpaTest`)

**Expected output:** all entities + repositories compile, context-load test passes.

---

## Phase 2: Concert & TicketCategory API (Operation side — publish first)

Build the operation side first since the customer side depends on already-published data.

- [ ] `POST /api/operation/concerts` — create a concert (default status DRAFT)
- [ ] `PATCH /api/operation/concerts/{id}/publish` — transition DRAFT → PUBLISHED
- [ ] `POST /api/operation/concerts/{id}/ticket-categories` — add a ticket category
      to a concert
- [ ] `GET /api/operation/concerts` — list all (including DRAFT) for operator view
- [ ] Unit tests for ConcertService, TicketCategoryService (mock repository)
- [ ] Add Swagger annotations to the above endpoints

**Expected output:** an operator can create a concert + ticket category via API,
verifiable through Postman/Swagger UI.

---

## Phase 3: Voucher Seeding (no full CRUD needed, per locked scope)

- [ ] Write Flyway migration `V2__seed_vouchers.sql` — seed sample data (3-5 vouchers
      of different types: PERCENTAGE, FIXED_AMOUNT, with varying max_usage limits for
      easy testing)
- [ ] `GET /api/vouchers/{code}/validate` — API to check if a voucher is still usable
      (NO POST/PUT/DELETE for vouchers — matches the scope limitation already documented)
- [ ] Unit tests for validation logic (still valid, still has quota, hasn't exceeded
      per-user limit)

**Expected output:** vouchers exist in the DB via seeding, the validate API works.

---

## Phase 4: Customer Booking Flow — CORE LOGIC (most important phase, take it slow)

Break this into small steps, do not write the entire BookingService in one pass:

- [ ] 4.1: `POST /api/bookings` — validate input (concert is PUBLISHED, still within
      sale_start_at/sale_end_at)
- [ ] 4.2: Implement idempotency check (query first by idempotencyKey)
- [ ] 4.3: Implement atomic reserve for each booking_item (sort by category_id before
      looping, per AGENTS.md section 2.3)
- [ ] 4.4: Implement voucher application (if any) — use pessimistic lock + redemption check
- [ ] 4.5: Implement creating the booking + booking_items in a single transaction, set
      expires_at (suggested: 10 minutes from reserved_at)
- [ ] 4.6: `GET /api/bookings/{bookingCode}` — view booking detail + status
- [ ] 4.7: `GET /api/bookings?userId=` — a user's booking history (uses idx_user_history)

**Expected output:** the end-to-end booking flow works via Postman, a single successful
request can be manually verified.

---

## Phase 5: Concurrency Integration Tests (mandatory — this is what the assessment

grades most heavily)

- [ ] 5.1: Set up Testcontainers MySQL for integration testing
- [ ] 5.2: "Oversell" test — fire N threads (N > quantity_total) booking the same
      ticket_category concurrently, assert total sold NEVER exceeds quantity_total
- [ ] 5.3: "Duplicate idempotency" test — call the booking-creation API twice with the
      same idempotencyKey, assert exactly 1 row exists in bookings
- [ ] 5.4: "Voucher abuse" test — one user attempts to exceed max_usage_per_user,
      assert it's rejected past the limit
- [ ] 5.5: "Voucher system-wide exhaustion" test — multiple users race for a voucher
      with a small max_usage, assert max_usage is never exceeded

**Expected output:** the above 4 test cases pass — this is the direct evidence of solving
3 of the 4 core problems the assessment states (oversell, duplicate, voucher abuse).

---

## Phase 6: Booking Lifecycle — Expiry Cronjob + Revert

- [ ] 6.1: Write a `@Scheduled` job that scans for expired bookings (atomic update,
      NOT SELECT-then-UPDATE, per AGENTS.md section 4)
- [ ] 6.2: Implement the full revert logic (3 steps in one transaction, per AGENTS.md
      section 2.4)
- [ ] 6.3: `PATCH /api/bookings/{bookingCode}/cancel` — user self-cancels a booking
      (triggers the same revert as the cronjob)
- [ ] 6.4: `PATCH /api/bookings/{bookingCode}/confirm-payment` — simulate a payment
      callback (PENDING/AWAITING_PAYMENT → PAID), atomic update to avoid racing with
      the cronjob
- [ ] 6.5: Integration test: cronjob and confirm-payment racing at the same moment,
      assert no double-processing occurs

**Expected output:** the full 5-state booking lifecycle works correctly, and the race
between the cronjob and payment confirmation is handled.

---

## Phase 7: Operation Dashboard APIs (remaining pieces)

- [ ] `GET /api/operation/bookings?status=` — monitor bookings by status
      (uses idx_status_created)
- [ ] `PATCH /api/operation/bookings/{id}/status` — operator manually changes status
      (logs to operation_logs)
- [ ] `PATCH /api/operation/bookings/{id}/mark-suspicious` — flag a booking as suspicious
- [ ] `GET /api/operation/bookings/{id}/logs` — view the audit trail for a booking
- [ ] Unit tests for OperationService

**Expected output:** the full set of operation-side APIs matching the required scope
("Monitor bookings, Handle failed/suspicious, Update status manually").

---

## Phase 8: Documentation & Finalize (mandatory — one of the 3 required deliverables)

- [ ] Set up Swagger/OpenAPI (springdoc-openapi), verify every endpoint has a full
      description
- [ ] Export the Postman collection, re-test the entire collection end-to-end against
      the local setup (don't just export without verifying)
- [ ] Finalize `README.md` — architecture, local setup & run instructions, coding
      convention
- [ ] Write `ASSUMPTIONS.md` — clearly list what's done / not done / system limitations
      (matching the example format given in the assessment)
- [ ] Do one full code review pass — remove dead code, remove leftover TODO comments
- [ ] Package: source code + docs into a single submission folder

**Expected output:** ready to submit.

---

## Notes for the Agent

- If context feels close to running out during Phase 4 or Phase 5, STOP immediately
  after completing one full sub-task (never stop mid-method), report progress, and let
  a new session continue from there
- Phase 5 must NOT be skipped — it's the direct evidence for the part of the assessment
  graded most heavily. Better to fall behind on other phases than to drop this one.
