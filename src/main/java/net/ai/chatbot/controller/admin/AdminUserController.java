package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.AdminUser;
import net.ai.chatbot.dto.admin.UserRole;
import net.ai.chatbot.dto.admin.response.UserListResponse;
import net.ai.chatbot.service.admin.AdminUserService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<UserListResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            UserListResponse response = adminUserService.getAllUsers(page, size, role, isActive, search);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUser> getUserById(@PathVariable String userId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            AdminUser user = adminUserService.getUserById(userId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Error fetching user {}", userId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<AdminUser> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            UserRole role = UserRole.valueOf(body.get("role"));
            AdminUser user = adminUserService.updateUserRole(userId, role);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Error updating user role {}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{userId}/active")
    public ResponseEntity<Void> setActive(
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            adminUserService.setActive(userId, body.get("isActive"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating user active status {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            adminUserService.deleteUser(userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Block a user - prevents login and platform access
     */
    @PostMapping("/{userId}/block")
    public ResponseEntity<Map<String, Object>> blockUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            String reason = body.getOrDefault("reason", "Blocked by administrator");
            adminUserService.blockUser(userId, reason);
            log.info("User {} blocked by admin {} - Reason: {}", userId, AuthUtils.getEmail(), reason);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User has been blocked successfully",
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Error blocking user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Unblock a user - restores login access
     */
    @PostMapping("/{userId}/unblock")
    public ResponseEntity<Map<String, Object>> unblockUser(@PathVariable String userId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            adminUserService.unblockUser(userId);
            log.info("User {} unblocked by admin {}", userId, AuthUtils.getEmail());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User has been unblocked successfully",
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Error unblocking user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Process refund for a user's transaction
     */
    @PostMapping("/{userId}/refund")
    public ResponseEntity<Map<String, Object>> refundUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            String transactionId = (String) body.get("transactionId");
            String reason = (String) body.getOrDefault("reason", "Refund processed by administrator");
            Double amount = body.get("amount") != null ? ((Number) body.get("amount")).doubleValue() : null;
            
            Map<String, Object> result = adminUserService.processRefund(userId, transactionId, amount, reason);
            log.info("Refund processed for user {} by admin {} - Transaction: {}", userId, AuthUtils.getEmail(), transactionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error processing refund for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user's transaction history for refund processing
     */
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<Map<String, Object>>> getUserTransactions(@PathVariable String userId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> transactions = adminUserService.getUserTransactions(userId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Error fetching transactions for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/stats/countries")
    public ResponseEntity<List<Map<String, Object>>> getUsersByCountry() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> stats = adminUserService.getUsersByCountry();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching users by country", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/stats/registrations")
    public ResponseEntity<List<Map<String, Object>>> getUserRegistrations(
            @RequestParam(defaultValue = "30") int days) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> stats = adminUserService.getUserRegistrationsByDay(days);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching user registrations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Map<String, Object> stats = adminUserService.getComprehensiveUserStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching comprehensive user stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
