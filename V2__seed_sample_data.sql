-- ============================================================
-- V2__seed_sample_data.sql
-- Seed data mẫu để người chấm bài test API ngay sau khi migrate,
-- không cần tạo tay bất kỳ bước nào trước khi gọi API booking.
-- ============================================================

-- --------------------------------------------------------------
-- USERS
-- password_hash dưới đây là bcrypt của chuỗi "password123"
-- (dùng chung cho mọi user mẫu, chỉ để test, không phải giá trị thật)
-- --------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, role) VALUES
(1, 'admin@geekup.vn',     '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Admin GeekUp',       'ADMIN'),
(2, 'operator@geekup.vn',  '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Operator Nguyen',    'OPERATOR'),
(3, 'customer1@test.vn',   '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Nguyen Van A',       'CUSTOMER'),
(4, 'customer2@test.vn',   '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqmFF9j4V4TxUEyLxvcv3Kh9jvsWM2y', 'Tran Thi B',         'CUSTOMER');

-- --------------------------------------------------------------
-- CONCERTS
-- 1 concert PUBLISHED đang mở bán (dùng để test flow chính)
-- 1 concert DRAFT (test là customer không thấy được)
-- 1 concert PUBLISHED nhưng ticket cực ít (dễ test race condition oversell)
-- --------------------------------------------------------------
INSERT INTO concerts (id, name, description, venue, event_date, sale_start_at, sale_end_at, status, created_by) VALUES
(1, 'Sơn Tùng M-TP Live Concert',
    'Đêm nhạc Sơn Tùng M-TP cùng dàn khách mời đặc biệt',
    'SVĐ Quân Khu 7, TP.HCM',
    '2026-09-15 19:00:00', '2026-07-01 00:00:00', '2026-09-10 23:59:59',
    'PUBLISHED', 2),

(2, 'Draft Concert - Chưa Publish',
    'Concert đang soạn, dùng để test operator flow',
    'Nhà hát Hòa Bình, TP.HCM',
    '2026-10-01 19:00:00', '2026-08-01 00:00:00', '2026-09-25 23:59:59',
    'DRAFT', 2),

(3, 'Flash Sale Test - Vé Cực Ít',
    'Concert dùng riêng để test concurrency (chỉ 5 vé VIP)',
    'Nhà hát Lớn Hà Nội',
    '2026-08-20 19:00:00', '2026-07-01 00:00:00', '2026-08-18 23:59:59',
    'PUBLISHED', 2);

-- --------------------------------------------------------------
-- TICKET_CATEGORIES
-- --------------------------------------------------------------
INSERT INTO ticket_categories (id, concert_id, name, price, quantity_total, quantity_sold) VALUES
-- Concert 1: đủ loại vé, số lượng thoải mái để test flow bình thường
(1, 1, 'VIP',        2500000.00, 200, 0),
(2, 1, 'Standard',   1200000.00, 500, 0),
(3, 1, 'Economy',     600000.00, 800, 0),

-- Concert 2: DRAFT nên category cũng chưa public theo
(4, 2, 'VIP',        3000000.00, 100, 0),

-- Concert 3: cố tình ít vé để bắn concurrent test dễ thấy oversell nếu có bug
(5, 3, 'VIP',        1500000.00,   5, 0);

-- --------------------------------------------------------------
-- VOUCHERS
-- Đa dạng loại để test đủ nhánh logic: percentage, fixed, hết hạn,
-- hết lượt, giới hạn theo user
-- --------------------------------------------------------------
INSERT INTO vouchers (id, code, concert_id, discount_type, discount_value, max_usage, used_count, max_usage_per_user, valid_from, valid_until) VALUES
-- Voucher đang hoạt động bình thường, giảm 10%
(1, 'WELCOME10', NULL, 'PERCENTAGE', 10.00, 1000, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Voucher giảm cố định, riêng cho concert 1
(2, 'SONTUNG50K', 1, 'FIXED_AMOUNT', 50000.00, 500, 0, 1, '2026-07-01 00:00:00', '2026-09-10 23:59:59'),

-- Voucher chỉ còn đúng 3 lượt toàn hệ thống, dùng để test tranh chấp voucher
(3, 'LIMITED3', NULL, 'PERCENTAGE', 20.00, 3, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Voucher đã hết hạn, dùng để test validate logic từ chối đúng
(4, 'EXPIRED_TEST', NULL, 'PERCENTAGE', 15.00, 100, 0, 1, '2026-01-01 00:00:00', '2026-06-30 23:59:59'),

-- Voucher đã hết lượt sẵn (used_count = max_usage), test hệ thống từ chối đúng
(5, 'SOLDOUT_TEST', NULL, 'FIXED_AMOUNT', 30000.00, 5, 5, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59'),

-- Voucher cho phép 1 user dùng nhiều lần (test max_usage_per_user > 1)
(6, 'MULTI_USE', NULL, 'PERCENTAGE', 5.00, 1000, 0, 3, '2026-07-01 00:00:00', '2026-12-31 23:59:59');

-- --------------------------------------------------------------
-- Reset AUTO_INCREMENT để tránh trùng ID khi app tạo record mới
-- qua API, do các bảng trên đang insert với ID cứng (1-6).
-- Set mốc 100 để có khoảng trống an toàn, tránh đụng ID seed.
-- --------------------------------------------------------------
ALTER TABLE users AUTO_INCREMENT = 100;
ALTER TABLE concerts AUTO_INCREMENT = 100;
ALTER TABLE ticket_categories AUTO_INCREMENT = 100;
ALTER TABLE vouchers AUTO_INCREMENT = 100;
