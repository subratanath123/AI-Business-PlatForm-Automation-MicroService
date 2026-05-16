package net.ai.chatbot.dto.admin.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminDashboardSummary {

    private long totalUsers;
    private long activeUsers;
    private long newUsersToday;
    private long newUsersThisMonth;

    private long totalTransactions;
    private BigDecimal totalRevenue;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;

    private long activeSubscriptions;
    private long expiredSubscriptions;
    private long cancelledSubscriptions;

    private TicketSummary ticketSummary;
    private List<CountryStats> topCountries;
    private List<RevenueByDay> revenueChart;
    private List<UsersByDay> userRegistrationChart;
    private Map<String, Long> subscriptionsByPlan;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TicketSummary {
        private long openTickets;
        private long inProgressTickets;
        private long pendingTickets;
        private long resolvedTickets;
        private long closedTickets;
        private long totalTickets;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CountryStats {
        private String country;
        private long userCount;
        private BigDecimal revenue;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RevenueByDay {
        private String date;
        private BigDecimal revenue;
        private long transactionCount;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UsersByDay {
        private String date;
        private long count;
    }
}
