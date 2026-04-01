package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.ProductAccount;

/**
 * 商品账号池 Mapper。
 * 负责账号池记录查询、加锁分配、释放回库以及状态维护。
 */
@Mapper
public interface ProductAccountMapper {

    /**
     * 查询后台全部账号池记录。
     *
     * @return 账号池列表
     */
    @Select("""
            SELECT pa.id, pa.product_id, p.title AS product_title, pa.account_name_masked, pa.account_ciphertext, pa.secret_ciphertext,
                   pa.note_ciphertext, pa.account_digest, pa.status, pa.assigned_order_id, pa.assigned_at, pa.created_at, pa.updated_at
            FROM product_account pa
            LEFT JOIN shop_product p ON p.id = pa.product_id
            ORDER BY pa.id DESC
            """)
    List<ProductAccount> findAll();

    /**
     * 按商品查询账号池记录。
     *
     * @param productId 商品主键
     * @return 对应商品的账号池记录
     */
    @Select("""
            SELECT pa.id, pa.product_id, p.title AS product_title, pa.account_name_masked, pa.account_ciphertext, pa.secret_ciphertext,
                   pa.note_ciphertext, pa.account_digest, pa.status, pa.assigned_order_id, pa.assigned_at, pa.created_at, pa.updated_at
            FROM product_account pa
            LEFT JOIN shop_product p ON p.id = pa.product_id
            WHERE pa.product_id = #{productId}
            ORDER BY pa.id DESC
            """)
    List<ProductAccount> findByProductId(@Param("productId") Long productId);

    /**
     * 按主键查询账号池记录。
     *
     * @param id 账号池主键
     * @return 账号池实体
     */
    @Select("""
            SELECT id, product_id, account_name_masked, account_ciphertext, secret_ciphertext, note_ciphertext, account_digest,
                   status, assigned_order_id, assigned_at, created_at, updated_at
            FROM product_account
            WHERE id = #{id}
            LIMIT 1
            """)
    ProductAccount findById(@Param("id") Long id);

    /**
     * 锁定某个商品下的可用账号。
     * SQL 使用 {@code FOR UPDATE}，确保并发下单时同一批账号不会被重复分配。
     *
     * @param productId 商品主键
     * @param quantity 需要锁定的账号数量
     * @return 已锁定的账号记录
     */
    @Select("""
            SELECT id, product_id, account_name_masked, account_ciphertext, secret_ciphertext, note_ciphertext, account_digest,
                   status, assigned_order_id, assigned_at, created_at, updated_at
            FROM product_account
            WHERE product_id = #{productId}
              AND status = 'AVAILABLE'
            ORDER BY id ASC
            LIMIT #{quantity}
            FOR UPDATE
            """)
    List<ProductAccount> lockAvailableAccounts(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 锁定某个订单已分配的账号。
     * 关闭订单时需要先锁定这些账号，再安全释放回可用池。
     *
     * @param orderId 订单主键
     * @return 被锁定的账号列表
     */
    @Select("""
            SELECT id, product_id, account_name_masked, account_ciphertext, secret_ciphertext, note_ciphertext, account_digest,
                   status, assigned_order_id, assigned_at, created_at, updated_at
            FROM product_account
            WHERE assigned_order_id = #{orderId}
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<ProductAccount> lockByAssignedOrderId(@Param("orderId") Long orderId);

    /**
     * 新增账号池记录。
     *
     * @param account 账号池实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO product_account (
                product_id, account_name_masked, account_ciphertext, secret_ciphertext, note_ciphertext, account_digest, status
            ) VALUES (
                #{productId}, #{accountNameMasked}, #{accountCiphertext}, #{secretCiphertext}, #{noteCiphertext}, #{accountDigest}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductAccount account);

    /**
     * 统计账号池总记录数。
     *
     * @return 总记录数
     */
    @Select("""
            SELECT COUNT(*)
            FROM product_account
            """)
    long countAll();

    /**
     * 将单个账号分配给订单。
     * 这里的更新会写入分配订单和分配时间，形成账号占用关系。
     *
     * @param id 账号主键
     * @param orderId 订单主键
     * @param assignedAt 分配时间
     * @return 影响行数
     */
    @Update("""
            UPDATE product_account
            SET status = 'ASSIGNED',
                assigned_order_id = #{orderId},
                assigned_at = #{assignedAt}
            WHERE id = #{id}
            """)
    int assignToOrder(@Param("id") Long id, @Param("orderId") Long orderId, @Param("assignedAt") LocalDateTime assignedAt);

    /**
     * 释放已占用账号。
     * 订单关闭后会通过该 SQL 把账号恢复为可用状态。
     *
     * @param id 账号主键
     * @return 影响行数
     */
    @Update("""
            UPDATE product_account
            SET status = 'AVAILABLE',
                assigned_order_id = NULL,
                assigned_at = NULL
            WHERE id = #{id}
            """)
    int release(@Param("id") Long id);

    /**
     * 更新账号状态。
     *
     * @param id 账号主键
     * @param status 目标状态
     * @return 影响行数
     */
    @Update("""
            UPDATE product_account
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
