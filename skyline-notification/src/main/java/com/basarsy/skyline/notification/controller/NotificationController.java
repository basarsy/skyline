package com.basarsy.skyline.notification.controller;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.response.ApiResponse;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.notification.repository.NotificationRepository;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's in-app notifications. Every endpoint is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notifications;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(@AuthenticationPrincipal Object principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID userId = userId(principal);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        var content = notifications.findByUser(userId, request.getPageSize(), request.getOffset()).stream()
                .map(n -> new NotificationResponse(n.id(), n.kind(), n.title(), n.body(), n.link(),
                        n.createdAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(), n.readAt() != null))
                .toList();
        return ApiResponse.success("Notifications retrieved",
                new PageImpl<>(content, request, notifications.countByUser(userId)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(new UnreadCount(notifications.countUnread(userId(principal))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Object principal, @PathVariable UUID id) {
        if (!notifications.markRead(userId(principal), id)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        return ApiResponse.success("Notification marked as read", null);
    }

    @PostMapping("/read-all")
    public ApiResponse<UnreadCount> markAllRead(@AuthenticationPrincipal Object principal) {
        notifications.markAllRead(userId(principal));
        return ApiResponse.success("All notifications marked as read", new UnreadCount(0));
    }

    /** Only user accounts have an inbox (an internal service caller has none). */
    private static UUID userId(Object principal) {
        if (principal instanceof SkylineUserDetails user && user.getId() != null) {
            return user.getId();
        }
        throw new SkylineException("This operation requires a user account", HttpStatus.FORBIDDEN);
    }

    public record UnreadCount(long count) {}
}
