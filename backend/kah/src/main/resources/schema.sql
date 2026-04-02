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
    assigned_order_id BIGINT NULL,
    assigned_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_account_digest (product_id, account_digest),
    UNIQUE KEY uk_product_account_card_key_digest (product_id, card_key_digest),
    KEY idx_product_account_product_status (product_id, status),
    KEY idx_product_account_card_pool (product_id, resource_type, sale_status, enable_status),
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
    status = 'DISABLED'
WHERE resource_type IS NULL OR resource_type <> 'CARD_KEY';