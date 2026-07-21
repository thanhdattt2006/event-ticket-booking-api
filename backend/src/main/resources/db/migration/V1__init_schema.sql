-- ============================================================
-- V1__init_schema.sql
-- Source: schema.sql (root of repo, kept as canonical reference)
-- Note: CREATE DATABASE / USE statements removed — Flyway connects
--       to the target database directly; those statements would fail.
-- Engine: MySQL 8.0+ (InnoDB)
-- Charset: utf8mb4 (supports emoji, Vietnamese characters)
-- ============================================================

-- ============================================================
-- 1. USERS
-- Shared table for Customer & Operator, role-based access control
-- ============================================================
CREATE TABLE users (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            ENUM('CUSTOMER', 'OPERATOR', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;


-- ============================================================
-- 2. CONCERTS
-- Root event entity; every ticket_category and booking revolves around this
-- ============================================================
CREATE TABLE concerts (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    venue           VARCHAR(255) NOT NULL,
    event_date      DATETIME NOT NULL,
    sale_start_at   DATETIME NOT NULL,
    sale_end_at     DATETIME NOT NULL,
    status          ENUM('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'DRAFT',
    created_by      BIGINT UNSIGNED NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_concerts_created_by FOREIGN KEY (created_by) REFERENCES users(id),

    -- query "list of concerts currently on sale" on the home page
    INDEX idx_concerts_status_sale (status, sale_start_at, sale_end_at)
) ENGINE=InnoDB;


-- ============================================================
-- 3. TICKET_CATEGORIES (Inventory Core)
-- NOT using version/optimistic lock here.
-- Ticket deduction MUST go through Atomic UPDATE (see AGENTS.md §2.1):
--   UPDATE ticket_categories
--   SET quantity_sold = quantity_sold + :qty
--   WHERE id = :id AND quantity_total - quantity_sold >= :qty
-- InnoDB row-level lock automatically serializes updates to the same row;
-- 0 affected rows => sold out, return error immediately, no retry.
-- ============================================================
CREATE TABLE ticket_categories (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    concert_id      BIGINT UNSIGNED NOT NULL,
    name            VARCHAR(100) NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    quantity_total  INT UNSIGNED NOT NULL,
    quantity_sold   INT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_ticket_categories_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),

    -- DB-level guard against overselling (defense in depth)
    CONSTRAINT chk_quantity_sold CHECK (quantity_sold <= quantity_total),

    INDEX idx_ticket_categories_concert (concert_id)
) ENGINE=InnoDB;


-- ============================================================
-- 4. VOUCHERS
-- used_count decremented via Atomic UPDATE similar to ticket_categories,
-- BUT max_usage_per_user requires a cross-table check with voucher_redemptions
-- => MUST use SELECT ... FOR UPDATE inside a transaction (pessimistic lock)
--    because a plain atomic update alone cannot handle 2-table conditions.
-- ============================================================
CREATE TABLE vouchers (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(50) NOT NULL,
    concert_id          BIGINT UNSIGNED NULL COMMENT 'NULL = applies system-wide, not tied to a specific concert',
    discount_type       ENUM('PERCENTAGE', 'FIXED_AMOUNT') NOT NULL,
    discount_value      DECIMAL(12,2) NOT NULL,
    max_usage           INT UNSIGNED NOT NULL,
    used_count          INT UNSIGNED NOT NULL DEFAULT 0,
    max_usage_per_user  INT UNSIGNED NOT NULL DEFAULT 1,
    valid_from          DATETIME NOT NULL,
    valid_until         DATETIME NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_vouchers_code (code),
    CONSTRAINT fk_vouchers_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT chk_used_count CHECK (used_count <= max_usage),

    INDEX idx_vouchers_valid_range (valid_from, valid_until)
) ENGINE=InnoDB;


-- ============================================================
-- 5. BOOKINGS (Transaction Core)
-- idempotency_key prevents duplicates from client retries.
-- expires_at supports the "hold ticket" pattern + expiry cronjob cleanup.
-- ============================================================
CREATE TABLE bookings (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_code        VARCHAR(50) NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    concert_id          BIGINT UNSIGNED NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL,
    status              ENUM('PENDING', 'AWAITING_PAYMENT', 'PAID', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    total_amount        DECIMAL(12,2) NOT NULL,
    discount_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
    voucher_id          BIGINT UNSIGNED NULL COMMENT 'Display only; source of truth is in voucher_redemptions',
    reserved_at         DATETIME NOT NULL,
    expires_at          DATETIME NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_bookings_code (booking_code),
    UNIQUE KEY uq_bookings_idempotency_key (idempotency_key),

    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_bookings_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT fk_bookings_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),

    -- cronjob scanning for expired bookings MUST use this index — without it every
    -- cronjob run is a full table scan
    INDEX idx_cronjob_scan (status, expires_at),

    -- operation dashboard filter by status, sorted by newest
    INDEX idx_status_created (status, created_at),

    -- user booking history
    INDEX idx_user_history (user_id, created_at)
) ENGINE=InnoDB;


-- ============================================================
-- 6. BOOKING_ITEMS
-- Price snapshot at time of purchase — do NOT join current category price
-- ============================================================
CREATE TABLE booking_items (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id          BIGINT UNSIGNED NOT NULL,
    ticket_category_id  BIGINT UNSIGNED NOT NULL,
    quantity            INT UNSIGNED NOT NULL,
    unit_price          DECIMAL(12,2) NOT NULL COMMENT 'Price at time of booking, immutable',
    subtotal            DECIMAL(12,2) NOT NULL,

    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_booking_items_category FOREIGN KEY (ticket_category_id) REFERENCES ticket_categories(id),

    INDEX idx_booking_items_booking (booking_id),
    -- when deducting inventory across multiple categories in one booking, updates MUST be
    -- sorted by ticket_category_id ASC to prevent deadlocks (AGENTS.md §2.3)
    INDEX idx_booking_items_category (ticket_category_id)
) ENGINE=InnoDB;


-- ============================================================
-- 7. VOUCHER_REDEMPTIONS (Abuse Prevention & Revert Handling)
-- Source of truth for who has used which voucher.
-- REVERTED status handles the case where a booking is cancelled/expired
-- => must return quota to the user AND decrement used_count on vouchers
--    within the SAME transaction.
-- ============================================================
CREATE TABLE voucher_redemptions (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    voucher_id      BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    booking_id      BIGINT UNSIGNED NOT NULL,
    status          ENUM('APPLIED', 'REVERTED') NOT NULL DEFAULT 'APPLIED',
    redeemed_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reverted_at     DATETIME NULL,

    CONSTRAINT fk_voucher_redemptions_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT fk_voucher_redemptions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_voucher_redemptions_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),

    -- fast count of APPLIED redemptions for a user+voucher pair (max_usage_per_user check)
    INDEX idx_voucher_redemptions_check (voucher_id, user_id, status)
) ENGINE=InnoDB;


-- ============================================================
-- 8. OPERATION_LOGS
-- Audit trail when an operator manually intervenes on a booking
-- ============================================================
CREATE TABLE operation_logs (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    booking_id      BIGINT UNSIGNED NOT NULL,
    operator_id     BIGINT UNSIGNED NOT NULL,
    action          VARCHAR(100) NOT NULL COMMENT 'e.g. STATUS_CHANGED, MARKED_SUSPICIOUS',
    old_value       VARCHAR(100),
    new_value       VARCHAR(100),
    note            TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_operation_logs_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_operation_logs_operator FOREIGN KEY (operator_id) REFERENCES users(id),

    INDEX idx_operation_logs_booking (booking_id)
) ENGINE=InnoDB;
