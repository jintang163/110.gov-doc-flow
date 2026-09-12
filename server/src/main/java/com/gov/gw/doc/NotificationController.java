package com.gov.gw.doc;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepo notificationRepo;
    private final AuthService authService;

    public NotificationController(NotificationRepo notificationRepo, AuthService authService) {
        this.notificationRepo = notificationRepo;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<Notification>> list() {
        return ApiResponse.ok(notificationRepo.findByUserIdOrderByIdDesc(authService.currentId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notificationRepo.countByUserIdAndReadFlagFalse(authService.currentId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        notificationRepo.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(authService.currentId())) {
                n.setReadFlag(true);
                notificationRepo.save(n);
            }
        });
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        List<Notification> list = notificationRepo.findByUserIdOrderByIdDesc(authService.currentId());
        list.forEach(n -> n.setReadFlag(true));
        notificationRepo.saveAll(list);
        return ApiResponse.ok();
    }
}
