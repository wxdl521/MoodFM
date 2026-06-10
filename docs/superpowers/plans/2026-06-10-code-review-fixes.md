# MoodFM Code Review 修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 2026-06-10 全项目 code review 发现的功能性 bug、生产部署隐患和安全问题，使项目达到可安全上线状态。

**Architecture:** 按风险/收益排序分 8 个阶段执行：先修前后端契约（拦截器、HTTP 状态码），再修生产部署配置（profile、nginx、volume），然后移除整条死掉的 WebSocket 链路（REST 反馈通道已可用），接着补验证码限流、修 Electron 壳的生产指向，最后做 status/deleted 字段拆分迁移和 CI。每个阶段独立可交付、可单独 commit/push。

**Tech Stack:** Spring Boot 3.3 / MyBatis-Plus / Flyway / Redis、Vue 3 + TS + Vite（新增 vitest）、Electron 33 + electron-builder、Docker Compose、GitHub Actions。

**约定:**
- 后端测试命令（Windows PowerShell，项目自带 Maven）：
  `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
- 前端命令在 `moodfm-frontend/` 目录下执行。
- 提交信息沿用仓库现有风格（`fix(frontend): ...` / `feat(...)` / `db(flyway): ...`）。
- 当前分支 `feat/electron-shell`，Task 0 先把未提交工作收尾。

---

## 总览表

| 阶段 | 内容 | 修复的 Review 问题 | 预估 | 风险 |
|---|---|---|---|---|
| Phase 0 | 当前分支收尾 + 仓库卫生 | 工程化 #4 | 0.5h | 低 |
| Phase 1 | 前后端契约修复（拦截器 + HTTP 状态码） | 🔴 #2、#3 | 2h | 低 |
| Phase 2 | 生产部署配置（profile / nginx / volume） | 🔴 #4、#5 | 1.5h | 低 |
| Phase 3 | 移除死掉的 WebSocket 层 | 🔴 #1 | 1.5h | 中 |
| Phase 4 | 验证码发送限流 | 🟠 #8 | 1.5h | 低 |
| Phase 5 | Electron 壳修复（URL 注入 / 安全 / 打包） | 🔴 #6 | 2h | 中 |
| Phase 6 | status / deleted 字段拆分（DB 迁移） | 🔴 #7 | 2h | 高 |
| Phase 7 | 快速收尾（TTL 封顶 / UserDetails 缓存 / 密钥出库） | 🟡 #13、#11、🟠 #10 | 1.5h | 低 |
| Phase 8 | CI 流水线 | 工程化 #1 | 1h | 低 |

每个 Phase 完成后立即 push，不要攒。

---

# Phase 0: 当前分支收尾 + 仓库卫生

### Task 0: 提交 feat/electron-shell 的未提交工作

**Files:**
- Move: `icon.png` → `moodfm-electron/assets/icon-source.png`
- Move: `MobilePlan.txt` → `docs/MobilePlan.md`
- Commit: 所有 git status 中的修改与新文件

- [ ] **Step 1: 移动散落在根目录的文件**

```powershell
Move-Item icon.png moodfm-electron\assets\icon-source.png
Move-Item MobilePlan.txt docs\MobilePlan.md
```

- [ ] **Step 2: 把含 dev 密码的 compose 文件排除在 git 之外**

`docker-compose.dev.yml` 含 dev Redis 密码，保持本地文件、不入库（Phase 7 Task 16 会轮换密码）。在 `.gitignore` 的「Environment and secrets」区块追加一行：

```
docker-compose.dev.yml
```

- [ ] **Step 3: 确认工作区状态**

Run: `git status --short`
Expected: 看到 `M README.md`、`M moodfm-backend/pom.xml`、`M moodfm-frontend/...`、`M .gitignore`、`?? moodfm-electron/`、`?? docs/MobilePlan.md` 等；**不应再出现根目录的 icon.png / MobilePlan.txt，也不应出现 docker-compose.dev.yml**。

- [ ] **Step 4: 提交**

```powershell
git add -A
git commit -m "feat(electron): desktop shell with tray, media keys, taskbar + frontend bridge"
```

---

# Phase 1: 前后端契约修复

### Task 1: 前端引入 vitest 测试基础设施

**Files:**
- Create: `moodfm-frontend/vitest.config.ts`
- Modify: `moodfm-frontend/package.json`

- [ ] **Step 1: 安装依赖**

```powershell
cd moodfm-frontend
npm install -D vitest happy-dom
```

- [ ] **Step 2: 创建 vitest 配置**

创建 `moodfm-frontend/vitest.config.ts`：

```ts
import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.spec.ts'],
  },
})
```

- [ ] **Step 3: 添加 npm script**

修改 `moodfm-frontend/package.json` 的 scripts 块：

```json
"scripts": {
  "dev": "vite",
  "build": "vue-tsc --build --force && vite build",
  "preview": "vite preview",
  "type-check": "vue-tsc --build --force",
  "test": "vitest run"
}
```

- [ ] **Step 4: 验证 vitest 可运行（无测试也应正常退出）**

Run: `npm test`
Expected: `No test files found` 且退出码 0（vitest 默认 passWithNoTests=false 时会报错——若报错，在 `vitest.config.ts` 的 `test` 块加 `passWithNoTests: true`，Task 2 写入第一个测试后再移除该行也可，保留亦无害）。

- [ ] **Step 5: Commit**

```powershell
git add package.json package-lock.json vitest.config.ts
git commit -m "chore(frontend): add vitest test infrastructure"
```

### Task 2: 修复响应拦截器——按 code 判断成败而非 data 字段

**背景:** 后端 `R.java` 标了 `@JsonInclude(NON_NULL)`，`R.ok()`（Void 成功）序列化为 `{"code":200,"message":"操作成功"}`（无 `data` 字段）。现拦截器 `client.ts:34-37` 以 `'data' in body` 判断成败，导致**所有 R<Void> 成功响应被 reject**（登出、反馈上报、保存偏好等）。

**Files:**
- Modify: `moodfm-frontend/src/api/client.ts:31-39`
- Test: `moodfm-frontend/src/api/client.spec.ts`（新建）

- [ ] **Step 1: 写失败的测试**

创建 `moodfm-frontend/src/api/client.spec.ts`：

```ts
import { describe, it, expect } from 'vitest'
import { unwrapResponse } from './client'

