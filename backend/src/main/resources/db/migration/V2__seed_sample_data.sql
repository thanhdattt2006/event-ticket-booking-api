-- ============================================================
-- V2__seed_sample_data.sql
-- Sample seed data for reviewers to test the API immediately after migration,
-- without manually creating any data before calling the booking API.
-- Source: V2__seed_sample_data.sql (root of repo)
-- ============================================================

-- --------------------------------------------------------------
-- USERS
-- password_hash below is bcrypt of the string "password123"
-- (shared across all sample users — test only, not a real value)
-- --------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, role) VALUES
(1, 'admin@geekup.vn',     '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Admin GeekUp',       'ADMIN'),
(2, 'operator@geekup.vn',  '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Operator Nguyen',    'OPERATOR'),
(3, 'customer1@test.vn',   '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Nguyen Van A',       'CUSTOMER'),
(4, 'customer2@test.vn',   '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Tran Thi B',         'CUSTOMER');

-- --------------------------------------------------------------
-- CONCERTS
-- 1 PUBLISHED concert with sale open (main flow testing)
-- 1 DRAFT concert (test that customers cannot see it)
-- 1 PUBLISHED concert with very few tickets (easy race condition / oversell testing)
-- --------------------------------------------------------------
INSERT INTO concerts (id, name, description, venue, event_date, sale_start_at, sale_end_at, status, created_by) VALUES
(1, 'Son Tung M-TP Live Concert',
    'Son Tung M-TP night with special guests',
    'Military Zone 7 Stadium, Ho Chi Minh City',
    '2026-09-15 19:00:00', '2026-07-01 00:00:00', '2026-09-10 23:59:59',
    'PUBLISHED', 2),

(2, 'Draft Concert - Not Published',
    'Concert in draft, used for operator flow testing',
    'Hoa Binh Theatre, Ho Chi Minh City',
    '2026-10-01 19:00:00', '2026-08-01 00:00:00', '2026-09-25 23:59:59',
    'DRAFT', 2),

(3, 'Flash Sale Test - Very Limited Tickets',
    'Concert specifically for concurrency testing (only 5 VIP tickets)',
    'Hanoi Opera House',
    '2026-08-20 19:00:00', '2026-07-01 00:00:00', '2026-08-18 23:59:59',
    'PUBLISHED', 2);

-- --------------------------------------------------------------
-- TICKET_CATEGORIES
-- --------------------------------------------------------------
INSERT INTO ticket_categories (id, concert_id, name, price, quantity_total, quantity_sold) VALUES
-- Concert 1: various ticket types with ample quantity for normal flow testing
(1, 1, 'VIP',        2500000.00, 200, 0),
(2, 1, 'Standard',   1200000.00, 500, 0),
(3, 1, 'Economy',     600000.00, 800, 0),

-- Concert 2: DRAFT — categories are also not public
(4, 2, 'VIP',        3000000.00, 100, 0),

-- Concert 3: intentionally few tickets to make concurrent oversell testing visible
(5, 3, 'VIP',        1500000.00,   5, 0);

-- --------------------------------------------------------------
-- VOUCHERS
-- Various types to test all logic branches: percentage, fixed, expired,
-- exhausted quota, per-user limit
-- --------------------------------------------------------------
INSERT INTO vouchers (id, code, concert_id, discount_type, discount_value, max_usage, used_count, max_usage_per_user, valid_from, valid_until) VALUES
-- Active voucher, 10% discount — system-wide
(1, 'WELCOME10', NULL, 'PERCENTAGE', 10.00, 1000, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Fixed discount, specific to concert 1
(2, 'SONTUNG50K', 1, 'FIXED_AMOUNT', 50000.00, 500, 0, 1, '2026-07-01 00:00:00', '2026-09-10 23:59:59'),

-- Only 3 uses remaining system-wide — tests voucher contention race
(3, 'LIMITED3', NULL, 'PERCENTAGE', 20.00, 3, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Already expired — tests that the system correctly rejects it
(4, 'EXPIRED_TEST', NULL, 'PERCENTAGE', 15.00, 100, 0, 1, '2026-01-01 00:00:00', '2026-06-30 23:59:59'),

-- Already fully exhausted (used_count = max_usage) — tests correct rejection
(5, 'SOLDOUT_TEST', NULL, 'FIXED_AMOUNT', 30000.00, 5, 5, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Allows one user to use it multiple times — tests max_usage_per_user > 1
(6, 'MULTI_USE', NULL, 'PERCENTAGE', 5.00, 1000, 0, 3, '2026-07-01 00:00:00', '2026-12-31 23:59:59');

-- --------------------------------------------------------------
-- Reset AUTO_INCREMENT to avoid ID collision when the app creates
-- new records via API (tables above inserted with hardcoded IDs 1-6).
-- Gap of 100 gives a safe buffer above the seeded IDs.
-- --------------------------------------------------------------
ALTER TABLE users AUTO_INCREMENT = 100;
ALTER TABLE concerts AUTO_INCREMENT = 100;
ALTER TABLE ticket_categories AUTO_INCREMENT = 100;
ALTER TABLE vouchers AUTO_INCREMENT = 100;
