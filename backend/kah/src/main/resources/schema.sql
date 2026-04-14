CREATE TABLE IF NOT EXISTS shop_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    vendor VARCHAR(80) NOT NULL,
    plan_name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    available_stock INT NOT NULL DEFAULT 0,
    sold_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_product_sku (sku)
);

CREATE TABLE IF NOT EXISTS member_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_user_username (username)
);

CREATE TABLE IF NOT EXISTS shop_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    sort_order INT NOT NULL DEFAULT 0,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS shop_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    product_id BIGINT NOT NULL,
    product_title_snapshot VARCHAR(160) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    buyer_name VARCHAR(64) NOT NULL,
    buyer_contact VARCHAR(80) NOT NULL,
    lookup_hash VARCHAR(128) NULL,
    buyer_remark VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    closed_reason VARCHAR(120) NULL,
    closed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_order_order_no (order_no),
    KEY idx_shop_order_product_id (product_id),
    KEY idx_shop_order_status (status),
    KEY idx_shop_order_buyer_contact (buyer_contact),
    KEY idx_shop_order_user_id (user_id),
    UNIQUE KEY uk_shop_order_lookup_hash (lookup_hash)
);

CREATE TABLE IF NOT EXISTS product_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    account_name_masked VARCHAR(120) NOT NULL,
    account_ciphertext TEXT NOT NULL,
    secret_ciphertext TEXT NOT NULL,
    note_ciphertext TEXT NULL,
    account_digest VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    resource_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_ACCOUNT',
    card_key_ciphertext TEXT NULL,
    card_key_digest VARCHAR(128) NULL,
    sale_status VARCHAR(16) NOT NULL DEFAULT 'UNSOLD',
    enable_status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    used_status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    assigned_order_id BIGINT NULL,
    assigned_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_account_digest (product_id, account_digest),
    UNIQUE KEY uk_product_account_card_key_digest (product_id, card_key_digest),
    KEY idx_product_account_product_status (product_id, status),
    KEY idx_product_account_card_pool (product_id, resource_type, sale_status, enable_status),
    KEY idx_product_account_hot_sale_pick (product_id, resource_type, sale_status, enable_status, id),
    KEY idx_product_account_assigned_order (assigned_order_id)
);

CREATE TABLE IF NOT EXISTS shop_order_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    masked_account_snapshot VARCHAR(120) NOT NULL,
    card_key_ciphertext_snapshot TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_account (order_id, account_id),
    KEY idx_shop_order_account_order (order_id)
);

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    display_name VARCHAR(80) NOT NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_user_username (username)
);

CREATE TABLE IF NOT EXISTS admin_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_user_permission (admin_user_id, permission_code),
    KEY idx_admin_user_permission_admin (admin_user_id)
);
SET @member_user_has_mail = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND COLUMN_NAME = 'mail'
);
SET @member_user_mail_sql = IF(
    @member_user_has_mail = 0,
    'ALTER TABLE member_user ADD COLUMN mail VARCHAR(120) NULL AFTER username',
    'SELECT 1'
);
PREPARE stmt_add_member_user_mail FROM @member_user_mail_sql;
EXECUTE stmt_add_member_user_mail;
DEALLOCATE PREPARE stmt_add_member_user_mail;

SET @member_user_has_last_seen_at = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND COLUMN_NAME = 'last_seen_at'
);
SET @member_user_last_seen_sql = IF(
    @member_user_has_last_seen_at = 0,
    'ALTER TABLE member_user ADD COLUMN last_seen_at DATETIME NULL AFTER last_login_at',
    'SELECT 1'
);
PREPARE stmt_add_member_user_last_seen FROM @member_user_last_seen_sql;
EXECUTE stmt_add_member_user_last_seen;
DEALLOCATE PREPARE stmt_add_member_user_last_seen;

SET @member_user_has_mail_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND INDEX_NAME = 'uk_member_user_mail'
);
SET @member_user_mail_index_sql = IF(
    @member_user_has_mail_index = 0,
    'ALTER TABLE member_user ADD UNIQUE KEY uk_member_user_mail (mail)',
    'SELECT 1'
);
PREPARE stmt_add_member_user_mail_index FROM @member_user_mail_index_sql;
EXECUTE stmt_add_member_user_mail_index;
DEALLOCATE PREPARE stmt_add_member_user_mail_index;