describe('unwrapResponse', () => {
  it('R<Void> 成功（code=200 无 data 字段）应当 resolve 而非 reject', async () => {
    const result = unwrapResponse({ data: { code: 200, message: '操作成功' } })
    await expect(Promise.resolve(result)).resolves.toBeUndefined()
  })

  it('R<T> 成功应返回 data', () => {
    expect(
      unwrapResponse({ data: { code: 200, message: 'ok', data: { id: 1 } } }),
    ).toEqual({ id: 1 })
  })

  it('业务失败（code≠200）应 reject 并携带 code', async () => {
    await expect(
      unwrapResponse({ data: { code: 410, message: '电台会话已结束' } }) as Promise<unknown>,
    ).rejects.toMatchObject({ code: 410, message: '电台会话已结束' })
  })

  it('非 R 包装的响应应原样返回', () => {
    expect(unwrapResponse({ data: 'plain-text' })).toBe('plain-text')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test`
Expected: FAIL——`unwrapResponse` 未导出（`client.ts` 当前是匿名内联函数）。

- [ ] **Step 3: 实现修复**

修改 `moodfm-frontend/src/api/client.ts`，把成功拦截器（原 31-39 行）替换为：

```ts
// 后端统一返回 R<T> = { code, message, data? }。data 为 null 时被 @JsonInclude(NON_NULL)
// 省略，因此必须按 code 判断成败，不能看有没有 data 字段。
export function unwrapResponse(res: { data: unknown }) {
  const body = res.data as Record<string, unknown> | null
  if (body && typeof body === 'object' && !Array.isArray(body) && 'code' in body) {
    if (body.code === 200) return (body as { data?: unknown }).data
    return Promise.reject({ message: (body.message as string) || '请求失败', code: body.code })
  }
  return body
}

api.interceptors.response.use(
  unwrapResponse,
  async (err) => {
    // …… 401 刷新逻辑保持原样，不要动 ……
  }
)
```

> 注意：错误处理分支（401 刷新）**原样保留**，只替换成功分支。
> 行为变化说明：旧代码对「code≠200 但带 data 的失败响应」会错误地 resolve，新代码统一 reject——这是修正，不是回归。

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test`
Expected: 4 passed。

- [ ] **Step 5: 类型检查 + 构建无回归**

Run: `npm run type-check`
Expected: 0 errors。

- [ ] **Step 6: Commit**

```powershell
git add src/api/client.ts src/api/client.spec.ts
git commit -m "fix(frontend): treat R<Void> success (no data field) as success in interceptor"
```

### Task 3: 后端业务异常返回正确的 HTTP 状态码

**背景:** `GlobalExceptionHandler.java:26-33` 只映射 400/401/403/404/429，业务码 1xxx/2xxx/3xxx 全部落到 HTTP 500（密码错误返回 500！）。

**设计决策:** `ResultCode` 枚举自带 `HttpStatus`；登录类失败（密码错误等）映射 **400** 而非 401——前端拦截器把 401 专用于「token 失效→刷新」流程，登录失败用 401 会误触发刷新逻辑。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/result/ResultCode.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/exception/BizException.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/exception/GlobalExceptionHandler.java:24-36`
- Test: `moodfm-backend/src/test/java/com/moodfm/controller/AuthControllerTest.java`

- [ ] **Step 1: 写失败的测试**

在 `AuthControllerTest.java` 追加（import 已有 BizException/ResultCode/jsonPath/status）：

```java
@Test
void loginWrongPasswordReturns400WithBizCode() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setAccount("user@example.com");
    request.setPassword("wrong-password-1");

    when(userService.login(any(LoginRequest.class)))
            .thenThrow(new BizException(ResultCode.WRONG_PASSWORD));

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1003));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test -Dtest=AuthControllerTest`
Expected: 新测试 FAIL——实际返回 500。

- [ ] **Step 3: 给 ResultCode 加 HttpStatus**

替换 `ResultCode.java` 全文：

```java
package com.moodfm.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功", HttpStatus.OK),
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或 Token 已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    TOO_MANY_REQUESTS(429, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // 业务错误码 (1xxx) —— 登录类失败用 400，401 保留给 token 失效（前端会触发刷新流程）
    USER_NOT_FOUND(1001, "用户不存在", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(1002, "邮箱或手机号已注册", HttpStatus.CONFLICT),
    WRONG_PASSWORD(1003, "密码错误", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED(1004, "账号已被锁定，请15分钟后再试", HttpStatus.LOCKED),
    ACCOUNT_DISABLED(1005, "账号已注销", HttpStatus.FORBIDDEN),

    // 验证码错误码 (11xx)
    INVALID_SMS_CODE(1101, "短信验证码错误或已过期", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_CODE(1102, "邮箱验证码错误或已过期", HttpStatus.BAD_REQUEST),
    OTP_TOO_MANY_ATTEMPTS(1105, "验证码尝试次数过多，请重新获取", HttpStatus.TOO_MANY_REQUESTS),

    // 平台绑定错误码 (2xxx)
    PLATFORM_NOT_BOUND(2001, "音乐平台账号未绑定", HttpStatus.BAD_REQUEST),
    PLATFORM_COOKIE_INVALID(2002, "平台账号 Cookie 已失效，请重新绑定", HttpStatus.BAD_REQUEST),
    COOKIE_INVALID(2003, "Cookie 无效或已过期，请重新获取", HttpStatus.BAD_REQUEST),
    PHONE_CODE_SEND_FAILED(2004, "短信验证码发送失败", HttpStatus.BAD_GATEWAY),
    PHONE_CODE_VERIFY_FAILED(2005, "短信验证码错误或已过期", HttpStatus.BAD_REQUEST),

    // AI 错误码 (3xxx)
    RECALL_FAILED(3002, "歌曲召回失败", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
```

- [ ] **Step 4: BizException 携带 HttpStatus**

替换 `BizException.java` 全文：

```java
package com.moodfm.common.exception;

import com.moodfm.common.result.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    /** 兼容旧的 new BizException(500, "...") 调用方式 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 410 -> HttpStatus.GONE;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default  -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

- [ ] **Step 5: GlobalExceptionHandler 使用异常自带状态**

替换 `GlobalExceptionHandler.java` 的 `handleBizException` 方法（原 24-36 行）：

```java
@ExceptionHandler(BizException.class)
public R<Void> handleBizException(BizException e, HttpServletResponse response) {
    response.setStatus(e.getHttpStatus().value());
    return R.fail(e.getCode(), e.getMessage());
}
```

- [ ] **Step 6: 跑测试，修正受影响的旧断言**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: 新测试 PASS。`AuthControllerTest.loginReturnsAccountLockedCodeWhenServiceRejectsLockedAccount` 若断言旧的 5xx 状态会 FAIL——把它的状态断言改为：

```java
.andExpect(status().isLocked())   // 423，原来是 500
```

其余对 `$.code` 的断言不变。再跑一次直到全绿。

- [ ] **Step 7: Commit**

```powershell
git add moodfm-backend/src
git commit -m "fix(backend): map business error codes to proper HTTP status instead of blanket 500"
```

---

# Phase 2: 生产部署配置修复

### Task 4: docker-compose.yml 默认 prod profile + 必填变量 + uploads volume

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`

- [ ] **Step 1: 修改 backend 服务的 environment 与 volumes**

将 `docker-compose.yml` 中 backend 服务的 `environment:` 块整体替换为：

```yaml
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: moodfm
      MYSQL_USER: ${MYSQL_USER:-moodfm}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
      MUSIC_ADAPTER_URL: http://music-adapter:3000
      ADAPTER_SECRET: ${ADAPTER_SECRET:?ADAPTER_SECRET is required}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      COOKIE_ENCRYPTION_KEY: ${COOKIE_ENCRYPTION_KEY:?COOKIE_ENCRYPTION_KEY is required}
      LLM_BASE_URL: ${LLM_BASE_URL:-https://api.deepseek.com}
      LLM_API_KEY: ${LLM_API_KEY:?LLM_API_KEY is required}
      LLM_MODEL: ${LLM_MODEL:-deepseek-chat}
      UPLOAD_DIR: /app/uploads
      QDRANT_ENABLED: "false"   # compose 里没有 qdrant 服务，显式关闭避免每次召回都连接失败
    volumes:
      - moodfm_uploads:/app/uploads
```

- [ ] **Step 2: mysql 服务密码也改为必填**

```yaml
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}
      MYSQL_DATABASE: moodfm
      MYSQL_USER: ${MYSQL_USER:-moodfm}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}
```

- [ ] **Step 3: 顶部 volumes 增加 uploads**

```yaml
volumes:
  mysql_data:
  redis_data:
  moodfm_uploads:
```

- [ ] **Step 4: .env.example 默认改 prod**

把 `.env.example:31` 的 `SPRING_PROFILES_ACTIVE=dev` 改为：

```
# 服务器部署必须用 prod（dev 会打开 DEBUG 日志、Swagger、并在日志里输出 OTP 验证码）
# 本地开发自行改成 dev
SPRING_PROFILES_ACTIVE=prod
```

- [ ] **Step 5: 验证 compose 可解析且默认值生效**

Run: `docker compose config | Select-String "SPRING_PROFILES_ACTIVE"`
Expected: 输出 `SPRING_PROFILES_ACTIVE: prod`（本机 .env 若显式写了 dev 则输出 dev——确认服务器上的 .env 改为 prod 即可）。

- [ ] **Step 6: Commit**

```powershell
git add docker-compose.yml .env.example
git commit -m "fix(deploy): default to prod profile, require secrets, persist uploads volume"
```

### Task 5: nginx 补 /uploads 代理与上传体积限制

**Files:**
- Modify: `moodfm-frontend/nginx.conf`

- [ ] **Step 1: 替换 nginx.conf 全文**

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # 后端允许 5MB 头像，nginx 默认 1MB 会先 413，留出余量
    client_max_body_size 6m;

    location /api/ {
        proxy_pass http://backend:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 头像等用户上传文件由后端静态服务（缺了这条，生产环境所有头像 404）
    location /uploads/ {
        proxy_pass http://backend:8081/uploads/;
        proxy_set_header Host $host;
        add_header X-Content-Type-Options nosniff;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 2: 本地验证 nginx 配置语法**

```powershell
docker build -t moodfm-frontend-test moodfm-frontend
docker run --rm moodfm-frontend-test nginx -t
```

Expected: `syntax is ok` + `test is successful`。

- [ ] **Step 3: Commit**

```powershell
git add moodfm-frontend/nginx.conf
git commit -m "fix(deploy): proxy /uploads to backend and raise client_max_body_size"
```

> ⚠️ 部署提醒（不在本计划内执行）：服务器上 `docker compose up -d --build` 后，用浏览器确认头像显示、>1MB 头像可上传。

---

# Phase 3: 移除死掉的 WebSocket 层

**背景与决策:** Review 确认 WS 链路存在 4 处独立断点（端点路径、目的地、鉴权、无发布方），从未工作过；反馈已走 REST（`/api/radio/feedback`），Cookie 过期状态在绑定页可见。**决策：整体删除**。若未来需要实时推送，另立计划重建（见文末「后续独立计划」）。

### Task 6: 前端移除 WS 代码

**Files:**
- Delete: `moodfm-frontend/src/composables/useWebSocket.ts`
- Delete: `moodfm-frontend/src/composables/useNotifications.ts`
- Modify: `moodfm-frontend/src/App.vue`
- Modify: `moodfm-frontend/src/views/player/Player.vue:309,322,774`
- Modify: `moodfm-frontend/package.json`（移除 @stomp/stompjs）

- [ ] **Step 1: Player.vue 移除三处引用**

删除以下三行（行号为当前文件位置）：
- `Player.vue:309` → `import { useWebSocket } from '@/composables/useWebSocket'`
- `Player.vue:322` → `const ws     = useWebSocket()`
- `Player.vue:774` → `if (player.sessionId) ws.connect(player.sessionId)`

- [ ] **Step 2: App.vue 移除通知 toast 与连接逻辑**

删除三处：
1. 模板中整个「Global notification toasts」块（`App.vue:11-38`，`<div v-if="notifications.length" ...>` 到对应 `</div>`）；
2. `App.vue:47` → `import { useNotifications } from '@/composables/useNotifications'`；
3. `App.vue:56` → `const { notifications, connect, dismiss } = useNotifications()`；
4. `App.vue:71-73` 的 watch 块：

```ts
watch(() => authStore.user?.id, (id) => {
  if (id) connect(id)
}, { immediate: true })
```

删除后 `watch` 若无其他使用，从 `import { computed, watch, onMounted } from 'vue'` 中移除 `watch`。

- [ ] **Step 3: 删除两个 composable 文件并卸载依赖**

```powershell
Remove-Item moodfm-frontend\src\composables\useWebSocket.ts, moodfm-frontend\src\composables\useNotifications.ts
cd moodfm-frontend
npm uninstall @stomp/stompjs
```

- [ ] **Step 4: 类型检查 + 测试 + 构建**

```powershell
npm run type-check
npm test
npm run build
```

Expected: 全部通过，无 `useWebSocket`/`useNotifications`/`stompjs` 残留引用报错。

- [ ] **Step 5: 确认无残留引用**

Run: `git grep -n "stompjs\|useWebSocket\|useNotifications" -- moodfm-frontend/src`
Expected: 无输出。

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "refactor(frontend): remove dead WebSocket layer (never connected; feedback uses REST)"
```

### Task 7: 后端移除 WS 代码

**Files:**
- Delete: `moodfm-backend/src/main/java/com/moodfm/websocket/FeedbackMessageHandler.java`
- Delete: `moodfm-backend/src/main/java/com/moodfm/config/WebSocketConfig.java`
- Delete: `moodfm-backend/src/main/java/com/moodfm/scheduler/CookieRefreshScheduler.java`
- Delete: `moodfm-backend/src/test/java/com/moodfm/scheduler/CookieNotificationTest.java`
- Modify: `moodfm-backend/pom.xml:67-71`（移除 websocket starter）
- Modify: `moodfm-backend/src/main/java/com/moodfm/config/SecurityConfig.java:63`（移除 `/ws/**` permitAll）

- [ ] **Step 1: 确认 SimpMessagingTemplate 没有其他使用方**

Run: `git grep -n "SimpMessagingTemplate\|convertAndSend" -- moodfm-backend/src`
Expected: 只命中即将删除的 `FeedbackMessageHandler.java`、`CookieRefreshScheduler.java`、`CookieNotificationTest.java`、`WebSocketConfig.java` 注释。**若出现其他文件，停下来评估后再继续。**

- [ ] **Step 2: 删除四个文件**

```powershell
Remove-Item moodfm-backend\src\main\java\com\moodfm\websocket\FeedbackMessageHandler.java
Remove-Item moodfm-backend\src\main\java\com\moodfm\config\WebSocketConfig.java
Remove-Item moodfm-backend\src\main\java\com\moodfm\scheduler\CookieRefreshScheduler.java
Remove-Item moodfm-backend\src\test\java\com\moodfm\scheduler\CookieNotificationTest.java
```

> 说明：`CookieRefreshScheduler` 的唯一产出就是 WS 推送（从未送达过）。Cookie 有效性检查仍由 `service/platform/CookieExpiryChecker` 负责，绑定页可见状态，无功能回归。

- [ ] **Step 3: pom.xml 移除依赖**

删除 `pom.xml:67-71`：

```xml
<!-- WebSocket (STOMP) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

- [ ] **Step 4: SecurityConfig 移除 /ws/** 放行**

删除 `SecurityConfig.java` permitAll 列表中的 `"/ws/**",` 一行。

- [ ] **Step 5: 编译 + 全量测试**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: BUILD SUCCESS。若有编译错误说明 Step 1 漏了使用方，回去处理。

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m "refactor(backend): remove dead WebSocket/STOMP layer and cookie-expiry push scheduler"
```

---

# Phase 4: 验证码发送限流

### Task 8: SMS/Email 发送加冷却 + 每日上限

**背景:** `/api/auth/sms/send`、`/api/auth/email/send-verification` 是匿名接口，`RateLimitInterceptor` 只限已登录用户——接通真实短信/SMTP 前必须堵上（短信轰炸 + 费用风险）。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/constant/RedisKeys.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/user/impl/UserServiceImpl.java`（`sendSmsCode`、`sendEmailVerification`）
- Test: `moodfm-backend/src/test/java/com/moodfm/service/user/impl/UserServiceOtpRateLimitTest.java`（新建）

- [ ] **Step 1: 写失败的测试**

创建 `UserServiceOtpRateLimitTest.java`：

```java
package com.moodfm.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceOtpRateLimitTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(null, null, null, null, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(userService, "activeProfile", "test");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void sendSmsCode_blockedDuringCooldown() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(true);

        assertThatThrownBy(() -> userService.sendSmsCode("13800000000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void sendSmsCode_blockedAfterDailyLimit() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(false);
        when(valueOps.increment(startsWith("sms:daily:"))).thenReturn(11L);

        assertThatThrownBy(() -> userService.sendSmsCode("13800000000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void sendSmsCode_firstSendStoresCodeAndCooldown() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(false);
        when(valueOps.increment(startsWith("sms:daily:"))).thenReturn(1L);

        userService.sendSmsCode("13800000000");

        verify(valueOps).set(startsWith("sms:code:"), anyString(), eq(Duration.ofMinutes(5)));
        verify(valueOps).set(startsWith("sms:cooldown:"), eq("1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void sendEmailVerification_blockedDuringCooldown() {
        when(redisTemplate.hasKey(startsWith("email:cooldown:"))).thenReturn(true);

        assertThatThrownBy(() -> userService.sendEmailVerification("a@b.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }
}
```

> 构造器参数顺序对应 `UserServiceImpl` 的 `@RequiredArgsConstructor` 字段声明顺序：`userMapper, userProfileMapper, passwordEncoder, jwtUtil, redisTemplate, objectMapper`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test -Dtest=UserServiceOtpRateLimitTest`
Expected: FAIL（限流逻辑尚不存在，发送直接成功）。

- [ ] **Step 3: RedisKeys 增加限流 key**

在 `RedisKeys.java` 的「短信验证码」「邮箱验证码」区块追加：

```java
// 验证码发送限流
public static final String SMS_SEND_COOLDOWN = "sms:cooldown:%s";     // phone
public static final String SMS_SEND_DAILY = "sms:daily:%s:%s";        // phone:yyyyMMdd
public static final String EMAIL_SEND_COOLDOWN = "email:cooldown:%s"; // email
public static final String EMAIL_SEND_DAILY = "email:daily:%s:%s";    // email:yyyyMMdd
```

- [ ] **Step 4: UserServiceImpl 实现限流**

在常量区追加：

```java
private static final Duration OTP_SEND_COOLDOWN = Duration.ofSeconds(60);
private static final int OTP_DAILY_SEND_LIMIT = 10;
```

新增私有方法：

```java
/**
 * 验证码发送限流：60 秒冷却 + 每个目标每日上限。
 * 冷却在最后设置，确保被每日上限拒绝的请求不消耗冷却窗口。
 */
private void checkOtpSendRateLimit(String cooldownKey, String dailyKey) {
    if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
        throw new BizException(ResultCode.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
    }
    Long daily = redisTemplate.opsForValue().increment(dailyKey);
    if (daily != null && daily == 1L) {
        redisTemplate.expire(dailyKey, Duration.ofDays(1));
    }
    if (daily != null && daily > OTP_DAILY_SEND_LIMIT) {
        throw new BizException(ResultCode.TOO_MANY_REQUESTS, "今日发送次数已达上限");
    }
    redisTemplate.opsForValue().set(cooldownKey, "1", OTP_SEND_COOLDOWN);
}
```

`sendSmsCode` 方法体开头插入：

```java
String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
checkOtpSendRateLimit(
        RedisKeys.format(RedisKeys.SMS_SEND_COOLDOWN, phone),
        RedisKeys.format(RedisKeys.SMS_SEND_DAILY, phone, today));
```

`sendEmailVerification` 方法体开头插入：

```java
String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
checkOtpSendRateLimit(
        RedisKeys.format(RedisKeys.EMAIL_SEND_COOLDOWN, email),
        RedisKeys.format(RedisKeys.EMAIL_SEND_DAILY, email, today));
```

- [ ] **Step 5: 运行测试确认通过 + 全量回归**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: 全绿。

- [ ] **Step 6: Commit**

```powershell
git add moodfm-backend/src
git commit -m "feat(auth): rate-limit OTP sending (60s cooldown + 10/day per target)"
```

> 后续增强（不在本计划内）：按 IP 维度限频需要把请求 IP 传入 service，留给接入真实短信通道时一并做。

---

# Phase 5: Electron 壳修复

### Task 9: 修复生产包指向 localhost:5173 的问题

**背景:** `preload.ts:7` 读运行时环境变量 `VITE_SERVER_URL`（用户机器上必然不存在）→ 打包后 App 请求发往 dev server。改为：main 进程从 electron-store 读 `serverUrl`，经 `additionalArguments` 传给 preload（沙箱兼容的标准做法）。

**Files:**
- Modify: `moodfm-electron/src/store.ts`
- Modify: `moodfm-electron/src/main.ts:26-41`
- Modify: `moodfm-electron/src/preload.ts:1-8`

- [ ] **Step 1: store.ts 区分 dev/prod 默认地址**

替换 `store.ts` 的 `defaults` 部分：

```ts
// ⚠️ 决策点：打包发布前必须把 PROD_SERVER_URL 改成真实服务器地址（如 http://你的服务器IP）
const PROD_SERVER_URL = 'http://YOUR_SERVER_IP';

const defaults: StoreSchema = {
  windowBounds: { width: 1200, height: 800 },
  startMinimized: false,
  serverUrl:
    process.env.NODE_ENV === 'development'
      ? 'http://localhost:5173'
      : PROD_SERVER_URL,
};
```

- [ ] **Step 2: main.ts 把 serverUrl 传给 preload**

在 `createWindow()` 中（`const minimized = ...` 之后）取值，并在 `webPreferences` 增加 `additionalArguments`：

```ts
const serverUrl = store.get('serverUrl');

mainWindow = new BrowserWindow({
  // ……既有配置不变……
  webPreferences: {
    preload: path.join(__dirname, 'preload.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: true,
    additionalArguments: [`--moodfm-server-url=${serverUrl}`],
  },
});
```

（`sandbox: false → true` 属于 Task 10 的安全加固，这里一并改掉。）

- [ ] **Step 3: preload.ts 从 process.argv 读取**

替换 `preload.ts` 开头的 server URL 暴露逻辑：

```ts
import { contextBridge, ipcRenderer } from 'electron';

// main 进程通过 webPreferences.additionalArguments 注入（沙箱 preload 也能读 process.argv）
const serverUrlArg = process.argv.find((a) => a.startsWith('--moodfm-server-url='));
const serverUrl = serverUrlArg
  ? serverUrlArg.slice('--moodfm-server-url='.length)
  : 'http://localhost:5173';

contextBridge.exposeInMainWorld('__MOODFM_SERVER_URL', serverUrl);
```

`electronAPI` 桥保持不变。

- [ ] **Step 4: 类型检查 + dev 模式验证**

```powershell
cd moodfm-electron
npm run typecheck
npm run dev
```

Expected: 窗口正常打开（dev 模式加载 5173），DevTools Console 执行 `window.__MOODFM_SERVER_URL` 输出 `http://localhost:5173`。关闭应用。

- [ ] **Step 5: Commit**

```powershell
git add src/store.ts src/main.ts src/preload.ts
git commit -m "fix(electron): inject server URL from store via additionalArguments; enable sandbox"
```

### Task 10: 外链导航守卫

**Files:**
- Modify: `moodfm-electron/src/main.ts`（createWindow 内，loadURL 之前）

- [ ] **Step 1: 增加导航守卫**

在 `main.ts` 顶部 import 中加入 `shell`：

```ts
import {
  app,
  BrowserWindow,
  ipcMain,
  globalShortcut,
  shell,
} from 'electron';
```

在 `createWindow()` 中、`if (isDev)` 加载逻辑之前插入：

```ts
// 外链一律交给系统浏览器，禁止在 Electron 窗口内导航到应用外的地址
mainWindow.webContents.setWindowOpenHandler(({ url }) => {
  shell.openExternal(url);
  return { action: 'deny' };
});
mainWindow.webContents.on('will-navigate', (event, url) => {
  const internalPrefix = isDev ? 'http://localhost:5173' : 'app://';
  if (!url.startsWith(internalPrefix)) {
    event.preventDefault();
    shell.openExternal(url);
  }
});
```

- [ ] **Step 2: 验证**

Run: `npm run typecheck`，然后 `npm run dev`
Expected: 应用内正常路由跳转不受影响（SPA 路由不触发 will-navigate）。

- [ ] **Step 3: Commit**

```powershell
git add src/main.ts
git commit -m "fix(electron): open external links in system browser, block in-window navigation"
```

### Task 11: 修复 electron-builder 打包配置并验证产物

**背景:** 当前 `files` 排除全部 node_modules 后只白名单 3 个包，但 electron-store 的传递依赖（conf、ajv 等）不在白名单 → 打包产物大概率启动即崩。electron-builder 默认会自动收集 production dependencies，删掉手写白名单即可。

**Files:**
- Modify: `moodfm-electron/electron-builder.yml:5-12`

- [ ] **Step 1: 替换 files 配置**

```yaml
files:
  - dist/**/*
  - app/**/*
  - assets/**/*
  - package.json
```

（删除原来的 `"!node_modules/**/*"` 和三行 node_modules 白名单——builder 会自动打入 dependencies 及其传递依赖。）

- [ ] **Step 2: 完整打包**

```powershell
cd moodfm-electron
npm run build
```

Expected: `release/win-unpacked/MoodFM.exe` 与 `release/MoodFM-Setup-1.0.0.exe` 生成，无报错。

- [ ] **Step 3: 验证打包产物能启动（关键步骤，不可跳过）**

```powershell
& .\release\win-unpacked\MoodFM.exe
```

Expected:
1. 窗口正常打开并加载打包的前端（不是白屏、不是崩溃对话框）；
2. 按 `Ctrl+Shift+I` 打开 DevTools，`window.__MOODFM_SERVER_URL` 输出 store.ts 中配置的生产地址；
3. 托盘图标出现。

若崩溃：查看 `%APPDATA%\moodfm-desktop\logs` 或以命令行启动看 stderr，最常见原因是依赖缺失（回查 Step 1）。

- [ ] **Step 4: Commit**

```powershell
git add electron-builder.yml
git commit -m "fix(electron): let electron-builder bundle production deps (was missing transitive deps)"
```

---

# Phase 6: status / deleted 字段拆分

**背景:** `User.status` 同时承担「逻辑删除」（`@TableLogic`，所有查询自动加 `status=1`）和「账号封禁」两种语义，导致 `login()` 里的 `ACCOUNT_DISABLED` 分支是死代码、封禁等同于删除。仅 `User` 实体受影响（已确认其他实体无 `@TableLogic`/`status` 字段）。

**⚠️ 本阶段涉及 DB 迁移，执行前先备份（仓库 `scripts/` 已有备份脚本）。**

### Task 12: V5 迁移 + 实体/配置切换

**Files:**
- Create: `moodfm-backend/src/main/resources/db/migration/V5__split_user_deleted_from_status.sql`
- Modify: `moodfm-backend/src/main/java/com/moodfm/domain/entity/User.java:25-26`
- Modify: `moodfm-backend/src/main/resources/application.yml:54-58`

- [ ] **Step 1: 编写迁移脚本**

创建 `V5__split_user_deleted_from_status.sql`：

```sql
-- 拆分 users.status 的双重语义：
--   status:  1=正常  0=封禁（管理员操作，可恢复）
--   deleted: 1=已注销（逻辑删除，@TableLogic）
ALTER TABLE users
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 1=已注销' AFTER status;

-- 历史上 status=0 的行都是"注销"语义，迁移为 deleted=1
UPDATE users SET deleted = 1 WHERE status = 0;
```

- [ ] **Step 2: User 实体切换 @TableLogic**

修改 `User.java`，把 `status` 上的 `@TableLogic` 移到新字段：

```java
private Integer status; // 1=正常 0=封禁（管理员操作）

@TableLogic
private Integer deleted; // 1=已注销（逻辑删除）
```

- [ ] **Step 3: application.yml 全局逻辑删除配置切换**

修改 `application.yml` 的 `global-config.db-config`：

```yaml
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

- [ ] **Step 4: 编译 + 全量测试**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: BUILD SUCCESS（此时业务行为尚未变化——deleted 字段对旧数据全为 0/1，等价于原 status 过滤）。

- [ ] **Step 5: 对 dev 数据库跑一次迁移验证**

```powershell
docker compose -f docker-compose.dev.yml up -d
```

然后用 IntelliJ 以 dev profile 启动后端（用户日常方式），观察日志。
Expected: 日志出现 `Migrating schema ... to version "5 - split user deleted from status"`，启动成功。

- [ ] **Step 6: Commit**

```powershell
git add moodfm-backend/src
git commit -m "db(flyway): V5 split users.deleted (logic delete) from users.status (ban)"
```

### Task 13: 业务代码适配新语义

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/user/impl/UserServiceImpl.java`（`deleteAccount`）
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/result/ResultCode.java`（ACCOUNT_DISABLED 文案）
- Test: `moodfm-backend/src/test/java/com/moodfm/service/user/impl/UserServiceDeleteAccountTest.java`（新建）

- [ ] **Step 1: 写失败的测试**

创建 `UserServiceDeleteAccountTest.java`：

```java
package com.moodfm.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteAccountTest {

    @Mock
    UserMapper userMapper;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    SetOperations<String, String> setOps;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, null, null, null, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void deleteAccount_usesLogicDelete_notStatusFlag() {
        User user = new User();
        user.setId(42L);
        user.setStatus(1);
        when(setOps.members(anyString())).thenReturn(java.util.Set.of());

        userService.deleteAccount(42L);

        // 走 MyBatis-Plus 逻辑删除（设置 deleted=1），而不是把 status 改成 0
        verify(userMapper).deleteById(42L);
        verify(userMapper, never()).updateById(any(User.class));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test -Dtest=UserServiceDeleteAccountTest`
Expected: FAIL——当前实现走的是 `getById + setStatus(0) + updateById`。

- [ ] **Step 3: 重写 deleteAccount**

替换 `UserServiceImpl.deleteAccount`：

```java
@Override
@CacheEvict(value = "users", key = "#userId")
public void deleteAccount(Long userId) {
    // Revoke all refresh tokens for this user before soft-deleting
    revokeAllUserRefreshTokens(userId);
    // @TableLogic on User.deleted → UPDATE users SET deleted=1 WHERE id=? AND deleted=0
    userMapper.deleteById(userId);
}
```

- [ ] **Step 4: 修正 ACCOUNT_DISABLED 文案**

`ResultCode.java` 中（Task 3 已重写过该文件）：

```java
ACCOUNT_DISABLED(1005, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN),
```

> 说明：拆分后 `login()` / `loginByPhone()` 里的 `user.getStatus() != 1` 分支**变成活代码**（封禁用户能被查出来、然后被拒绝），无需改动这两处逻辑，但文案要从「已注销」改为「已被禁用」。

- [ ] **Step 5: 全量测试**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: 全绿。

- [ ] **Step 6: 手工冒烟（dev 环境）**

后端跑起来后：
1. 注册一个测试账号 → 登录成功；
2. MySQL 中手动 `UPDATE users SET status=0 WHERE id=<测试id>` → 再登录，应返回 1005「账号已被禁用」（而不是 1003 密码错误）；
3. 恢复 `status=1`，调用注销接口 → `deleted` 变 1，再登录返回 1003（查不到用户，表现为密码错误，符合预期）。

- [ ] **Step 7: Commit**

```powershell
git add moodfm-backend/src
git commit -m "fix(user): ban (status) and account deletion (deleted) are now distinct states"
```

> 已知边界（记录，不在本计划解决）：注销用户的 email/phone 仍占用 UNIQUE 索引，重新注册同邮箱会触发 1002。如需释放，应在注销时对 email/phone 做混淆改写（如追加 `#deleted_{id}` 后缀），另立任务。

---

# Phase 7: 快速收尾（性能/安全小修）

### Task 14: 无限时长会话的 Redis marker 封顶 24h

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/player/impl/PlayerServiceImpl.java:113-116`

- [ ] **Step 1: 修改 startRadio 中的 marker 写入**

```java
if (durationMinutes == null) {
    // 无限时长：marker 封顶 24 小时，避免 Redis 中永不过期的 key 无限堆积。
    // 行为变化：连续播放超过 24h 的会话会被判定过期，需重新开台（可接受）。
    redisTemplate.opsForValue().set(ttlKey, "infinite", Duration.ofHours(24));
} else {
```

- [ ] **Step 2: 测试 + Commit**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: 全绿。

```powershell
git add moodfm-backend/src
git commit -m "fix(player): cap infinite-session TTL marker at 24h to stop Redis key leak"
```

### Task 15: UserDetails 短缓存——消除每请求一次的 MySQL 查询

**背景:** `JwtAuthFilter` 每个已认证请求都调 `loadUserByUsername` → `selectById`。加 2 分钟 Caffeine 缓存；封禁/改密的生效延迟 ≤2 分钟，可接受（登出走 Redis 黑名单，仍即时生效）。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/config/CacheConfig.java:19-33`
- Modify: `moodfm-backend/src/main/java/com/moodfm/security/UserDetailsServiceImpl.java:22`
- Test: `moodfm-backend/src/test/java/com/moodfm/security/UserDetailsCacheTest.java`（新建）

- [ ] **Step 1: 写失败的测试**

创建 `UserDetailsCacheTest.java`：

```java
package com.moodfm.security;

import com.moodfm.config.CacheConfig;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {CacheConfig.class, UserDetailsCacheTest.TestConfig.class})
class UserDetailsCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        UserMapper userMapper() {
            return Mockito.mock(UserMapper.class);
        }

        @Bean
        UserDetailsServiceImpl userDetailsService(UserMapper userMapper) {
            return new UserDetailsServiceImpl(userMapper);
        }
    }

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    UserMapper userMapper;

    @Test
    void loadUserByUsername_isCachedWithinTtl() {
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setStatus(1);
        user.setRole("USER");
        when(userMapper.selectById(7L)).thenReturn(user);

        userDetailsService.loadUserByUsername("7");
        userDetailsService.loadUserByUsername("7");

        verify(userMapper, times(1)).selectById(7L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test -Dtest=UserDetailsCacheTest`
Expected: FAIL——`selectById` 被调用 2 次。

- [ ] **Step 3: CacheConfig 增加 userDetails 缓存**

在 `CacheConfig.java` 的 switch 中、`case "embeddings"` 之后加：

```java
case "userDetails" -> Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .build();
```

- [ ] **Step 4: UserDetailsServiceImpl 加注解**

```java
import org.springframework.cache.annotation.Cacheable;

@Override
@Cacheable(value = "userDetails", key = "#account")
public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
```

（抛出 `UsernameNotFoundException` 时不会缓存，无效 token 不会污染缓存。）

- [ ] **Step 5: 测试 + Commit**

Run: `.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test`
Expected: 全绿。

```powershell
git add moodfm-backend/src
git commit -m "perf(auth): cache UserDetails for 2min to avoid per-request MySQL lookup"
```

### Task 16: dev Redis 密码出库 + 轮换

**背景:** `application-dev.yml:10` 把 dev Redis 密码硬编码进了 git 历史。改为占位 + 把真实值收敛到未跟踪的 `docker-compose.dev.yml`/IDE 运行配置，并轮换密码。

**Files:**
- Modify: `moodfm-backend/src/main/resources/application-dev.yml:10`
- Modify: `docker-compose.dev.yml:22`（未跟踪文件，本地改）

- [ ] **Step 1: 生成新密码并更新 docker-compose.dev.yml**

```powershell
python -c "import secrets; print(secrets.token_hex(32))"
```

把输出的新密码替换 `docker-compose.dev.yml:22` 的 `--requirepass` 值。

- [ ] **Step 2: application-dev.yml 去掉硬编码默认值**

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}
      password: ${REDIS_PASSWORD:}   # 真实值放 IDE 运行配置 / 本地环境变量，勿写进 git
```

- [ ] **Step 3: 在 IntelliJ 运行配置中设置 REDIS_PASSWORD**

IntelliJ → Run Configuration → Environment variables 加 `REDIS_PASSWORD=<Step 1 的新密码>`。

- [ ] **Step 4: 重启 dev 容器验证连通**

```powershell
docker compose -f docker-compose.dev.yml up -d --force-recreate redis
```

IntelliJ 启动后端，Expected: 无 Redis AUTH 报错。

> 注意：`docker-compose.dev.yml` 已在 Task 0 加入 `.gitignore`，运行 `git status` 确认它没有出现在待提交列表中。

- [ ] **Step 5: Commit**

```powershell
git add moodfm-backend/src/main/resources/application-dev.yml
git commit -m "chore(security): remove hardcoded dev redis password from tracked config"
```

---

# Phase 8: CI 流水线

### Task 17: GitHub Actions——后端测试 + 前端类型检查/测试/构建

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: 创建 workflow**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Run backend tests
        run: mvn -B -f moodfm-backend/pom.xml test

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: moodfm-frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: moodfm-frontend/package-lock.json
      - run: npm ci
      - run: npm run type-check
      - run: npm test
      - run: npm run build
```

- [ ] **Step 2: 本地预检（CI 会跑的命令本地先全过一遍）**

```powershell
.tools\apache-maven-3.9.9\bin\mvn.cmd -f moodfm-backend\pom.xml test
cd moodfm-frontend
npm run type-check
npm test
npm run build
```

Expected: 全部成功。若 `RateLimitIntegrationTest` 等测试在干净环境需要 Redis，在 backend job 增加：

```yaml
    services:
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
```

- [ ] **Step 3: 提交并观察首次运行**

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions for backend tests and frontend typecheck/test/build"
git push origin feat/electron-shell
```

打开 GitHub → Actions 页确认两个 job 全绿；红了就修到绿为止再合并。

---

# 收尾

- [ ] 全部 Phase 完成后：`git push origin feat/electron-shell`，创建 PR 合入 `main`（或按现有习惯直接合并）。
- [ ] 服务器重新部署：确认 `.env` 中 `SPRING_PROFILES_ACTIVE=prod`，`docker compose up -d --build`，验证：登录、头像显示与上传、电台开台、Swagger 不可访问（`/docs` 404）。

---

# 后续独立计划（本计划不包含，按需另立）

| 主题 | 内容 | 来源 |
|---|---|---|
| PlayerServiceImpl 拆分 | 1280 行上帝类拆为 Recall/Ranking/Persistence/Queue 四个服务；先抽取重复的队列替换逻辑 | Review #14 |
| 前端 Song 模型统一 | 合并 `Song`/`SongVO` 双模型，集中 `toSong()` 映射 | Review #15 |
| WebSocket 重建（如需要） | JWT ChannelInterceptor 鉴权 + `/user/queue` 订阅 + nginx WS 代理，端到端联调 | Review #1 |
| Refresh token 哈希化 | Redis 只存 SHA-256(token)，防 Redis 泄露后的会话接管 | Review #10 |
| 登录锁定按 账号+IP | 防止恶意锁定他人账号 | Review #9 |
| songs 表去重约束 | `title_artist_hash` 唯一索引 + `INSERT ... ON DUPLICATE KEY` | Review #13 |
| 头像 magic bytes 校验 | 不信任客户端 Content-Type | Review #10 |
| 依赖升级 | Spring Boot 3.3 → 3.5（3.3.x OSS 支持已结束）、Electron 33 → 当前稳定版 | 工程化 #3 |
| 邮箱 SMTP 接入 | QQ 邮箱 SMTP 发验证码（已有方案，依赖本计划 Phase 4 的限流先行） | 既有计划 |
