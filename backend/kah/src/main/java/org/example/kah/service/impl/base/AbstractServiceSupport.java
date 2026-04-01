package org.example.kah.service.impl.base;

import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;

/**
 * service 实现层公共基类。
 * 负责承载断言、分页归一、字符串清洗等所有实现类共用的基础能力。
 */
public abstract class AbstractServiceSupport {

    /**
     * 使用默认错误码断言条件是否成立。
     *
     * @param condition 断言条件
     * @param message 失败时的异常消息
     */
    protected void require(boolean condition, String message) {
        require(condition, ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 使用指定错误码断言条件是否成立。
     *
     * @param condition 断言条件
     * @param code 业务错误码
     * @param message 失败时的异常消息
     */
    protected void require(boolean condition, int code, String message) {
        if (!condition) {
            throw new BusinessException(code, message);
        }
    }

    /**
     * 去除字符串首尾空白。
     *
     * @param value 原始字符串
     * @return 处理后的字符串
     */
    protected String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 将页码归一到有效范围。
     *
     * @param page 原始页码
     * @return 最小为 1 的页码
     */
    protected int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 将分页大小归一到指定上限。
     *
     * @param size 原始分页大小
     * @param maxSize 允许的最大分页大小
     * @return 归一后的分页大小
     */
    protected int normalizeSize(int size, int maxSize) {
        return Math.min(Math.max(size, 1), maxSize);
    }
}
