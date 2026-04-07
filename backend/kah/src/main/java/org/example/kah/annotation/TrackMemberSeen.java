package org.example.kah.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要统计会员最近活跃时间的业务方法。
 * 切面会在方法成功返回后，将会员活跃时间写入 Redis。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackMemberSeen {
}