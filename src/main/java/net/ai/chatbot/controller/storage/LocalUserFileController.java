package net.ai.chatbot.controller.storage;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.service.storage.LocalUserStorageService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Serves one local user file by its id.
 *
 * The AI Images page sets {@code img.src} to the returned URL and does not attach
 * a bearer token, so a request with no identity is allowed when the file name is
 * a known id. A request that does carry a user identity can only read that user's folder.
 * Directory listing is not exposed.
 */
@RestController
@RequestMapping("/v1/api/public/user-files")
@RequiredArgsConstructor
public class LocalUserFileController {

    private final LocalUserStorageService localUserStorage;

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) {
        if (!LocalUserStorageService.isSafeFileName(fileName)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Path> file = localUserStorage.findByFileName(fileName);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String userId = AuthUtils.getUserId();
        String email = AuthUtils.getUserEmail();
        if ((userId != null || email != null) && !localUserStorage.ownedBy(file.get(), userId, email)) {
            return ResponseEntity.status(403).build();
        }

        String contentType = LocalUserStorageService.contentTypeFor(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(file.get()));
    }
}
