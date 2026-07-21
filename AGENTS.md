# AGENTS.md

This is the rulebook. Every AI agent (Antigravity, Claude, Copilot...) working in this repo
MUST read this file before writing a single line of code. No inferring, no "deciding for
convenience" — anything that violates this file is wrong and must be fixed.

## 0. Project Context

Backend for a concert ticket booking platform (flash sale scenario). This is a technical
assessment, NOT a production system — priorities are: (1) correct core business logic,
(2) clean code that's easy to explain, (3) DO NOT scope-creep beyond what's locked in TODO.md.

4 core problems must be solved correctly — these are the primary grading criteria:

1. Overselling tickets under high concurrent booking
2. Duplicate bookings caused by client retries
3. Voucher abuse (exceeding max_usage / max_usage_per_user)
4. System stability under traffic spikes (flash sale)

## 1. Mandatory reading before starting any task

Before starting ANY task in TODO.md, the agent must:

1. Re-read README.md to recall the overall architecture
2. Re-read schema.sql to confirm exact table/column names — do NOT invent different names
3. Read the current phase in TODO.md, only work on that phase, no skipping ahead
4. If a conflict is found between TODO.md and schema.sql — STOP, report it, do not
   silently modify the schema

## 2. Concurrency Handling Rules (MANDATORY, non-negotiable)

This is the part AI agents are most likely to hallucinate on — the rules below are fixed:

### 2.1. Ticket inventory deduction (`ticket_categories.quantity_sold`)

MUST use a **native/JPQL query** (`@Modifying @Query` in Spring Data JPA), NOT a regular
entity `save()`. Reason: this needs an atomic UPDATE with a WHERE condition in a single SQL
statement. Entity `save()` reads then writes (2 steps), which introduces a race condition.

```java
@Modifying
@Query("UPDATE TicketCategory t SET t.quantitySold = t.quantitySold + :qty " +
       "WHERE t.id = :id AND (t.quantityTotal - t.quantitySold) >= :qty")
int reserveTickets(@Param("id") Long id, @Param("qty") int qty);
```

After calling this, ALWAYS check the return value (affected row count). If 0 → sold out,
throw `InsufficientTicketException`. Do NOT auto-retry at this layer.

### 2.2. Voucher (`vouchers.used_count` + check against `voucher_redemptions`)

MUST use **JPA pessimistic lock**, because validity depends on checking across 2 tables
within a single transaction:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT v FROM Voucher v WHERE v.code = :code")
Optional<Voucher> findByCodeForUpdate(@Param("code") String code);
```

Within the transaction: lock the voucher row first → count `voucher_redemptions` where
(voucher_id, user_id, status='APPLIED') → only then update used_count if valid.
Do NOT split these steps across multiple separate transactions.

### 2.3. When a booking spans multiple ticket_categories (booking_items)

MUST sort by `ticket_category_id` ascending before looping the update. This prevents
deadlocks between two transactions that book overlapping categories in different orders.
Do not skip this sort step.

### 2.4. Reverting on EXPIRED/CANCELLED

The following 3 actions MUST happen inside the SAME `@Transactional`:

1. Return `quantity_sold` back to ticket_categories
2. Set `voucher_redemptions.status = REVERTED`, `reverted_at = NOW()`
3. Decrement `vouchers.used_count`

Missing any of these 3 steps is a bug, not something to "do later."

## 3. Idempotency

Every booking-creation API (`POST /api/bookings`) MUST accept an `idempotencyKey` from the
client (a UUID the client generates itself). The server checks for existence in
`bookings.idempotency_key` before creating a new record — if it already exists, return the
existing booking with status 200 instead of creating a duplicate.

## 4. Scheduled job for expired bookings

Use a simple `@Scheduled(fixedRate = ...)`, NOT Spring Batch (overkill for this assessment's
scope). The scan query MUST use the `(status, expires_at)` index defined in schema.sql —
verify with `EXPLAIN` if in doubt.

Updating expired status MUST use an atomic UPDATE with a WHERE condition (same pattern as
2.1), NOT a SELECT followed by a separate UPDATE — this avoids a race with a user confirming
payment at the exact moment of expiry.

## 5. Coding Convention

- Package structure by layer: `controller` / `service` / `repository` / `entity` / `dto` / `exception`
- Naming: PascalCase for classes, camelCase for methods/fields, snake_case for DB columns
  (JPA maps this explicitly via `@Column(name = "...")`, do NOT rely on automatic physical
  naming strategy guessing)
- Every money field uses `BigDecimal`, NEVER `double`/`float`
- Every API response is wrapped in a standard `ApiResponse<T>` (success, data, error) —
  defined in Phase 1
- Centralize exception handling via `@RestControllerAdvice`, no scattered try-catch in
  controllers
- Code comments: short, explain "WHY" not "WHAT" (the code already shows what it does).
  Write like a real person coding, not verbose Javadoc on every method.

## 6. Testing

- Unit tests: cover all core service logic (BookingService, VoucherService,
  TicketCategoryService) — mock the repository layer, test business logic in isolation
- Integration tests: use Testcontainers (a real MySQL container), specifically covering:
  - Concurrent booking on the same ticket_category (fire N threads simultaneously, assert
    total sold never exceeds quantity_total)
  - Duplicate idempotency key (call the API twice with the same key, assert only 1 booking
    row is created)
  - Voucher exceeding max_usage_per_user (assert it's correctly rejected)
- 100% coverage is NOT required — prioritize correctness of the 3 concurrency scenarios
  above, since that's the primary thing this assessment grades

## 7. After completing each Phase

1. Run the full test suite, confirm everything passes
2. Update `PROGRESS.md` — mark the phase as done, note any deviation from TODO.md
3. Do NOT auto-proceed to the next phase without confirmation — stop and report

## 8. Absolutely forbidden

- Do NOT add features outside TODO.md scope just because it's "convenient while you're in there"
- Do NOT rename tables/columns from what's defined in schema.sql
- Do NOT use `@Transactional` without understanding the propagation/isolation being applied
- Do NOT put business logic inside Controllers
- Do NOT use `SELECT *`, always select only needed columns
- Do NOT hardcode connection strings — use `application.yml` + profiles (local/test)
