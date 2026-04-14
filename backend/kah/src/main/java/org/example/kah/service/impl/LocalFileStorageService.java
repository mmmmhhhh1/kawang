package org.example.kah.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.config.ShopStorageProperties;
import org.example.kah.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final ShopStorageProperties storageProperties;

    @Override
    public String saveRechargeScreenshot(MultipartFile file) {
        return save(file, "recharge-screenshots");
    }

    @Override
    public String savePaymentQr(MultipartFile file) {
        return save(file, "payment-qrs");
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        Path resolved = resolve(relativePath);
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return new FileSystemResource(resolved);
    }

    @Override
    public Path resolve(String relativePath) {
        Path basePath = Paths.get(storageProperties.basePath()).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非法文件路径");
        }
        return resolved;
    }

    private String save(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传文件");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        String relativePath = folder + "/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return relativePath.replace('\\', '/');
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".png";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        return extension.length() > 10 ? ".png" : extension;
    }
}