package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.ShopNotice;

/**
 * 公告表 Mapper。
 * 负责前台公告读取和后台公告管理。
 */
@Mapper
public interface NoticeMapper {

    /**
     * 查询前台可展示公告。
     *
     * @return 已发布公告列表
     */
    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            WHERE status = 'PUBLISHED'
            ORDER BY sort_order ASC, published_at DESC, id DESC
            """)
    List<ShopNotice> findPublished();

    /**
     * 查询后台全部公告。
     *
     * @return 全量公告记录
     */
    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            ORDER BY sort_order ASC, published_at DESC, id DESC
            """)
    List<ShopNotice> findAll();

    /**
     * 按主键查询公告。
     *
     * @param id 公告主键
     * @return 公告实体
     */
    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            WHERE id = #{id}
            LIMIT 1
            """)
    ShopNotice findById(@Param("id") Long id);

    /**
     * 新增公告。
     *
     * @param notice 公告实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO shop_notice (title, summary, content, status, sort_order, published_at)
            VALUES (#{title}, #{summary}, #{content}, #{status}, #{sortOrder}, #{publishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopNotice notice);

    /**
     * 更新公告内容。
     *
     * @param notice 公告实体
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_notice
            SET title = #{title},
                summary = #{summary},
                content = #{content},
                status = #{status},
                sort_order = #{sortOrder},
                published_at = #{publishedAt}
            WHERE id = #{id}
            """)
    int update(ShopNotice notice);

    /**
     * 更新公告状态。
     *
     * @param id 公告主键
     * @param status 目标状态
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_notice
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
