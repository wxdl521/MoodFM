package com.moodfm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "健康检查")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "moodfm-backend");
    }
}
