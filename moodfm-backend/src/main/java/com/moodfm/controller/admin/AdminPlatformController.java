package com.moodfm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.common.result.R;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.mapper.PlatformBindingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理员-平台", description = "平台绑定统计 / Cookie 到期预警")
@RestController
@RequestMapping("/api/admin/platforms")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminPlatformController {

    private final PlatformBindingMapper platformBindingMapper;

    @Operation(summary = "各平台绑定统计（总数 / 即将到期 / 已失效）")
    @GetMapping("/stats")
    public R<Map<String, Map<String, Long>>> getPlatformStats() {
        // 1. Collect every distinct platform from the table
        List<PlatformBinding> all = platformBindingMapper.selectList(
                new LambdaQueryWrapper<PlatformBinding>()
                        .select(PlatformBinding::getPlatform));

        List<String> platforms = all.stream()
                .map(PlatformBinding::getPlatform)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, Map<String, Long>> result = new LinkedHashMap<>();

        for (String platform : platforms) {
            // bound = total for this platform
            long bound = platformBindingMapper.selectCount(
                    new LambdaQueryWrapper<PlatformBinding>()
                            .eq(PlatformBinding::getPlatform, platform));

            // expiring = expires within 3 days AND is_valid = 1
            long expiring = platformBindingMapper.selectCount(
                    new LambdaQueryWrapper<PlatformBinding>()
                            .eq(PlatformBinding::getPlatform, platform)
                            .eq(PlatformBinding::getIsValid, 1)
                            .apply("expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 3 DAY)"));

            // expired = already past expires_at OR is_valid = 0
            long expired = platformBindingMapper.selectCount(
                    new LambdaQueryWrapper<PlatformBinding>()
                            .eq(PlatformBinding::getPlatform, platform)
                            .and(w -> w.apply("expires_at < NOW()").or().eq(PlatformBinding::getIsValid, 0)));

            Map<String, Long> stats = new LinkedHashMap<>();
            stats.put("bound", bound);
            stats.put("expiring", expiring);
            stats.put("expired", expired);
            result.put(platform, stats);
        }

        return R.ok(result);
    }

    @Operation(summary = "7 天内到期及已过期 Cookie 列表（含用户名）")
    @GetMapping("/cookie-expiry")
    public R<List<Map<String, Object>>> getCookieExpiry() {
        List<Map<String, Object>> rows = platformBindingMapper.selectExpiringCookies();
        return R.ok(rows);
    }
}
