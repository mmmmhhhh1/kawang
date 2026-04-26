package org.example.kah.service;

import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String saveRechargeScreenshot(MultipartFile file);

    String savePaymentQr(MultipartFile file);

    String saveSupportAttachment(MultipartFile file);

    Resource loadAsResource(String relativePath);

    Path resolve(String relativePath);
}
