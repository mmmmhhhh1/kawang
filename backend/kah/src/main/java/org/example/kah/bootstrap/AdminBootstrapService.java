package org.example.kah.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.config.ShopSecurityProperties;
import org.example.kah.entity.AccountStatus;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.util.CryptoService;
import org.example.kah.util.MaskingUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动初始化器。
 * 用于确保后台管理员账号存在，并在账号池为空时补充演示账号数据。
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapService implements ApplicationRunner {//这个接口实现对应run方法就可以在项目启动使自动执行一次

    /**
     * 后台管理员 Mapper。
     */
    private final AdminUserMapper adminUserMapper;

    /**
     * 商品 Mapper。
     */
    private final ProductMapper productMapper;

    /**
     * 账号池 Mapper。
     */
    private final ProductAccountMapper productAccountMapper;

    /**
     * 密码编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 安全配置。
     */
    private final ShopSecurityProperties securityProperties;

    /**
     * 敏感字段加密服务。
     */
    private final CryptoService cryptoService;

    /**
     * 启动时执行初始化逻辑。
     * 整个流程放在一个事务内，避免管理员同步和演示数据写入出现部分成功。
     *
     * @param args 启动参数
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        syncAdminUser();
        seedAccountsIfNeeded();
        productMapper.syncStats();
    }

    /**
     * 同步后台管理员账号。
     * 如果数据库中还没有管理员，则创建；如果已存在，则按配置覆盖密码和展示名称。
     */
    private void syncAdminUser() {
        AdminUser existing = adminUserMapper.findByUsername(securityProperties.admin().username());
        if (existing == null) {
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername(securityProperties.admin().username());
            adminUser.setPasswordHash(passwordEncoder.encode(securityProperties.admin().password()));
            adminUser.setStatus(AdminStatus.ACTIVE);
            adminUser.setDisplayName(securityProperties.admin().displayName());
            adminUserMapper.insert(adminUser);
            return;
        }

        existing.setPasswordHash(passwordEncoder.encode(securityProperties.admin().password()));
        existing.setStatus(AdminStatus.ACTIVE);
        existing.setDisplayName(securityProperties.admin().displayName());
        adminUserMapper.updateProfile(existing);
    }

    /**
     * 在账号池为空时补充演示账号。
     * 每个商品默认补三条账号，用于首次启动后的联调和演示。
     */
    private void seedAccountsIfNeeded() {
        if (productAccountMapper.countAll() > 0) {
            return;
        }

        List<ShopProduct> products = productMapper.findAllProducts();
        int counter = 1;
        for (ShopProduct product : products) {
            for (int index = 1; index <= 3; index++) {
                ProductAccount account = new ProductAccount();
                String accountName = product.getSku().toLowerCase() + "-" + counter + "@demo.ai";
                account.setProductId(product.getId());
                account.setAccountNameMasked(MaskingUtils.maskAccount(accountName));
                account.setAccountCiphertext(cryptoService.encrypt(accountName));
                account.setSecretCiphertext(cryptoService.encrypt("Pass@" + counter + "Demo"));
                account.setNoteCiphertext(cryptoService.encrypt("系统演示账号"));
                account.setAccountDigest(cryptoService.digest(product.getId() + ":" + accountName));
                account.setStatus(AccountStatus.AVAILABLE);
                productAccountMapper.insert(account);
                counter++;
            }
        }
    }
}
