package org.example.kah.controller;

import lombok.RequiredArgsConstructor;
import org.example.kah.service.PaymentQrService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台支付展示接口。
 */
@RestController
@RequestMapping("/api/public/payment")
@RequiredArgsConstructor
public class PublicPaymentController {

    private final PaymentQrService paymentQrService;

    /**
     * 获取当前生效的支付宝收款码。
     */
    @GetMapping("/alipay-qr")
    public ResponseEntity<Resource> activeAlipayQr() {
        Resource resource = paymentQrService.loadActiveQr();
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}