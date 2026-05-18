package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.common.result.ResultCode;
import com.moodfm.domain.vo.SearchResultVO;
import com.moodfm.service.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "搜索", description = "关键词搜索与心情语义搜索")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final SearchService searchService;

    private Long uid(UserDetails ud) {
        return Long.parseLong(ud.getUsername());
    }

    @Operation(summary = "搜索歌曲",
               description = "mode=keyword 精确关键词搜索（转发至绑定平台）；mode=mood 心情语义搜索（向量检索）")
    @GetMapping
    public R<SearchResultVO> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "keyword") String mode,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails ud) {
        if (q == null || q.isBlank()) {
            return R.fail(ResultCode.BAD_REQUEST, "搜索词不能为空");
        }
        if (!"keyword".equals(mode) && !"mood".equals(mode)) {
            return R.fail(ResultCode.BAD_REQUEST, "mode 仅支持 keyword / mood");
        }
        return R.ok(searchService.search(uid(ud), q.trim(), mode, Math.min(limit, 50)));
    }
}
