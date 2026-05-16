package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.AdminUserDao;
import net.ai.chatbot.dao.admin.TransactionDao;
import net.ai.chatbot.dto.admin.AdminUser;
import net.ai.chatbot.dto.admin.Transaction;
import net.ai.chatbot.dto.admin.TransactionStatus;
import net.ai.chatbot.dto.admin.UserRole;
import net.ai.chatbot.dto.admin.response.UserListResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserDao adminUserDao;
    private final TransactionDao transactionDao;

    public AdminUser getUserById(String id) {
        return adminUserDao.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public AdminUser getUserByEmail(String email) {
        return adminUserDao.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    public UserListResponse getAllUsers(int page, int size, UserRole role, Boolean isActive, String search) {
        List<AdminUser> users = adminUserDao.findAll(page, size, role, isActive, search);
        long totalCount = adminUserDao.count(role, isActive, search);

        return UserListResponse.builder()
                .users(users)
                .totalCount(totalCount)
                .page(page)
                .pageSize(size)
                .totalPages((int) Math.ceil((double) totalCount / size))
                .activeCount(adminUserDao.countActive())
                .inactiveCount(adminUserDao.countTotal() - adminUserDao.countActive())
                .adminCount(adminUserDao.countByRole(UserRole.ADMIN) + adminUserDao.countByRole(UserRole.SUPER_ADMIN))
                .businessCount(adminUserDao.countByRole(UserRole.BUSINESS))
                .build();
    }

    public AdminUser updateUserRole(String userId, UserRole role) {
        AdminUser user = getUserById(userId);
        user.setRole(role);
        log.info("Updating user {} role to {}", user.getEmail(), role);
        return adminUserDao.save(user);
    }

    public void setActive(String userId, boolean isActive) {
        log.info("Setting user {} active status to {}", userId, isActive);
        adminUserDao.setActive(userId, isActive);
    }

    public void updateLastLogin(String userId) {
        adminUserDao.updateLastLogin(userId);
    }

    public boolean isAdmin(String email) {
        return adminUserDao.isAdmin(email);
    }

    public AdminUser createOrUpdateUser(String id, String email, String userName, String picture) {
        AdminUser user = adminUserDao.findByEmail(email).orElse(null);

        if (user == null) {
            user = AdminUser.builder()
                    .id(id)
                    .email(email)
                    .userName(userName)
                    .picture(picture)
                    .role(UserRole.USER)
                    .isActive(true)
                    .build();
        } else {
            user.setUserName(userName);
            user.setPicture(picture);
        }

        return adminUserDao.save(user);
    }

    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);
        adminUserDao.delete(userId);
    }

    public void blockUser(String userId, String reason) {
        AdminUser user = getUserById(userId);
        user.setBlocked(true);
        user.setBlockReason(reason);
        user.setBlockedAt(Instant.now());
        user.setBlockedBy(AuthUtils.getEmail());
        user.setActive(false);
        adminUserDao.save(user);
        log.info("User {} blocked. Reason: {}", user.getEmail(), reason);
    }

    public void unblockUser(String userId) {
        AdminUser user = getUserById(userId);
        user.setBlocked(false);
        user.setBlockReason(null);
        user.setBlockedAt(null);
        user.setBlockedBy(null);
        user.setActive(true);
        adminUserDao.save(user);
        log.info("User {} unblocked", user.getEmail());
    }

    public Map<String, Object> processRefund(String userId, String transactionId, Double amount, String reason) {
        Map<String, Object> result = new HashMap<>();
        
        AdminUser user = getUserById(userId);
        
        if (transactionId != null && !transactionId.isEmpty()) {
            Transaction transaction = transactionDao.findById(transactionId)
                    .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
            
            if (transaction.getStatus() != TransactionStatus.COMPLETED) {
                throw new RuntimeException("Transaction is not eligible for refund. Status: " + transaction.getStatus());
            }
            
            BigDecimal refundAmount = amount != null ? BigDecimal.valueOf(amount) : transaction.getAmount();
            
            transaction.setStatus(TransactionStatus.REFUNDED);
            transaction.setRefundedAt(Instant.now());
            transaction.setRefundReason(reason);
            transaction.setRefundedBy(AuthUtils.getEmail());
            transaction.setRefundAmount(refundAmount);
            transactionDao.save(transaction);
            
            result.put("transactionId", transactionId);
            result.put("refundAmount", refundAmount);
            result.put("originalAmount", transaction.getAmount());
        }
        
        result.put("success", true);
        result.put("message", "Refund processed successfully");
        result.put("userId", userId);
        result.put("userEmail", user.getEmail());
        result.put("processedBy", AuthUtils.getEmail());
        result.put("processedAt", Instant.now().toString());
        result.put("reason", reason);
        
        return result;
    }

    public List<Map<String, Object>> getUserTransactions(String userId) {
        AdminUser user = getUserById(userId);
        List<Transaction> transactions = transactionDao.findByUserEmail(user.getEmail());
        
        return transactions.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("amount", t.getAmount());
            map.put("currency", t.getCurrency());
            map.put("status", t.getStatus() != null ? t.getStatus().name() : null);
            map.put("transactionNumber", t.getTransactionNumber());
            map.put("planName", t.getPlanName());
            map.put("gateway", t.getGateway() != null ? t.getGateway().name() : null);
            map.put("pricingType", t.getPricingType() != null ? t.getPricingType().name() : null);
            map.put("createdAt", t.getCreatedAt());
            map.put("paidAt", t.getPaidAt());
            map.put("refundedAt", t.getRefundedAt());
            map.put("refundAmount", t.getRefundAmount());
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUsersByCountry() {
        return adminUserDao.countByCountry();
    }

    public List<java.util.Map<String, Object>> getUserRegistrationsByDay(int days) {
        return adminUserDao.getUserRegistrationsByDay(days);
    }

    public java.util.Map<String, Object> getComprehensiveUserStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        // Basic counts
        long totalUsers = adminUserDao.countTotal();
        stats.put("totalUsers", totalUsers);
        stats.put("onlineUsers", adminUserDao.countRecentlyActive(15)); // active in last 15 minutes
        stats.put("visitorsToday", adminUserDao.countLoggedInToday());
        stats.put("newUsersThisMonth", adminUserDao.countNewUsersThisMonth());
        
        // Country distribution (top 12)
        List<java.util.Map<String, Object>> countries = adminUserDao.countByCountry();
        List<java.util.Map<String, Object>> topCountries = countries.stream()
            .limit(12)
            .map(c -> {
                java.util.Map<String, Object> country = new java.util.HashMap<>();
                country.put("country", c.getOrDefault("country", "Unknown"));
                country.put("code", c.getOrDefault("code", "XX"));
                country.put("count", c.getOrDefault("count", 0));
                return country;
            })
            .collect(java.util.stream.Collectors.toList());
        stats.put("countryDistribution", topCountries);
        
        // Daily registrations for current month
        List<java.util.Map<String, Object>> dailyReg = adminUserDao.getDailyRegistrationsThisMonth();
        stats.put("dailyRegistrations", dailyReg);
        
        // Monthly registrations for current year
        List<java.util.Map<String, Object>> monthlyReg = adminUserDao.getMonthlyRegistrationsThisYear();
        stats.put("monthlyRegistrations", monthlyReg);
        
        return stats;
    }
}
