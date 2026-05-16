package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.*;
import net.ai.chatbot.dto.admin.SubscriptionStatus;
import net.ai.chatbot.dto.admin.TicketStatus;
import net.ai.chatbot.dto.admin.TransactionStatus;
import net.ai.chatbot.dto.admin.response.AdminDashboardSummary;
import net.ai.chatbot.dto.admin.response.AdminDashboardSummary.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminUserDao adminUserDao;
    private final TransactionDao transactionDao;
    private final UserSubscriptionDao userSubscriptionDao;
    private final SupportTicketDao supportTicketDao;

    public AdminDashboardSummary getDashboardSummary() {
        log.info("Fetching admin dashboard summary");

        Instant now = Instant.now();
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        Instant startOfMonth = now.truncatedTo(ChronoUnit.DAYS)
                .minus(now.atZone(java.time.ZoneOffset.UTC).getDayOfMonth() - 1, ChronoUnit.DAYS);

        long totalUsers = adminUserDao.countTotal();
        long activeUsers = adminUserDao.countActive();
        long newUsersToday = adminUserDao.countCreatedToday();
        long newUsersThisMonth = adminUserDao.countCreatedThisMonth();

        long totalTransactions = transactionDao.count(null, null, null);
        BigDecimal totalRevenue = transactionDao.sumTotalAmount(TransactionStatus.COMPLETED);
        BigDecimal revenueToday = transactionDao.sumTotalAmountBetween(
                TransactionStatus.COMPLETED, startOfDay, now);
        BigDecimal revenueThisMonth = transactionDao.sumTotalAmountBetween(
                TransactionStatus.COMPLETED, startOfMonth, now);

        long activeSubscriptions = userSubscriptionDao.countByStatus(SubscriptionStatus.ACTIVE);
        long expiredSubscriptions = userSubscriptionDao.countByStatus(SubscriptionStatus.EXPIRED);
        long cancelledSubscriptions = userSubscriptionDao.countByStatus(SubscriptionStatus.CANCELLED);

        TicketSummary ticketSummary = TicketSummary.builder()
                .openTickets(supportTicketDao.countByStatus(TicketStatus.OPEN))
                .inProgressTickets(supportTicketDao.countByStatus(TicketStatus.IN_PROGRESS))
                .pendingTickets(supportTicketDao.countByStatus(TicketStatus.PENDING))
                .resolvedTickets(supportTicketDao.countByStatus(TicketStatus.RESOLVED))
                .closedTickets(supportTicketDao.countByStatus(TicketStatus.CLOSED))
                .totalTickets(supportTicketDao.count(null, null))
                .build();

        List<CountryStats> topCountries = adminUserDao.countByCountry().stream()
                .map(m -> CountryStats.builder()
                        .country((String) m.get("country"))
                        .userCount(((Number) m.get("count")).longValue())
                        .build())
                .collect(Collectors.toList());

        List<RevenueByDay> revenueChart = transactionDao.getRevenueByDay(30).stream()
                .map(m -> RevenueByDay.builder()
                        .date((String) m.get("date"))
                        .revenue(m.get("revenue") != null ?
                                BigDecimal.valueOf(((Number) m.get("revenue")).doubleValue()) :
                                BigDecimal.ZERO)
                        .transactionCount(m.get("transactionCount") != null ?
                                ((Number) m.get("transactionCount")).longValue() : 0L)
                        .build())
                .collect(Collectors.toList());

        List<UsersByDay> userRegistrationChart = adminUserDao.getUserRegistrationsByDay(30).stream()
                .map(m -> UsersByDay.builder()
                        .date((String) m.get("date"))
                        .count(((Number) m.get("count")).longValue())
                        .build())
                .collect(Collectors.toList());

        Map<String, Long> subscriptionsByPlan = userSubscriptionDao.countByPlanGrouped();

        return AdminDashboardSummary.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .newUsersToday(newUsersToday)
                .newUsersThisMonth(newUsersThisMonth)
                .totalTransactions(totalTransactions)
                .totalRevenue(totalRevenue)
                .revenueToday(revenueToday)
                .revenueThisMonth(revenueThisMonth)
                .activeSubscriptions(activeSubscriptions)
                .expiredSubscriptions(expiredSubscriptions)
                .cancelledSubscriptions(cancelledSubscriptions)
                .ticketSummary(ticketSummary)
                .topCountries(topCountries)
                .revenueChart(revenueChart)
                .userRegistrationChart(userRegistrationChart)
                .subscriptionsByPlan(subscriptionsByPlan)
                .build();
    }
}