SET @admin_user_has_is_super_admin = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_user'
      AND COLUMN_NAME = 'is_super_admin'
);
SET @admin_user_is_super_admin_sql = IF(
    @admin_user_has_is_super_admin = 0,
    'ALTER TABLE admin_user ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER display_name',
    'SELECT 1'
);
PREPARE stmt_add_admin_user_is_super_admin FROM @admin_user_is_super_admin_sql;
EXECUTE stmt_add_admin_user_is_super_admin;
DEALLOCATE PREPARE stmt_add_admin_user_is_super_admin;
SET @shop_order_has_user_id = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND COLUMN_NAME = 'user_id'
);
SET @shop_order_user_id_sql = IF(
    @shop_order_has_user_id = 0,
    'ALTER TABLE shop_order ADD COLUMN user_id BIGINT NULL AFTER order_no',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_user_id FROM @shop_order_user_id_sql;
EXECUTE stmt_add_shop_order_user_id;
DEALLOCATE PREPARE stmt_add_shop_order_user_id;

SET @shop_order_has_lookup_hash = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND COLUMN_NAME = 'lookup_hash'
);
SET @shop_order_lookup_hash_sql = IF(
    @shop_order_has_lookup_hash = 0,
    'ALTER TABLE shop_order ADD COLUMN lookup_hash VARCHAR(128) NULL AFTER buyer_contact',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_lookup_hash FROM @shop_order_lookup_hash_sql;
EXECUTE stmt_add_shop_order_lookup_hash;
DEALLOCATE PREPARE stmt_add_shop_order_lookup_hash;

SET @shop_order_has_lookup_hash_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND INDEX_NAME = 'uk_shop_order_lookup_hash'
);
SET @shop_order_lookup_hash_index_sql = IF(
    @shop_order_has_lookup_hash_index = 0,
    'ALTER TABLE shop_order ADD UNIQUE KEY uk_shop_order_lookup_hash (lookup_hash)',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_lookup_hash_index FROM @shop_order_lookup_hash_index_sql;
EXECUTE stmt_add_shop_order_lookup_hash_index;
DEALLOCATE PREPARE stmt_add_shop_order_lookup_hash_index;

SET @product_account_has_resource_type = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'resource_type'
);
SET @product_account_resource_type_sql = IF(
    @product_account_has_resource_type = 0,
    'ALTER TABLE product_account ADD COLUMN resource_type VARCHAR(32) NOT NULL DEFAULT ''LEGACY_ACCOUNT'' AFTER status',
    'SELECT 1'
);
PREPARE stmt_add_product_account_resource_type FROM @product_account_resource_type_sql;
EXECUTE stmt_add_product_account_resource_type;
DEALLOCATE PREPARE stmt_add_product_account_resource_type;

SET @product_account_has_card_key_ciphertext = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'card_key_ciphertext'
);
SET @product_account_card_key_ciphertext_sql = IF(
    @product_account_has_card_key_ciphertext = 0,
    'ALTER TABLE product_account ADD COLUMN card_key_ciphertext TEXT NULL AFTER resource_type',
    'SELECT 1'
);
PREPARE stmt_add_product_account_card_key_ciphertext FROM @product_account_card_key_ciphertext_sql;
EXECUTE stmt_add_product_account_card_key_ciphertext;
DEALLOCATE PREPARE stmt_add_product_account_card_key_ciphertext;

SET @product_account_has_card_key_digest = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'card_key_digest'
);
SET @product_account_card_key_digest_sql = IF(
    @product_account_has_card_key_digest = 0,
    'ALTER TABLE product_account ADD COLUMN card_key_digest VARCHAR(128) NULL AFTER card_key_ciphertext',
    'SELECT 1'
);
PREPARE stmt_add_product_account_card_key_digest FROM @product_account_card_key_digest_sql;
EXECUTE stmt_add_product_account_card_key_digest;
DEALLOCATE PREPARE stmt_add_product_account_card_key_digest;

SET @product_account_has_sale_status = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'sale_status'
);
SET @product_account_sale_status_sql = IF(
    @product_account_has_sale_status = 0,
    'ALTER TABLE product_account ADD COLUMN sale_status VARCHAR(16) NOT NULL DEFAULT ''UNSOLD'' AFTER card_key_digest',
    'SELECT 1'
);
PREPARE stmt_add_product_account_sale_status FROM @product_account_sale_status_sql;
EXECUTE stmt_add_product_account_sale_status;
DEALLOCATE PREPARE stmt_add_product_account_sale_status;

