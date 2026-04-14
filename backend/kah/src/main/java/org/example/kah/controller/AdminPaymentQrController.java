package org.example.kah.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminPaymentQrItemView;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminPermissionService;
import org.example.kah.service.PaymentQrService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/payment-qr")
@RequiredArgsConstructor
public class AdminPaymentQrController {

    private final PaymentQrService paymentQrService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public ApiResponse<List<AdminPaymentQrItemView>> list() {
        return ApiResponse.success(paymentQrService.list());
    }

    @GetMapping("/page")
    public ApiResponse<CursorPageResponse<AdminPaymentQrItemView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success(paymentQrService.page(size, cursor));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminPaymentQrItemView> upload(
            Authentication authentication,
            @RequestParam String name,
            @RequestParam MultipartFile file) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        adminPermissionService.requireSuperAdmin(currentUser);
        return ApiResponse.success(paymentQrService.upload(name, file, currentUser.userId()));
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<AdminPaymentQrItemView> activate(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        adminPermissionService.requireSuperAdmin(currentUser);
        return ApiResponse.success(paymentQrService.activate(id, currentUser.userId()));
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<AdminPaymentQrItemView> disable(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        adminPermissionService.requireSuperAdmin(currentUser);
        return ApiResponse.success(paymentQrService.disable(id, currentUser.userId()));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        Resource resource = paymentQrService.loadById(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}