package com.mercato.Controller;

import com.mercato.Payloads.Response.AdminDashboardStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminController {
    private final com.mercato.Service.AdminUserActivityService activity;
    @GetMapping("/admin/users/{id}/activity")
    public com.mercato.Payloads.Response.AdminUserActivityDTO userActivity(@PathVariable String id,
            @RequestParam(defaultValue="overview") String section,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="ALL") String status) {
        return activity.inspect(id,section,page,status);
    }
}