SET @product_account_has_enable_status = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'enable_status'
);
SET @product_account_enable_status_sql = IF(
    @product_account_has_enable_status = 0,
    'ALTER TABLE product_account ADD COLUMN enable_status VARCHAR(16) NOT NULL DEFAULT ''DISABLED'' AFTER sale_status',
    'SELECT 1'
);
PREPARE stmt_add_product_account_enable_status FROM @product_account_enable_status_sql;
EXECUTE stmt_add_product_account_enable_status;
DEALLOCATE PREPARE stmt_add_product_account_enable_status;

SET @product_account_has_used_status = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND COLUMN_NAME = 'used_status'
);
SET @product_account_used_status_sql = IF(
    @product_account_has_used_status = 0,
    'ALTER TABLE product_account ADD COLUMN used_status VARCHAR(16) NOT NULL DEFAULT ''UNUSED'' AFTER enable_status',
    'SELECT 1'
);
PREPARE stmt_add_product_account_used_status FROM @product_account_used_status_sql;
EXECUTE stmt_add_product_account_used_status;
DEALLOCATE PREPARE stmt_add_product_account_used_status;

SET @product_account_has_card_key_digest_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND INDEX_NAME = 'uk_product_account_card_key_digest'
);
SET @product_account_card_key_digest_index_sql = IF(
    @product_account_has_card_key_digest_index = 0,
    'ALTER TABLE product_account ADD UNIQUE KEY uk_product_account_card_key_digest (product_id, card_key_digest)',
    'SELECT 1'
);
PREPARE stmt_add_product_account_card_key_digest_index FROM @product_account_card_key_digest_index_sql;
EXECUTE stmt_add_product_account_card_key_digest_index;
DEALLOCATE PREPARE stmt_add_product_account_card_key_digest_index;

SET @product_account_has_card_pool_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND INDEX_NAME = 'idx_product_account_card_pool'
);
SET @product_account_card_pool_index_sql = IF(
    @product_account_has_card_pool_index = 0,
    'ALTER TABLE product_account ADD KEY idx_product_account_card_pool (product_id, resource_type, sale_status, enable_status)',
    'SELECT 1'
);
PREPARE stmt_add_product_account_card_pool_index FROM @product_account_card_pool_index_sql;
EXECUTE stmt_add_product_account_card_pool_index;
DEALLOCATE PREPARE stmt_add_product_account_card_pool_index;

SET @shop_order_account_has_card_key_snapshot = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order_account'
      AND COLUMN_NAME = 'card_key_ciphertext_snapshot'
);
SET @shop_order_account_card_key_snapshot_sql = IF(
    @shop_order_account_has_card_key_snapshot = 0,
    'ALTER TABLE shop_order_account ADD COLUMN card_key_ciphertext_snapshot TEXT NULL AFTER masked_account_snapshot',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_account_card_key_snapshot FROM @shop_order_account_card_key_snapshot_sql;
EXECUTE stmt_add_shop_order_account_card_key_snapshot;
DEALLOCATE PREPARE stmt_add_shop_order_account_card_key_snapshot;

UPDATE product_account
SET resource_type = 'LEGACY_ACCOUNT',
    sale_status = CASE WHEN assigned_order_id IS NULL THEN 'UNSOLD' ELSE 'SOLD' END,
    enable_status = 'DISABLED',
    used_status = 'UNUSED',
    status = 'DISABLED'
WHERE resource_type IS NULL OR resource_type <> 'CARD_KEY';
CREATE TABLE IF NOT EXISTS member_balance_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_no VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_before DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_balance_flow_biz (biz_type, biz_no, direction),
    KEY idx_member_balance_flow_user_created (user_id, created_at, id)
);

CREATE TABLE IF NOT EXISTS member_recharge_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    screenshot_path VARCHAR(255) NOT NULL,
    payer_remark VARCHAR(255) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    reject_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_recharge_request_no (request_no),
    KEY idx_member_recharge_user_created (user_id, created_at, id),
    KEY idx_member_recharge_status_created (status, created_at, id)
);

CREATE TABLE IF NOT EXISTS payment_qr_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    created_by BIGINT NOT NULL,
    activated_by BIGINT NULL,
    activated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_payment_qr_status_created (status, created_at, id)
);

SET @member_user_has_balance = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND COLUMN_NAME = 'balance'
);
SET @member_user_balance_sql = IF(
    @member_user_has_balance = 0,
    'ALTER TABLE member_user ADD COLUMN balance DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER status',
    'SELECT 1'
);
PREPARE stmt_add_member_user_balance FROM @member_user_balance_sql;
EXECUTE stmt_add_member_user_balance;
DEALLOCATE PREPARE stmt_add_member_user_balance;

