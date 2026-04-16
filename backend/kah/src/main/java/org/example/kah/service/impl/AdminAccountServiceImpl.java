package org.example.kah.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountDetailView;
import org.example.kah.dto.admin.AdminAccountItemRequest;
import org.example.kah.dto.admin.AdminAccountView;
import org.example.kah.entity.AccountStatus;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ResourceType;
import org.example.kah.entity.SaleStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.entity.UsedStatus;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.service.AdminAccountService;
import org.example.kah.service.OrderReservationService;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.ProductLockExecutorService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.AllocationHandleGenerator;
import org.example.kah.util.CryptoService;
import org.example.kah.util.CursorCodecUtils;
import org.example.kah.util.MaskingUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl extends AbstractCrudService<ProductAccount, Long> implements AdminAccountService {

    private final ProductAccountMapper productAccountMapper;
    private final ProductMapper productMapper;
    private final CryptoService cryptoService;
    private final ProductLockExecutorService productLockExecutorService;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final OrderReservationService orderReservationService;

    @Override
    public List<AdminAccountView> list(Long productId, String saleStatus, String enableStatus) {
        return queryViews(productId, saleStatus, enableStatus);
    }

    @Override
    public CursorPageResponse<AdminAccountView> page(
            int size,
            String cursor,
            Long productId,
            String saleStatus,
            String enableStatus,
            String usedStatus,
            String keyword) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", productId);
        params.put("saleStatus", trim(saleStatus));
        params.put("enableStatus", trim(enableStatus));
        params.put("usedStatus", trim(usedStatus));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<ProductAccount> rows = productAccountMapper.findCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<ProductAccount> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toView).toList(), nextCursor, hasMore);
    }

    @Override
    public AdminAccountDetailView detail(Long id) {
        ProductAccount account = productAccountMapper.findDetailById(id);
        if (account == null || !ResourceType.CARD_KEY.equals(account.getResourceType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "卡密不存在");
        }
        return new AdminAccountDetailView(
                account.getId(),
                account.getProductId(),
                account.getProductTitle(),
                decryptCardKey(account),
                account.getSaleStatus(),
                account.getEnableStatus(),
                account.getUsedStatus(),
                account.getAssignedOrderId(),
                account.getAssignedOrderNo(),
                account.getAssignedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                decryptNote(account));
    }

    @Override
    public List<AdminAccountView> create(AdminAccountCreateRequest request) {
        AtomicReference<PoolMutation> mutationRef = new AtomicReference<>(PoolMutation.none());
        return productLockExecutorService.execute(
                request.productId(),
                () -> {
                    CreateBatchResult result = doCreate(request);
                    mutationRef.set(new PoolMutation(result.addItems(), List.of()));
                    return result.views();
                },
                () -> refreshProductState(request.productId(), mutationRef.get()));
    }

    @Override
    public AdminAccountView updateStatus(Long id, String enableStatus) {
        ProductAccount account = requireCardKey(id);
        AtomicReference<PoolMutation> mutationRef = new AtomicReference<>(PoolMutation.none());
        return productLockExecutorService.execute(
                account.getProductId(),
                () -> {
                    UpdateStatusResult result = doUpdateStatus(id, enableStatus);
                    mutationRef.set(result.mutation());
                    return result.view();
                },
                () -> refreshProductState(account.getProductId(), mutationRef.get()));
    }

    @Override
    public AdminAccountView updateUsedStatus(Long id, String usedStatus) {
        ProductAccount account = requireCardKey(id);
        return productLockExecutorService.execute(account.getProductId(), () -> doUpdateUsedStatus(id, usedStatus), null);
    }

    @Override
    public int bulkDisable(String scope, Long productId) {
        return handleBulkStatus(scope, productId, false);
    }

    @Override
    public int bulkEnable(String scope, Long productId) {
        return handleBulkStatus(scope, productId, true);
    }

    @Override
    public void delete(Long id) {
        ProductAccount account = requireCardKey(id);
        PoolMutation mutation = PoolMutation.removeOnly(account.getAllocationHandle());
        productLockExecutorService.execute(
                account.getProductId(),
                () -> doDelete(id),
                () -> refreshProductState(account.getProductId(), mutation));
    }

    @Override
    protected ProductAccount findEntityById(Long id) {
        return productAccountMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "卡密";
    }

    private List<AdminAccountView> queryViews(Long productId, String saleStatus, String enableStatus) {
        List<ProductAccount> cardKeys = productId == null
                ? productAccountMapper.findAllCardKeys()
                : productAccountMapper.findCardKeysByProductId(productId);
        return cardKeys.stream()
                .filter(item -> saleStatus == null || saleStatus.isBlank() || saleStatus.equals(item.getSaleStatus()))
                .filter(item -> enableStatus == null || enableStatus.isBlank() || enableStatus.equals(item.getEnableStatus()))
                .map(this::toView)
                .toList();
    }

    private CreateBatchResult doCreate(AdminAccountCreateRequest request) {
        ShopProduct product = productMapper.findById(request.productId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        List<AdminAccountView> created = new ArrayList<>();
        Map<String, Double> addItems = new LinkedHashMap<>();
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
            account.setUsedStatus(UsedStatus.UNUSED);
            insertCardKey(account, cardKey);
            ProductAccount createdAccount = productAccountMapper.findDetailById(account.getId());
            if (createdAccount != null) {
                created.add(toView(createdAccount));
                addAvailabilityItem(addItems, createdAccount);
            }
        }

        productMapper.syncStatsByProductId(request.productId());
        return new CreateBatchResult(created, addItems);
    }

    private UpdateStatusResult doUpdateStatus(Long id, String enableStatus) {
        String safeEnableStatus = trim(enableStatus);
        if (!EnableStatus.ENABLED.equals(safeEnableStatus) && !EnableStatus.DISABLED.equals(safeEnableStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 ENABLED 或 DISABLED");
        }

        ProductAccount account = requireCardKey(id);
        if (!safeEnableStatus.equals(account.getEnableStatus())) {
            productAccountMapper.updateEnableStatus(id, safeEnableStatus);
            productMapper.syncStatsByProductId(account.getProductId());
        }

        ProductAccount updatedAccount = productAccountMapper.findDetailById(id);
        PoolMutation mutation = safeEnableStatus.equals(account.getEnableStatus())
                ? PoolMutation.none()
                : buildAvailabilityMutation(updatedAccount);
        return new UpdateStatusResult(toView(updatedAccount), mutation);
    }

    private AdminAccountView doUpdateUsedStatus(Long id, String usedStatus) {
        String safeUsedStatus = trim(usedStatus);
        if (!UsedStatus.USED.equals(safeUsedStatus) && !UsedStatus.UNUSED.equals(safeUsedStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 USED 或 UNUSED");
        }

        ProductAccount account = requireCardKey(id);
        if (!SaleStatus.SOLD.equals(account.getSaleStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未售出卡密不能修改使用状态");
        }
        if (!safeUsedStatus.equals(account.getUsedStatus())) {
            productAccountMapper.updateUsedStatus(id, safeUsedStatus);
        }
        return toView(productAccountMapper.findDetailById(id));
    }

    private int handleBulkStatus(String scope, Long productId, boolean enable) {
        String safeScope = trim(scope);
        if ("PRODUCT".equalsIgnoreCase(safeScope)) {
            require(productId != null, "按商品批量操作时必须传入 productId");
            ShopProduct product = productMapper.findById(productId);
            if (product == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
            }
            return productLockExecutorService.execute(
                    productId,
                    () -> doBulkStatusByProduct(productId, enable),
                    () -> refreshProductState(productId, null));
        }
        if ("ALL".equalsIgnoreCase(safeScope)) {
            int updated = 0;
            for (Long affectedProductId : productAccountMapper.findAllCardKeyProductIds()) {
                updated += productLockExecutorService.execute(
                        affectedProductId,
                        () -> doBulkStatusByProduct(affectedProductId, enable),
                        () -> refreshProductState(affectedProductId, null));
            }
            return updated;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "批量范围仅支持 PRODUCT 或 ALL");
    }

    private int doBulkStatusByProduct(Long productId, boolean enable) {
        int updated = enable
                ? productAccountMapper.bulkEnableCardKeysByProduct(productId)
                : productAccountMapper.bulkDisableCardKeysByProduct(productId);
        productMapper.syncStatsByProductId(productId);
        return updated;
    }

    private void doDelete(Long id) {
        ProductAccount account = requireCardKey(id);
        if (SaleStatus.SOLD.equals(account.getSaleStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已售卡密不能删除，请改用停用功能");
        }
        productAccountMapper.deleteById(id);
        productMapper.syncStatsByProductId(account.getProductId());
    }

    private ProductAccount requireCardKey(Long id) {
        ProductAccount account = requireById(id);
        if (!ResourceType.CARD_KEY.equals(account.getResourceType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史账号池记录不允许通过卡密接口修改");
        }
        return account;
    }

    private void insertCardKey(ProductAccount account, String cardKey) {
        for (int attempt = 0; attempt < 5; attempt++) {
            account.setAllocationHandle(AllocationHandleGenerator.newHandle());
            try {
                productAccountMapper.insert(account);
                return;
            } catch (DuplicateKeyException exception) {
                if (attempt == 4) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "存在重复卡密：" + cardKey);
                }
            }
        }
    }

    private void refreshProductState(Long productId, PoolMutation mutation) {
        productCacheRefreshService.refreshStatsAfterWrite(productId);
        if (mutation == null) {
            orderReservationService.rebuildProductPool(productId);
            return;
        }
        orderReservationService.removeAvailableHandles(productId, mutation.removeHandles());
        orderReservationService.addAvailableItems(productId, mutation.addItems());
    }

    private PoolMutation buildAvailabilityMutation(ProductAccount account) {
        if (account == null || account.getAllocationHandle() == null || account.getAllocationHandle().isBlank()) {
            return PoolMutation.none();
        }
        if (SaleStatus.UNSOLD.equals(account.getSaleStatus()) && EnableStatus.ENABLED.equals(account.getEnableStatus())) {
            return PoolMutation.addOnly(Map.of(account.getAllocationHandle(), account.getId().doubleValue()));
        }
        return PoolMutation.removeOnly(account.getAllocationHandle());
    }

    private void addAvailabilityItem(Map<String, Double> addItems, ProductAccount account) {
        if (account == null || account.getAllocationHandle() == null || account.getAllocationHandle().isBlank()) {
            return;
        }
        if (SaleStatus.UNSOLD.equals(account.getSaleStatus()) && EnableStatus.ENABLED.equals(account.getEnableStatus())) {
            addItems.put(account.getAllocationHandle(), account.getId().doubleValue());
        }
    }

    private AdminAccountView toView(ProductAccount account) {
        return new AdminAccountView(
                account.getId(),
                account.getProductId(),
                account.getProductTitle(),
                decryptCardKey(account),
                account.getSaleStatus(),
                account.getEnableStatus(),
                account.getUsedStatus(),
                account.getAssignedOrderId(),
                account.getAssignedOrderNo(),
                account.getAssignedAt(),
                account.getCreatedAt());
    }

    private String decryptCardKey(ProductAccount account) {
        if (account.getCardKeyCiphertext() != null && !account.getCardKeyCiphertext().isBlank()) {
            return cryptoService.decrypt(account.getCardKeyCiphertext());
        }
        return account.getAccountNameMasked();
    }

    private String decryptNote(ProductAccount account) {
        if (account.getNoteCiphertext() == null || account.getNoteCiphertext().isBlank()) {
            return null;
        }
        return cryptoService.decrypt(account.getNoteCiphertext());
    }

    private record CreateBatchResult(List<AdminAccountView> views, Map<String, Double> addItems) {
    }

    private record UpdateStatusResult(AdminAccountView view, PoolMutation mutation) {
    }

    private record PoolMutation(Map<String, Double> addItems, List<String> removeHandles) {
        private static PoolMutation none() {
            return new PoolMutation(Map.of(), List.of());
        }

        private static PoolMutation addOnly(Map<String, Double> addItems) {
            return new PoolMutation(addItems == null ? Map.of() : addItems, List.of());
        }

        private static PoolMutation removeOnly(String handle) {
            if (handle == null || handle.isBlank()) {
                return none();
            }
            return new PoolMutation(Map.of(), List.of(handle));
        }
    }
}