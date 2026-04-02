package org.example.kah.mapper;

import java.util.Map;

/**
 * 订单动态 SQL 构建器。
 * 用于后台分页查询和游客查单场景。
 */
public class ShopOrderSqlProvider {

    /**
     * 构建后台分页列表 SQL。
     */
    public String buildAdminListSql(Map<String, Object> params) {
        return adminSelect(params) + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}";
    }

    /**
     * 构建后台分页统计 SQL。
     */
    public String buildAdminCountSql(Map<String, Object> params) {
        return "SELECT COUNT(*) " + adminFrom(params);
    }

    /**
     * 构建游客查单 SQL。
     * 新订单要求联系方式加查单密码；旧订单兼容联系方式加订单号。
     */
    public String buildContactQuerySql(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount,
                       buyer_name, buyer_contact, lookup_hash, buyer_remark, status, closed_reason, closed_at, created_at, updated_at
                FROM shop_order
                WHERE buyer_contact = #{buyerContact}
                """);
        if (params.get("lookupHash") != null && !params.get("lookupHash").toString().isBlank()) {
            sql.append(" AND lookup_hash = #{lookupHash}");
        } else if (params.get("orderNo") != null && !params.get("orderNo").toString().isBlank()) {
            sql.append(" AND lookup_hash IS NULL");
            sql.append(" AND order_no = #{orderNo}");
        } else {
            sql.append(" AND 1 = 0");
        }
        sql.append(" ORDER BY id DESC");
        return sql.toString();
    }

    /**
     * 组装后台订单查询 SELECT 子句。
     */
    private String adminSelect(Map<String, Object> params) {
        return "SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,"
                + " buyer_contact, lookup_hash, buyer_remark, status, closed_reason, closed_at, created_at, updated_at "
                + adminFrom(params);
    }

    /**
     * 组装后台订单查询 FROM / WHERE 子句。
     */
    private String adminFrom(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder("FROM shop_order WHERE 1 = 1");
        if (params.get("status") != null && !params.get("status").toString().isBlank()) {
            sql.append(" AND status = #{status}");
        }
        if (params.get("productId") != null) {
            sql.append(" AND product_id = #{productId}");
        }
        if (params.get("keyword") != null && !params.get("keyword").toString().isBlank()) {
            sql.append(" AND (order_no LIKE CONCAT('%', #{keyword}, '%') OR buyer_contact LIKE CONCAT('%', #{keyword}, '%'))");
        }
        return sql.toString();
    }
}
