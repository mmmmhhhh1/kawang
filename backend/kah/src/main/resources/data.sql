INSERT INTO shop_product (sku, title, vendor, plan_name, description, price, status, sort_order)
VALUES
    ('CHATGPT-PLUS-MONTH', 'ChatGPT Plus 会员', 'OpenAI', '月度版', '适合高频对话、文案写作、代码协作与多模型探索。', 129.00, 'ACTIVE', 10),
    ('CLAUDE-PRO-MONTH', 'Claude Pro 会员', 'Anthropic', '月度版', '擅长长文本理解、知识整合、文档拆解与复杂推理场景。', 149.00, 'ACTIVE', 20),
    ('MIDJOURNEY-STANDARD', 'Midjourney Standard', 'Midjourney', '月度版', '适合 AI 绘图、海报设计、视觉灵感板与内容创作。', 168.00, 'ACTIVE', 30),
    ('GEMINI-ADVANCED', 'Gemini Advanced', 'Google', '月度版', '适合办公协作、多模态搜索与 Google 生态辅助场景。', 139.00, 'ACTIVE', 40)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    vendor = VALUES(vendor),
    plan_name = VALUES(plan_name),
    description = VALUES(description),
    price = VALUES(price),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

INSERT INTO shop_notice (title, summary, content, status, sort_order, published_at)
SELECT '全站升级完成', '商城页面、查单流程和用户中心已升级为新版本。', '本次升级完成了 Holy Card 风格前台重构、游客查单、会员登录注册和后台公告管理，欢迎直接体验完整链路。', 'PUBLISHED', 10, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM shop_notice
    WHERE title = '全站升级完成'
);

INSERT INTO shop_notice (title, summary, content, status, sort_order, published_at)
SELECT '账号安全说明', '下单后系统仅在后台分配账号，前台不会返回账号明文。', '为了降低敏感信息泄露风险，账号池数据仅在后端以加密形式保存，前台查单页和我的订单页仅展示订单状态、金额和商品信息。', 'PUBLISHED', 20, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM shop_notice
    WHERE title = '账号安全说明'
);

INSERT INTO shop_notice (title, summary, content, status, sort_order, published_at)
SELECT '模拟下单提示', '当前项目为演示版，下单会真实落库并扣库存，但不接入真实支付。', '你仍然可以完整体验商品展示、下单、联系方式查单、登录后查看我的订单以及后台订单和账号池联动管理。', 'PUBLISHED', 30, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM shop_notice
    WHERE title = '模拟下单提示'
);
