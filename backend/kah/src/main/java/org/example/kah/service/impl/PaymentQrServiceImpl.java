package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminPaymentQrItemView;
import org.example.kah.entity.PaymentQrConfig;
import org.example.kah.entity.PaymentQrStatus;
import org.example.kah.mapper.PaymentQrConfigMapper;
import org.example.kah.service.FileStorageService;
import org.example.kah.service.PaymentQrService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PaymentQrServiceImpl extends AbstractServiceSupport implements PaymentQrService {

    private final PaymentQrConfigMapper paymentQrConfigMapper;
    private final FileStorageService fileStorageService;

    @Override
    public List<AdminPaymentQrItemView> list() {
        return paymentQrConfigMapper.findAll().stream().map(this::toView).toList();
    }

    @Override
    public CursorPageResponse<AdminPaymentQrItemView> page(int size, String cursor) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<PaymentQrConfig> rows = paymentQrConfigMapper.findCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<PaymentQrConfig> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toView).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional
    public AdminPaymentQrItemView upload(String name, MultipartFile file, Long operatorId) {
        PaymentQrConfig config = new PaymentQrConfig();
        config.setName(trim(name));
        config.setImagePath(fileStorageService.savePaymentQr(file));
        config.setStatus(PaymentQrStatus.DISABLED);
        config.setCreatedBy(operatorId);
        paymentQrConfigMapper.insert(config);
        return toView(paymentQrConfigMapper.findById(config.getId()));
    }

    @Override
    @Transactional
    public AdminPaymentQrItemView activate(Long id, Long operatorId) {
        requireExisting(id);
        paymentQrConfigMapper.disableAll();
        paymentQrConfigMapper.activate(id, operatorId);
        return toView(paymentQrConfigMapper.findById(id));
    }

    @Override
    @Transactional
    public AdminPaymentQrItemView disable(Long id, Long operatorId) {
        PaymentQrConfig existing = requireExisting(id);
        paymentQrConfigMapper.disable(id);
        return toView(paymentQrConfigMapper.findById(existing.getId()));
    }

    @Override
    public Resource loadActiveQr() {
        PaymentQrConfig config = paymentQrConfigMapper.findActive();
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前没有生效的收款二维码");
        }
        return fileStorageService.loadAsResource(config.getImagePath());
    }

    @Override
    public Resource loadById(Long id) {
        return fileStorageService.loadAsResource(requireExisting(id).getImagePath());
    }

    private PaymentQrConfig requireExisting(Long id) {
        PaymentQrConfig config = paymentQrConfigMapper.findById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收款二维码不存在");
        }
        return config;
    }

    private AdminPaymentQrItemView toView(PaymentQrConfig config) {
        return new AdminPaymentQrItemView(
                config.getId(),
                config.getName(),
                config.getStatus(),
                "/api/admin/payment-qr/" + config.getId() + "/image",
                config.getCreatedByName(),
                config.getActivatedByName(),
                config.getActivatedAt(),
                config.getCreatedAt());
    }
}