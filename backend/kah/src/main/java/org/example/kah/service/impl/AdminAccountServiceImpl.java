package org.example.kah.service.impl;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountItemRequest;
import org.example.kah.dto.admin.AdminAccountView;
import org.example.kah.entity.AccountStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.service.AdminAccountService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CryptoService;
import org.example.kah.util.MaskingUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminAccountService} 的默认实现。
 * 负责账号池维护，并在导入或状态变更后同步商品库存统计。
 */
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl extends AbstractCrudService<ProductAccount, Long> implements AdminAccountService {

    private final ProductAccountMapper productAccountMapper;
    private final ProductMapper productMapper;
    private final CryptoService cryptoService;

    /**
     * 按商品或状态筛选账号池列表。
     */
    @Override
    public List<AdminAccountView> list(Long productId, String status) {
        List<ProductAccount> accounts = productId == null
                ? productAccountMapper.findAll()
                : productAccountMapper.findByProductId(productId);
        return accounts.stream()
                .filter(account -> status == null || status.isBlank() || status.equals(account.getStatus()))
                .map(this::toView)
                .toList();
    }

    /**
     * 批量新增账号。
     * 这里会对账号名、密码和备注做加密存储，并在完成后回刷库存统计。
     */
    @Override
    @Transactional
    public List<AdminAccountView> create(AdminAccountCreateRequest request) {
        ShopProduct product = productMapper.findById(request.productId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        List<AdminAccountView> created = new ArrayList<>();
        for (AdminAccountItemRequest item : request.items()) {
            ProductAccount account = new ProductAccount();
            String accountName = trim(item.accountName());
            account.setProductId(request.productId());
            account.setAccountNameMasked(MaskingUtils.maskAccount(accountName));
            account.setAccountCiphertext(cryptoService.encrypt(accountName));
            account.setSecretCiphertext(cryptoService.encrypt(trim(item.secret())));
            account.setNoteCiphertext(item.note() == null || item.note().isBlank() ? null : cryptoService.encrypt(trim(item.note())));
            account.setAccountDigest(cryptoService.digest(request.productId() + ":" + accountName));
            account.setStatus(AccountStatus.AVAILABLE);
            try {
                productAccountMapper.insert(account);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在重复账号：" + accountName);
            }
            ProductAccount createdAccount = productAccountMapper.findById(account.getId());
            createdAccount.setProductTitle(product.getTitle());
            created.add(toView(createdAccount));
        }

        productMapper.syncStats();
        return created;
    }

    /**
     * 更新账号可用状态。
     * 已分配到订单的账号不允许直接切换状态，避免破坏履约记录。
     */
    @Override
    @Transactional
    public AdminAccountView updateStatus(Long id, String status) {
        if (!AccountStatus.AVAILABLE.equals(status) && !AccountStatus.DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 AVAILABLE 或 DISABLED");
        }

        ProductAccount account = requireById(id);
        if (AccountStatus.ASSIGNED.equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已分配订单的账号不能直接变更状态");
        }
        if (!status.equals(account.getStatus())) {
            productAccountMapper.updateStatus(id, status);
            productMapper.syncStats();
        }

        ProductAccount latest = productAccountMapper.findById(id);
        latest.setProductTitle(resolveProductTitle(latest.getProductId()));
        return toView(latest);
    }

    /**
     * 按主键查询账号实体。
     */
    @Override
    protected ProductAccount findEntityById(Long id) {
        return productAccountMapper.findById(id);
    }

    /**
     * 返回实体名称供异常提示复用。
     */
    @Override
    protected String entityLabel() {
        return "账号";
    }

    /**
     * 解析账号所属商品标题。
     */
    private String resolveProductTitle(Long productId) {
        ShopProduct product = productMapper.findById(productId);
        return product == null ? "-" : product.getTitle();
    }

    /**
     * 将账号实体映射为后台视图。
     */
    private AdminAccountView toView(ProductAccount account) {
        return new AdminAccountView(
                account.getId(),
                account.getProductId(),
                account.getProductTitle(),
                account.getAccountNameMasked(),
                account.getStatus(),
                account.getAssignedOrderId(),
                account.getAssignedAt(),
                account.getCreatedAt());
    }
}
