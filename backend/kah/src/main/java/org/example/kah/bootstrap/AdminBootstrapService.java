package org.example.kah.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.config.ShopSecurityProperties;
import org.example.kah.entity.AccountStatus;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ResourceType;
import org.example.kah.entity.SaleStatus;
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
 * 用于同步管理员账号，并在没有卡密池数据时补充演示卡密。
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapService implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final ShopSecurityProperties securityProperties;
    private final CryptoService cryptoService;

    /**
     * 启动时执行初始化逻辑。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        syncAdminUser();
        productAccountMapper.normalizeLegacyPool();
        seedCardKeysIfNeeded();
        productMapper.syncStats();
    }

    /**
     * 同步后台管理员账号。
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
     * 在卡密池为空时补充演示卡密。
     */
    private void seedCardKeysIfNeeded() {
        if (productAccountMapper.countByResourceType(ResourceType.CARD_KEY) > 0) {
            return;
        }

        List<ShopProduct> products = productMapper.findAllProducts();
        int counter = 1;
        for (ShopProduct product : products) {
            for (int index = 1; index <= 3; index++) {
                String cardKey = product.getSku() + "-CARD-" + counter + "-" + index;
                ProductAccount account = new ProductAccount();
                account.setProductId(product.getId());
                account.setAccountNameMasked(MaskingUtils.maskAccount(cardKey));
                account.setAccountCiphertext(cryptoService.encrypt(cardKey));
                account.setSecretCiphertext(cryptoService.encrypt(""));
                account.setNoteCiphertext(cryptoService.encrypt("系统演示卡密"));
                account.setAccountDigest(cryptoService.digest(product.getId() + ":" + cardKey));
                account.setStatus(AccountStatus.AVAILABLE);
                account.setResourceType(ResourceType.CARD_KEY);
                account.setCardKeyCiphertext(cryptoService.encrypt(cardKey));
                account.setCardKeyDigest(cryptoService.digest(product.getId() + ":" + cardKey));
                account.setSaleStatus(SaleStatus.UNSOLD);
                account.setEnableStatus(EnableStatus.ENABLED);
                productAccountMapper.insert(account);
                counter++;
            }
        }
    }
}