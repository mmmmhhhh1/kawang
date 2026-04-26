package org.example.kah.controller;

import lombok.RequiredArgsConstructor;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.SupportChatService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/support/messages")
@RequiredArgsConstructor
public class SupportAttachmentController {

    private final SupportChatService supportChatService;

    @GetMapping("/{messageId}/attachment")
    public ResponseEntity<Resource> attachment(Authentication authentication, @PathVariable Long messageId) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        Resource resource = supportChatService.loadAttachment(currentUser, messageId);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        boolean inline = mediaType.getType().equalsIgnoreCase("image");
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline().filename(resource.getFilename() == null ? "attachment" : resource.getFilename()).build()
                : ContentDisposition.attachment().filename(resource.getFilename() == null ? "attachment" : resource.getFilename()).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(mediaType)
                .body(resource);
    }
}
