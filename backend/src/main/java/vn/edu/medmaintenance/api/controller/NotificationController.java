package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.service.NotificationService;

@RestController @RequestMapping("/api/notifications") public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) {
        this.service=service;
    }
    @GetMapping public PageResponse<NotificationResponse> list(@Valid @ModelAttribute PageQuery query) {
        return service.list(PageRequests.create(query, Set.of("createdAt", "id"), "createdAt", Sort.Direction.DESC));
    }
    @GetMapping("/unread-count") public Map<String, Long> unread() {
        return Map.of("count", service.unread());
    }
    @PostMapping("/{id}/read") public NotificationResponse read(@PathVariable Long id) {
        PageRequests.requirePositive(id, "id");
        return service.read(id);
    }
    @PostMapping("/read-all") public void readAll() {
        service.readAll();
    }
}
