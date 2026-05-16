package net.ai.chatbot.dto.admin.response;

import lombok.*;
import net.ai.chatbot.dto.admin.AdminUser;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserListResponse {

    private List<AdminUser> users;
    private long totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    private long activeCount;
    private long inactiveCount;
    private long adminCount;
    private long businessCount;
}
