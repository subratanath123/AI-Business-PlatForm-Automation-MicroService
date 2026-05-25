package net.ai.chatbot.controller.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.notification.BillingAlertConfigDao;
import net.ai.chatbot.dao.notification.NotificationDao;
import net.ai.chatbot.dto.notification.BillingAlertConfig;
import net.ai.chatbot.dto.notification.Notification;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationDao notificationDao;
    private final BillingAlertConfigDao billingAlertConfigDao;

    /** GET /v1/api/notifications — recent 30 notifications + unread count */
    @GetMapping
    public ResponseEntity<NotificationsResponse> getNotifications() {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        List<Notification> items = notificationDao.findByUserId(userId, 30);
        long unread = notificationDao.countUnread(userId);
        return ResponseEntity.ok(new NotificationsResponse(items, unread));
    }

    /** POST /v1/api/notifications/{id}/read */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        notificationDao.markRead(id, userId);
        return ResponseEntity.ok().build();
    }

    /** POST /v1/api/notifications/read-all */
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        notificationDao.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    /** DELETE /v1/api/notifications/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        notificationDao.deleteById(id, userId);
        return ResponseEntity.ok().build();
    }

    // ── Billing alert config ──────────────────────────────────────────

    /** GET /v1/api/notifications/alert-config */
    @GetMapping("/alert-config")
    public ResponseEntity<BillingAlertConfig> getAlertConfig() {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(billingAlertConfigDao.findOrCreate(userId));
    }

    /** PUT /v1/api/notifications/alert-config */
    @PutMapping("/alert-config")
    public ResponseEntity<BillingAlertConfig> saveAlertConfig(@RequestBody BillingAlertConfig body) {
        String userId = AuthUtils.getUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        body.setUserId(userId);
        return ResponseEntity.ok(billingAlertConfigDao.save(body));
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    public record NotificationsResponse(List<Notification> notifications, long unreadCount) {}
}