SET @shop_order_has_payment_method = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND COLUMN_NAME = 'payment_method'
);
SET @shop_order_payment_method_sql = IF(
    @shop_order_has_payment_method = 0,
    'ALTER TABLE shop_order ADD COLUMN payment_method VARCHAR(16) NOT NULL DEFAULT ''LEGACY'' AFTER total_amount',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_payment_method FROM @shop_order_payment_method_sql;
EXECUTE stmt_add_shop_order_payment_method;
DEALLOCATE PREPARE stmt_add_shop_order_payment_method;

SET @shop_order_has_balance_amount = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND COLUMN_NAME = 'balance_amount'
);
SET @shop_order_balance_amount_sql = IF(
    @shop_order_has_balance_amount = 0,
    'ALTER TABLE shop_order ADD COLUMN balance_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER payment_method',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_balance_amount FROM @shop_order_balance_amount_sql;
EXECUTE stmt_add_shop_order_balance_amount;
DEALLOCATE PREPARE stmt_add_shop_order_balance_amount;

SET @shop_order_has_refunded_at = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND COLUMN_NAME = 'refunded_at'
);
SET @shop_order_refunded_at_sql = IF(
    @shop_order_has_refunded_at = 0,
    'ALTER TABLE shop_order ADD COLUMN refunded_at DATETIME NULL AFTER closed_at',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_refunded_at FROM @shop_order_refunded_at_sql;
EXECUTE stmt_add_shop_order_refunded_at;
DEALLOCATE PREPARE stmt_add_shop_order_refunded_at;

SET @shop_order_has_cursor_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND INDEX_NAME = 'idx_shop_order_created_id'
);
SET @shop_order_cursor_index_sql = IF(
    @shop_order_has_cursor_index = 0,
    'ALTER TABLE shop_order ADD KEY idx_shop_order_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_cursor_index FROM @shop_order_cursor_index_sql;
EXECUTE stmt_add_shop_order_cursor_index;
DEALLOCATE PREPARE stmt_add_shop_order_cursor_index;

SET @shop_order_has_status_cursor_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_order'
      AND INDEX_NAME = 'idx_shop_order_status_product_created'
);
SET @shop_order_status_cursor_index_sql = IF(
    @shop_order_has_status_cursor_index = 0,
    'ALTER TABLE shop_order ADD KEY idx_shop_order_status_product_created (status, product_id, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_order_status_cursor_index FROM @shop_order_status_cursor_index_sql;
EXECUTE stmt_add_shop_order_status_cursor_index;
DEALLOCATE PREPARE stmt_add_shop_order_status_cursor_index;

SET @product_account_has_created_cursor_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND INDEX_NAME = 'idx_product_account_resource_created'
);
SET @product_account_created_cursor_index_sql = IF(
    @product_account_has_created_cursor_index = 0,
    'ALTER TABLE product_account ADD KEY idx_product_account_resource_created (resource_type, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_product_account_created_cursor_index FROM @product_account_created_cursor_index_sql;
EXECUTE stmt_add_product_account_created_cursor_index;
DEALLOCATE PREPARE stmt_add_product_account_created_cursor_index;

SET @product_account_has_sale_enable_used_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND INDEX_NAME = 'idx_product_account_product_sale_enable_used'
);
SET @product_account_sale_enable_used_index_sql = IF(
    @product_account_has_sale_enable_used_index = 0,
    'ALTER TABLE product_account ADD KEY idx_product_account_product_sale_enable_used (product_id, sale_status, enable_status, used_status, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_product_account_sale_enable_used_index FROM @product_account_sale_enable_used_index_sql;
EXECUTE stmt_add_product_account_sale_enable_used_index;
DEALLOCATE PREPARE stmt_add_product_account_sale_enable_used_index;

SET @product_account_has_hot_sale_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_account'
      AND INDEX_NAME = 'idx_product_account_hot_sale_pick'
);
SET @product_account_hot_sale_index_sql = IF(
    @product_account_has_hot_sale_index = 0,
    'ALTER TABLE product_account ADD KEY idx_product_account_hot_sale_pick (product_id, resource_type, sale_status, enable_status, id)',
    'SELECT 1'
);
PREPARE stmt_add_product_account_hot_sale_index FROM @product_account_hot_sale_index_sql;
EXECUTE stmt_add_product_account_hot_sale_index;
DEALLOCATE PREPARE stmt_add_product_account_hot_sale_index;

