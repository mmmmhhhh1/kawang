package org.example.kah.service;

import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminPaymentQrItemView;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface PaymentQrService {

    CursorPageResponse<AdminPaymentQrItemView> page(int size, String cursor);

    AdminPaymentQrItemView upload(String name, MultipartFile file, Long operatorId);

    AdminPaymentQrItemView activate(Long id, Long operatorId);

    AdminPaymentQrItemView disable(Long id, Long operatorId);

    Resource loadActiveQr();

    Resource loadById(Long id);
}