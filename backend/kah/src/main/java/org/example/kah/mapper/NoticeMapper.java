package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.ShopNotice;

@Mapper
public interface NoticeMapper {

    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            WHERE status = 'PUBLISHED'
            ORDER BY sort_order ASC, published_at DESC, id DESC
            """)
    List<ShopNotice> findPublished();

    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            ORDER BY sort_order ASC, published_at DESC, id DESC
            """)
    List<ShopNotice> findAll();

    @Select({
            "<script>",
            "SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at",
            "FROM shop_notice",
            "<where>",
            "  <if test='status != null and status != \"\"'> AND status = #{status} </if>",
            "  <if test='keyword != null and keyword != \"\"'> AND (title LIKE CONCAT(#{keyword}, '%') OR summary LIKE CONCAT(#{keyword}, '%')) </if>",
            "  <if test='cursorCreatedAt != null and cursorId != null'>",
            "    AND (created_at &lt; #{cursorCreatedAt} OR (created_at = #{cursorCreatedAt} AND id &lt; #{cursorId}))",
            "  </if>",
            "</where>",
            "ORDER BY created_at DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ShopNotice> findAdminCursorPage(Map<String, Object> params);

    @Select("""
            SELECT id, title, summary, content, status, sort_order, published_at, created_at, updated_at
            FROM shop_notice
            WHERE id = #{id}
            LIMIT 1
            """)
    ShopNotice findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO shop_notice (title, summary, content, status, sort_order, published_at)
            VALUES (#{title}, #{summary}, #{content}, #{status}, #{sortOrder}, #{publishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopNotice notice);

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

    @Update("""
            UPDATE shop_notice
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}