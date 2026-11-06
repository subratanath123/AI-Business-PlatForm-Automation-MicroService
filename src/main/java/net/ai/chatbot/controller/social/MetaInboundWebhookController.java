package net.ai.chatbot.controller.social;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.service.social.MetaInboundMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta webhook previously hosted on n8n. Point the Meta app callback here.
 */
@RestController
@RequestMapping("/v1/api/meta/webhook")
@RequiredArgsConstructor
public class MetaInboundWebhookController {

    private final MetaInboundMessageService metaInboundMessageService;

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(value = "hub.mode", required = false) String mode,
                                         @RequestParam(value = "hub.verify_token", required = false) String token,
                                         @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && metaInboundMessageService.isValidVerifyToken(token) && challenge != null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody byte[] body,
                                          @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!metaInboundMessageService.hasValidSignature(body, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }
        metaInboundMessageService.accept(body);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
