package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.vo.PlatformBindingVO;
import com.moodfm.service.platform.PlatformBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "平台绑定", description = "音乐平台账号绑定管理")
@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PlatformBindingController {

    private final PlatformBindingService platformBindingService;

    @Operation(summary = "获取所有绑定列表")
    @GetMapping
    public R<List<PlatformBindingVO>> list(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(platformBindingService.listBindings(userId));
    }

    @Operation(summary = "生成二维码（扫码绑定第一步）")
    @PostMapping("/{platform}/qr/generate")
    public R<Map<String, Object>> generateQr(@PathVariable String platform,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(platformBindingService.generateQrCode(userId, platform));
    }

    @Operation(summary = "检查扫码状态（前端轮询）")
    @GetMapping("/{platform}/qr/status")
    public R<Map<String, Object>> checkQrStatus(@PathVariable String platform,
                                                 @RequestParam String key,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(platformBindingService.checkQrStatus(userId, platform, key));
    }

    @Operation(summary = "手动 Cookie 绑定（高级用户）")
    @PostMapping("/{platform}/bind/cookie")
    public R<Void> bindByCookie(@PathVariable String platform,
                                 @RequestBody Map<String, String> body,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        String cookie = body.get("cookie");
        if (cookie == null || cookie.isBlank()) {
            return R.fail(400, "cookie 不能为空");
        }
        platformBindingService.bindByCookie(userId, platform, cookie);
        return R.ok();
    }

    @Operation(summary = "设置默认平台")
    @PutMapping("/{platform}/default")
    public R<Void> setDefault(@PathVariable String platform,
                               @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        platformBindingService.setDefault(userId, platform);
        return R.ok();
    }

    @Operation(summary = "解绑平台")
    @DeleteMapping("/{platform}")
    public R<Void> unbind(@PathVariable String platform,
                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        platformBindingService.unbind(userId, platform);
        return R.ok();
    }
}
