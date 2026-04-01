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
    KEY idx_shop_order_user_id (user_id)
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
    assigned_order_id BIGINT NULL,
    assigned_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_account_digest (product_id, account_digest),
    KEY idx_product_account_product_status (product_id, status),
    KEY idx_product_account_assigned_order (assigned_order_id)
);

CREATE TABLE IF NOT EXISTS shop_order_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    masked_account_snapshot VARCHAR(120) NOT NULL,
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
