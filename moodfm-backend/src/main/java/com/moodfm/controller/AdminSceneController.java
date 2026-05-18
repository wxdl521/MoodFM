package com.moodfm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.common.result.R;
import com.moodfm.domain.entity.SceneTemplate;
import com.moodfm.mapper.SceneTemplateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "管理员-场景模板", description = "AI 引擎场景模板管理")
@RestController
@RequestMapping("/api/admin/scenes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminSceneController {

    private final SceneTemplateMapper sceneMapper;

    @Operation(summary = "场景模板列表")
    @GetMapping
    public R<List<SceneTemplate>> list() {
        return R.ok(sceneMapper.selectList(
                new LambdaQueryWrapper<SceneTemplate>().orderByAsc(SceneTemplate::getId)));
    }

    @Operation(summary = "更新场景模板")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SceneTemplate scene = sceneMapper.selectById(id);
        if (scene == null) return R.fail(404, "场景不存在");

        if (body.containsKey("name"))   scene.setName(String.valueOf(body.get("name")));
        if (body.containsKey("cn"))     scene.setCn(String.valueOf(body.get("cn")));
        if (body.containsKey("key"))    scene.setKey(String.valueOf(body.get("key")));
        if (body.containsKey("active")) scene.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
        scene.setUpdatedAt(LocalDateTime.now());
        sceneMapper.updateById(scene);
        return R.ok();
    }

    @Operation(summary = "新建场景模板")
    @PostMapping
    public R<SceneTemplate> create(@RequestBody SceneTemplate scene) {
        scene.setId(null);
        scene.setCreatedAt(LocalDateTime.now());
        scene.setUpdatedAt(LocalDateTime.now());
        if (scene.getSongs() == null) scene.setSongs(0);
        if (scene.getAccuracy() == null) scene.setAccuracy("—");
        sceneMapper.insert(scene);
        return R.ok(scene);
    }
}
