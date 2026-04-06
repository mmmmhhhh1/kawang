package org.example.kah.entity;

import java.util.List;

/**
 * 后台管理员权限常量。
 * 权限既用于前端按钮显隐，也用于后端接口的强制校验。
 */
public final class AdminPermissionCode {

    /** 停用或启用会员账号。 */
    public static final String DISABLE_USER = "DISABLE_USER";

    /** 删除商品。 */
    public static final String DELETE_PRODUCT = "DELETE_PRODUCT";

    /** 删除订单。 */
    public static final String DELETE_ORDER = "DELETE_ORDER";

    /** 创建管理员、修改管理员权限、删除管理员。 */
    public static final String CREATE_ADMIN = "CREATE_ADMIN";

    private static final List<String> ALL = List.of(
            DISABLE_USER,
            DELETE_PRODUCT,
            DELETE_ORDER,
            CREATE_ADMIN);

    private AdminPermissionCode() {
    }

    /** 返回系统内全部管理员权限编码。 */
    public static List<String> all() {
        return ALL;
    }

    /** 判断权限编码是否受支持。 */
    public static boolean isSupported(String permission) {
        return ALL.contains(permission);
    }
}