SET @member_user_has_status_created_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND INDEX_NAME = 'idx_member_user_status_created'
);
SET @member_user_status_created_index_sql = IF(
    @member_user_has_status_created_index = 0,
    'ALTER TABLE member_user ADD KEY idx_member_user_status_created (status, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_member_user_status_created_index FROM @member_user_status_created_index_sql;
EXECUTE stmt_add_member_user_status_created_index;
DEALLOCATE PREPARE stmt_add_member_user_status_created_index;

SET @admin_user_has_created_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'admin_user'
      AND INDEX_NAME = 'idx_admin_user_created'
);
SET @admin_user_created_index_sql = IF(
    @admin_user_has_created_index = 0,
    'ALTER TABLE admin_user ADD KEY idx_admin_user_created (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_admin_user_created_index FROM @admin_user_created_index_sql;
EXECUTE stmt_add_admin_user_created_index;
DEALLOCATE PREPARE stmt_add_admin_user_created_index;
SET @shop_product_has_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_product'
      AND INDEX_NAME = 'idx_shop_product_created_id'
);
SET @shop_product_created_id_index_sql = IF(
    @shop_product_has_created_id_index = 0,
    'ALTER TABLE shop_product ADD KEY idx_shop_product_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_product_created_id_index FROM @shop_product_created_id_index_sql;
EXECUTE stmt_add_shop_product_created_id_index;
DEALLOCATE PREPARE stmt_add_shop_product_created_id_index;

SET @shop_product_has_status_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_product'
      AND INDEX_NAME = 'idx_shop_product_status_created_id'
);
SET @shop_product_status_created_id_index_sql = IF(
    @shop_product_has_status_created_id_index = 0,
    'ALTER TABLE shop_product ADD KEY idx_shop_product_status_created_id (status, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_product_status_created_id_index FROM @shop_product_status_created_id_index_sql;
EXECUTE stmt_add_shop_product_status_created_id_index;
DEALLOCATE PREPARE stmt_add_shop_product_status_created_id_index;

SET @shop_notice_has_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_notice'
      AND INDEX_NAME = 'idx_shop_notice_created_id'
);
SET @shop_notice_created_id_index_sql = IF(
    @shop_notice_has_created_id_index = 0,
    'ALTER TABLE shop_notice ADD KEY idx_shop_notice_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_notice_created_id_index FROM @shop_notice_created_id_index_sql;
EXECUTE stmt_add_shop_notice_created_id_index;
DEALLOCATE PREPARE stmt_add_shop_notice_created_id_index;

SET @shop_notice_has_status_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_notice'
      AND INDEX_NAME = 'idx_shop_notice_status_created_id'
);
SET @shop_notice_status_created_id_index_sql = IF(
    @shop_notice_has_status_created_id_index = 0,
    'ALTER TABLE shop_notice ADD KEY idx_shop_notice_status_created_id (status, created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_shop_notice_status_created_id_index FROM @shop_notice_status_created_id_index_sql;
EXECUTE stmt_add_shop_notice_status_created_id_index;
DEALLOCATE PREPARE stmt_add_shop_notice_status_created_id_index;

SET @member_user_has_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_user'
      AND INDEX_NAME = 'idx_member_user_created_id'
);
SET @member_user_created_id_index_sql = IF(
    @member_user_has_created_id_index = 0,
    'ALTER TABLE member_user ADD KEY idx_member_user_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_member_user_created_id_index FROM @member_user_created_id_index_sql;
EXECUTE stmt_add_member_user_created_id_index;
DEALLOCATE PREPARE stmt_add_member_user_created_id_index;

SET @payment_qr_has_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payment_qr_config'
      AND INDEX_NAME = 'idx_payment_qr_created_id'
);
SET @payment_qr_created_id_index_sql = IF(
    @payment_qr_has_created_id_index = 0,
    'ALTER TABLE payment_qr_config ADD KEY idx_payment_qr_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_payment_qr_created_id_index FROM @payment_qr_created_id_index_sql;
EXECUTE stmt_add_payment_qr_created_id_index;
DEALLOCATE PREPARE stmt_add_payment_qr_created_id_index;

SET @member_recharge_has_created_id_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_recharge_request'
      AND INDEX_NAME = 'idx_member_recharge_created_id'
);
SET @member_recharge_created_id_index_sql = IF(
    @member_recharge_has_created_id_index = 0,
    'ALTER TABLE member_recharge_request ADD KEY idx_member_recharge_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE stmt_add_member_recharge_created_id_index FROM @member_recharge_created_id_index_sql;
EXECUTE stmt_add_member_recharge_created_id_index;
DEALLOCATE PREPARE stmt_add_member_recharge_created_id_index;
