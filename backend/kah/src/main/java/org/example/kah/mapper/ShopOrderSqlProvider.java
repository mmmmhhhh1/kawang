package org.example.kah.mapper;

import java.util.Map;

/**
 * 订单动态 SQL 构建器。
 * 用于封装后台订单分页查询和前台联系方式查单的 SQL 拼装逻辑。
 */
public class ShopOrderSqlProvider {

    /**
     * 构建后台分页列表 SQL。
     * 该 SQL 会根据状态、商品、关键词等条件动态追加筛选，并附带分页参数。
     *
     * @param params 查询参数
     * @return 完整分页查询 SQL
     */
    public String buildAdminListSql(Map<String, Object> params) {
        return adminSelect(params) + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}";
    }

    /**
     * 构建后台分页统计 SQL。
     *
     * @param params 查询参数
     * @return 统计总数 SQL
     */
    public String buildAdminCountSql(Map<String, Object> params) {
        return "SELECT COUNT(*) " + adminFrom(params);
    }

    /**
     * 构建联系方式查单 SQL。
     * 这里固定要求联系方式匹配；当传入订单号时，会进一步缩小查询范围。
     *
     * @param params 查询参数
     * @return 前台查单 SQL
     */
    public String buildContactQuerySql(Map<String, Object> params) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount,
                       buyer_name, buyer_contact, buyer_remark, status, closed_reason, closed_at, created_at, updated_at
                FROM shop_order
                WHERE buyer_contact = #{buyerContact}
                """);
        if (params.get("orderNo") != null && !params.get("orderNo").toString().isBlank()) {
            sql.append(" AND order_no = #{orderNo}");
        }
        sql.append(" ORDER BY id DESC");
        return sql.toString();
    }

    /**
     * 组装后台订单查询的 SELECT 子句。
     *
     * @param params 查询参数
     * @return SELECT + FROM 片段
     */
    private String adminSelect(Map<String, Object> params) {
        return "SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,"
                + " buyer_contact, buyer_remark, status, closed_reason, closed_at, created_at, updated_at "
                + adminFrom(params);
    }

    /**
     * 组装后台订单查询的 FROM / WHERE 子句。
     * 这里是动态 SQL 的核心：不同筛选项只在传参存在时才拼接。
     *
     * @param params 查询参数
     * @return FROM / WHERE 片段
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
