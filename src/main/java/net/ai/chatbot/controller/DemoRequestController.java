package net.ai.chatbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.DemoRequest;
import net.ai.chatbot.service.DemoRequestEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/api/demo-request")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DemoRequestController {

    private final DemoRequestEmailService demoRequestEmailService;

    @PostMapping
    public ResponseEntity<?> submitDemoRequest(@RequestBody DemoRequest request) {
        log.info("Received demo request from {} ({})", request.getFullName(), request.getEmail());

        // Validate required fields
        if (!StringUtils.hasText(request.getFullName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
        }
        if (!StringUtils.hasText(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        if (!StringUtils.hasText(request.getCompany())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Company name is required"));
        }
        if (!StringUtils.hasText(request.getCompanySize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Company size is required"));
        }
        if (!StringUtils.hasText(request.getPrimaryGoal())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Primary goal is required"));
        }

        boolean success = demoRequestEmailService.sendDemoRequestEmail(request);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Demo request submitted successfully"
            ));
        } else {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to process demo request"
            ));
        }
    }
}
