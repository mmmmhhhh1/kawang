package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 公告实体。
 * 对应表 {@code shop_notice}，用于保存前台公共说明和后台公告内容。
 */
@Data
public class ShopNotice {

    /** 公告主键。 */
    private Long id;

    /** 公告标题。 */
    private String title;

    /** 公告摘要。 */
    private String summary;

    /** 公告正文。 */
    private String content;

    /** 公告状态，例如 PUBLISHED / HIDDEN。 */
    private String status;

    /** 排序值，值越小越靠前。 */
    private Integer sortOrder;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
