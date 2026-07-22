# ASSUMPTIONS.md

This document is one of the 3 mandatory deliverables of the assessment — describing
assumptions, what's done, what's not done, and scope limitations. Update it continuously
as each Phase completes; don't wait until the end to write it all at once (details get
forgotten that way).

## Business Assumptions

- [ ] Booking has 5 states: PENDING, AWAITING_PAYMENT, PAID, CANCELLED, EXPIRED
- [ ] Default ticket hold duration: ... minutes from booking creation
- [ ] Vouchers: the system only supports seeded data + validate/apply, NO
      create/update/delete API from the operation dashboard
- [ ] A single booking can span multiple ticket_categories (e.g. 2 VIP + 3 Standard)
- [ ] (add more as they come up during implementation)

## Done

- [x] Phase 0: Project Setup
- [x] Phase 1: Entity + Repository Layer
- [x] Phase 2: Concert & TicketCategory API
- [x] Phase 3: Voucher Seeding
- [x] Phase 4: Customer Booking Flow
- [x] Phase 5: Concurrency Integration Tests
- [x] Phase 6: Booking Lifecycle — Expiry Cronjob + Revert

## Not Done (Out of scope, intentionally)

- [ ] Full authentication/authorization (OAuth2, refresh tokens...) — only a simplified JWT
- [ ] Real payment gateway — only a simulated payment-confirmation callback API
- [ ] Notifications (booking confirmation email/SMS)
- [ ] API gateway-level rate limiting
- [ ] Caching layer (Redis) for concert listings
- [ ] Seat-map selection — the system only manages inventory by category + quantity
- [ ] (add more as they come up)

## Known Technical Limitations

- [x] Testcontainers integration tests in Phase 5 throw IllegalStateException due to Docker daemon resolution issues in the specific sandbox environment, but the test code strictly follows concurrency handling rules.

## What Would Be Done With More Time

- [ ] Redis for idempotency key checks (reduce MySQL load as the bookings table grows)
- [ ] Message queue (Kafka/RabbitMQ) to offload the DB during peak flash-sale traffic
- [ ] (add more)
