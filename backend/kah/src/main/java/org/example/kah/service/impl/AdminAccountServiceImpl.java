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
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ResourceType;
import org.example.kah.entity.SaleStatus;
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
 * 负责卡密池管理，并在导入、启停、删除后同步商品库存统计。
 */
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl extends AbstractCrudService<ProductAccount, Long> implements AdminAccountService {

    private final ProductAccountMapper productAccountMapper;
    private final ProductMapper productMapper;
    private final CryptoService cryptoService;

    /** 查询卡密池列表。 */
    @Override
    public List<AdminAccountView> list(Long productId, String saleStatus, String enableStatus) {
        List<ProductAccount> cardKeys = productId == null
                ? productAccountMapper.findAllCardKeys()
                : productAccountMapper.findCardKeysByProductId(productId);
        return cardKeys.stream()
                .filter(item -> saleStatus == null || saleStatus.isBlank() || saleStatus.equals(item.getSaleStatus()))
                .filter(item -> enableStatus == null || enableStatus.isBlank() || enableStatus.equals(item.getEnableStatus()))
                .map(this::toView)
                .toList();
    }

    /** 批量导入卡密。 */
    @Override
    @Transactional
    public List<AdminAccountView> create(AdminAccountCreateRequest request) {
        ShopProduct product = productMapper.findById(request.productId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        List<AdminAccountView> created = new ArrayList<>();
        for (AdminAccountItemRequest item : request.items()) {
            String cardKey = trim(item.cardKey());
            ProductAccount account = new ProductAccount();
            account.setProductId(request.productId());
            account.setAccountNameMasked(MaskingUtils.maskAccount(cardKey));
            account.setAccountCiphertext(cryptoService.encrypt(cardKey));
            account.setSecretCiphertext(cryptoService.encrypt(""));
            account.setNoteCiphertext(item.note() == null || item.note().isBlank() ? null : cryptoService.encrypt(trim(item.note())));
            account.setAccountDigest(cryptoService.digest(request.productId() + ":" + cardKey));
            account.setStatus(AccountStatus.AVAILABLE);
            account.setResourceType(ResourceType.CARD_KEY);
            account.setCardKeyCiphertext(cryptoService.encrypt(cardKey));
            account.setCardKeyDigest(cryptoService.digest(request.productId() + ":" + cardKey));
            account.setSaleStatus(SaleStatus.UNSOLD);
            account.setEnableStatus(EnableStatus.ENABLED);
            try {
                productAccountMapper.insert(account);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在重复卡密：" + cardKey);
            }
            ProductAccount createdAccount = productAccountMapper.findById(account.getId());
            createdAccount.setProductTitle(product.getTitle());
            created.add(toView(createdAccount));
        }

        productMapper.syncStats();
        return created;
    }

    /** 更新单条卡密启用状态。 */
    @Override
    @Transactional
    public AdminAccountView updateStatus(Long id, String enableStatus) {
        String safeEnableStatus = trim(enableStatus);
        if (!EnableStatus.ENABLED.equals(safeEnableStatus) && !EnableStatus.DISABLED.equals(safeEnableStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 ENABLED 或 DISABLED");
        }

        ProductAccount account = requireCardKey(id);
        if (!safeEnableStatus.equals(account.getEnableStatus())) {
            productAccountMapper.updateEnableStatus(id, safeEnableStatus);
            productMapper.syncStats();
        }

        ProductAccount latest = productAccountMapper.findById(id);
        latest.setProductTitle(resolveProductTitle(latest.getProductId()));
        return toView(latest);
    }

    /** 批量停用卡密。 */
    @Override
    @Transactional
    public int bulkDisable(String scope, Long productId) {
        int updated = handleBulkStatus(scope, productId, false);
        productMapper.syncStats();
        return updated;
    }

    /** 批量启用卡密。 */
    @Override
    @Transactional
    public int bulkEnable(String scope, Long productId) {
        int updated = handleBulkStatus(scope, productId, true);
        productMapper.syncStats();
        return updated;
    }

    /**
     * 删除单条卡密。
     * 只允许删除未售出的卡密；已售卡密需要保留历史发货痕迹，只允许停用。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        ProductAccount account = requireCardKey(id);
        if (SaleStatus.SOLD.equals(account.getSaleStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已售卡密不能删除，请改用停用功能");
        }
        productAccountMapper.deleteById(id);
        productMapper.syncStats();
    }

    /** 按主键查询资源实体。 */
    @Override
    protected ProductAccount findEntityById(Long id) {
        return productAccountMapper.findById(id);
    }

    /** 返回实体名称供异常提示复用。 */
    @Override
    protected String entityLabel() {
        return "卡密";
    }

    /** 统一处理批量启停逻辑。 */
    private int handleBulkStatus(String scope, Long productId, boolean enable) {
        String safeScope = trim(scope);
        int updated;
        if ("PRODUCT".equalsIgnoreCase(safeScope)) {
            require(productId != null, "按商品批量操作时必须传入 productId");
            ShopProduct product = productMapper.findById(productId);
            if (product == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
            }
            updated = enable
                    ? productAccountMapper.bulkEnableCardKeysByProduct(productId)
                    : productAccountMapper.bulkDisableCardKeysByProduct(productId);
        } else if ("ALL".equalsIgnoreCase(safeScope)) {
            updated = enable
                    ? productAccountMapper.bulkEnableAllCardKeys()
                    : productAccountMapper.bulkDisableAllCardKeys();
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批量范围仅支持 PRODUCT 或 ALL");
        }
        return updated;
    }

    /** 校验记录属于卡密池主流程。 */
    private ProductAccount requireCardKey(Long id) {
        ProductAccount account = requireById(id);
        if (!ResourceType.CARD_KEY.equals(account.getResourceType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史账号池记录不允许通过卡密接口修改");
        }
        return account;
    }

    /** 解析卡密所属商品标题。 */
    private String resolveProductTitle(Long productId) {
        ShopProduct product = productMapper.findById(productId);
        return product == null ? "-" : product.getTitle();
    }

    /** 将资源实体映射为后台视图。 */
    private AdminAccountView toView(ProductAccount account) {
        return new AdminAccountView(
                account.getId(),
                account.getProductId(),
                account.getProductTitle(),
                decryptCardKey(account),
                account.getSaleStatus(),
                account.getEnableStatus(),
                account.getAssignedOrderId(),
                account.getAssignedAt(),
                account.getCreatedAt());
    }

    /** 解密卡密正文。 */
    private String decryptCardKey(ProductAccount account) {
        if (account.getCardKeyCiphertext() != null && !account.getCardKeyCiphertext().isBlank()) {
            return cryptoService.decrypt(account.getCardKeyCiphertext());
        }
        return account.getAccountNameMasked();
    }
}