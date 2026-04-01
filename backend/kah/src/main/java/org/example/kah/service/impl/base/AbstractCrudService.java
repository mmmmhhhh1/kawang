package org.example.kah.service.impl.base;

import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;

/**
 * CRUD 型 service 实现层抽象基类。
 * 子类只需提供主键查询与实体名称，即可复用“实体必须存在”的统一校验逻辑。
 *
 * @param <E> 实体类型
 * @param <ID> 主键类型
 */
public abstract class AbstractCrudService<E, ID> extends AbstractServiceSupport {

    /**
     * 按主键查询实体。
     *
     * @param id 主键
     * @return 查询到的实体，可为空
     */
    protected abstract E findEntityById(ID id);

    /**
     * 返回实体中文名称，用于拼接错误提示。
     *
     * @return 实体名称
     */
    protected abstract String entityLabel();

    /**
     * 按主键查询实体并确保存在。
     *
     * @param id 主键
     * @return 查询到的实体
     */
    protected E requireById(ID id) {
        E entity = findEntityById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, entityLabel() + "不存在");
        }
        return entity;
    }
}
