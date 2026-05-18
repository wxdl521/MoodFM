# Dual-Mode Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global search page supporting two modes — keyword search (delegates to bound music platforms via MusicApiClient) and mood/semantic search (embeds the query then queries Qdrant).

**Architecture:** A single `GET /api/search?q=&mode=keyword|mood&limit=20` endpoint dispatches to `SearchServiceImpl`, which for keyword mode fans out to all user-bound platforms via `MusicApiClient.searchSongs()` and merges results, and for mood mode calls `EmbeddingService.embed()` → `QdrantService.searchSimilar()` with a keyword fallback when vector results are sparse. The frontend `Search.vue` renders both modes with a toggle tab and debounced input.

**Tech Stack:** Java 17 / Spring Boot 3, MybatisPlus, Spring Security JWT, `@WebMvcTest` + Mockito, Vue 3 + Pinia, Axios (with existing interceptor that unwraps `R<T>`)

---

## File Map

### Backend — new files
| File | Role |
|------|------|
| `src/main/java/com/moodfm/domain/vo/SearchResultVO.java` | Response VO: mode + song list + optional notice |
| `src/main/java/com/moodfm/service/search/SearchService.java` | Interface with one method |
| `src/main/java/com/moodfm/service/search/impl/SearchServiceImpl.java` | Business logic for both modes |
| `src/main/java/com/moodfm/controller/SearchController.java` | `GET /api/search` endpoint |
| `src/test/java/com/moodfm/controller/SearchControllerTest.java` | Slice test for the controller |

### Backend — modified files
*(none — service/VO layers are fully new)*

### Frontend — new files
| File | Role |
|------|------|
| `src/api/search.ts` | Typed API client for the search endpoint |
| `src/views/Search.vue` | Search page: input, mode tabs, results grid |

### Frontend — modified files
| File | Change |
|------|--------|
| `src/router/index.ts` | Add `/search` route |

---

## Task 1 — SearchResultVO (backend VO)

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/domain/vo/SearchResultVO.java`

- [ ] **Step 1: Create the VO**

```java
package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SearchResultVO {
    private String mode;           // "keyword" or "mood"
    private String query;
    private List<SongVO> songs;
    private String notice;         // e.g. "未绑定平台，结果为空" — nullable
}
```

- [ ] **Step 2: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/domain/vo/SearchResultVO.java
git commit -m "feat(search): add SearchResultVO"
```

---

## Task 2 — SearchService interface

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/service/search/SearchService.java`

- [ ] **Step 1: Create the interface**

```java
package com.moodfm.service.search;

import com.moodfm.domain.vo.SearchResultVO;

public interface SearchService {
    /**
     * @param userId  authenticated user ID
     * @param query   search string (keyword or mood description)
     * @param mode    "keyword" | "mood"
     * @param limit   max results per platform (capped at 50)
     */
    SearchResultVO search(Long userId, String query, String mode, int limit);
}
```

- [ ] **Step 2: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/service/search/SearchService.java
git commit -m "feat(search): add SearchService interface"
```

---

## Task 3 — SearchServiceImpl (business logic)

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/service/search/impl/SearchServiceImpl.java`

Depends on existing beans:
- `MusicApiClient` — `searchSongs(platform, keywords, limit)` returns `JsonNode`
- `MusicResponseParser` — `parseSongs(data, platform)` returns `List<SongVO>`
- `PlatformBindingMapper` — query by userId to find bound platforms
- `EmbeddingService` — `embed(text)` returns `float[]`
- `QdrantService` — `searchSimilar(vector, limit)` returns `List<Long>` (song IDs)
- `SongMapper` — selectById to hydrate song details into `SongVO`
- `Song` entity has fields: `id`, `title`, `artist`, `album`, `durationSeconds`, `coverUrl`, `platform`, `platformSongId`

- [ ] **Step 1: Create SearchServiceImpl**

```java
package com.moodfm.service.search.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SearchResultVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformBindingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.search.SearchService;
import com.moodfm.service.vector.QdrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final MusicApiClient musicApiClient;
    private final PlatformBindingMapper bindingMapper;
    private final AesUtil aesUtil;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final SongMapper songMapper;

    private static final int MAX_LIMIT = 50;

    @Override
    public SearchResultVO search(Long userId, String query, String mode, int limit) {
        int cap = Math.min(limit, MAX_LIMIT);
        return "mood".equalsIgnoreCase(mode)
                ? moodSearch(userId, query, cap)
                : keywordSearch(userId, query, cap);
    }

    // ── Mode A: keyword ──────────────────────────────────────────────

    private SearchResultVO keywordSearch(Long userId, String query, int limit) {
        List<PlatformBinding> bindings = activeBindings(userId);
        if (bindings.isEmpty()) {
            return SearchResultVO.builder()
                    .mode("keyword")
                    .query(query)
                    .songs(List.of())
                    .notice("请先在「平台绑定」中接入音乐平台")
                    .build();
        }

        // Dedup by platformSongId to avoid duplicates when searching multiple platforms
        Map<String, SongVO> seen = new LinkedHashMap<>();
        for (PlatformBinding b : bindings) {
            try {
                String cookie = aesUtil.decrypt(b.getEncryptedCookie());
                JsonNode raw = musicApiClient.searchSongs(b.getPlatform(), query, limit);
                List<SongVO> songs = MusicResponseParser.parseSongs(raw, b.getPlatform());
                for (SongVO s : songs) {
                    seen.putIfAbsent(b.getPlatform() + ":" + s.getPlatformSongId(), s);
                }
            } catch (Exception e) {
                log.warn("Keyword search failed for platform {}: {}", b.getPlatform(), e.getMessage());
            }
        }

        return SearchResultVO.builder()
                .mode("keyword")
                .query(query)
                .songs(new ArrayList<>(seen.values()))
                .build();
    }

    // ── Mode C: mood / semantic ──────────────────────────────────────

    private SearchResultVO moodSearch(Long userId, String query, int limit) {
        List<SongVO> results = new ArrayList<>();
        try {
            float[] vector = embeddingService.embed(query);
            List<Long> songIds = qdrantService.searchSimilar(vector, limit);
            for (Long id : songIds) {
                Song song = songMapper.selectById(id);
                if (song != null) {
                    results.add(toVO(song));
                }
            }
        } catch (Exception e) {
            log.warn("Mood search (Qdrant) failed: {}", e.getMessage());
        }

        // Fallback: if vector search returned fewer than 5 songs, supplement with keyword search
        if (results.size() < 5) {
            SearchResultVO kw = keywordSearch(userId, query, limit - results.size());
            for (SongVO s : kw.getSongs()) {
                String key = s.getPlatform() + ":" + s.getPlatformSongId();
                boolean alreadyPresent = results.stream()
                        .anyMatch(r -> key.equals(r.getPlatform() + ":" + r.getPlatformSongId()));
                if (!alreadyPresent) results.add(s);
            }
        }

        return SearchResultVO.builder()
                .mode("mood")
                .query(query)
                .songs(results)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private List<PlatformBinding> activeBindings(Long userId) {
        return bindingMapper.selectList(
                new LambdaQueryWrapper<PlatformBinding>()
                        .eq(PlatformBinding::getUserId, userId)
                        .eq(PlatformBinding::getValid, true)
        );
    }

    private SongVO toVO(Song song) {
        return SongVO.builder()
                .id(song.getId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .album(song.getAlbum())
                .durationSeconds(song.getDurationSeconds())
                .coverUrl(song.getCoverUrl())
                .platform(song.getPlatform())
                .platformSongId(song.getPlatformSongId())
                .build();
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd moodfm-backend
mvn compile -q
```

Expected: `BUILD SUCCESS` (no output on success with `-q`).

- [ ] **Step 3: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/service/search/
git commit -m "feat(search): implement SearchServiceImpl — keyword + mood modes"
```

---

## Task 4 — SearchController

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/controller/SearchController.java`

- [ ] **Step 1: Create the controller**

```java
package com.moodfm.controller;

import com.moodfm.common.result.R;
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
            return R.ok(SearchResultVO.builder()
                    .mode(mode).query("").songs(List.of()).build());
        }
        return R.ok(searchService.search(uid(ud), q.trim(), mode, limit));
    }
}
```

> **Note:** add `import java.util.List;` to the controller.

- [ ] **Step 2: Compile check**

```bash
cd moodfm-backend
mvn compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/controller/SearchController.java
git commit -m "feat(search): add SearchController GET /api/search"
```

---

## Task 5 — SearchController slice test

**Files:**
- Create: `moodfm-backend/src/test/java/com/moodfm/controller/SearchControllerTest.java`

Pattern: mirrors existing `AuthControllerTest` — `@WebMvcTest` + `@MockBean` service + `addFilters = false`.

- [ ] **Step 1: Write the failing test**

```java
package com.moodfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.GlobalExceptionHandler;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.domain.vo.SearchResultVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SearchService searchService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Test
    void search_keywordMode_returnsSongs() throws Exception {
        SongVO song = SongVO.builder()
                .id(1L).title("晴天").artist("周杰伦")
                .platform("netease").platformSongId("186001").build();
        SearchResultVO result = SearchResultVO.builder()
                .mode("keyword").query("晴天").songs(List.of(song)).build();

        when(searchService.search(anyLong(), eq("晴天"), eq("keyword"), eq(20)))
                .thenReturn(result);

        mockMvc.perform(get("/api/search").param("q", "晴天"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("keyword"))
                .andExpect(jsonPath("$.data.songs[0].title").value("晴天"));
    }

    @Test
    void search_emptyQuery_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs").isArray())
                .andExpect(jsonPath("$.data.songs").isEmpty());
    }

    @Test
    void search_moodMode_passesCorrectMode() throws Exception {
        SearchResultVO result = SearchResultVO.builder()
                .mode("mood").query("深夜忧郁").songs(List.of()).build();

        when(searchService.search(anyLong(), eq("深夜忧郁"), eq("mood"), eq(20)))
                .thenReturn(result);

        mockMvc.perform(get("/api/search").param("q", "深夜忧郁").param("mode", "mood"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("mood"));
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (SearchController not yet wired to security context mock)**

```bash
cd moodfm-backend
mvn test -pl . -Dtest=SearchControllerTest -q
```

Expected: Failing because `@AuthenticationPrincipal` resolves to null → `uid()` throws `NumberFormatException`.

- [ ] **Step 3: Fix — inject a mock principal**

The existing test pattern in `AuthControllerTest` uses `addFilters = false` and doesn't inject a principal. Since `SearchController.uid()` calls `Long.parseLong(ud.getUsername())`, we need to provide a `UserDetails` principal for non-empty queries. Use `@WithMockUser(username = "1")` from Spring Security test.

Add `spring-security-test` — it is already on the classpath via `spring-boot-starter-security`. Update the two tests that hit the real endpoint:

```java
// Add import at top of SearchControllerTest:
import org.springframework.security.test.context.support.WithMockUser;

// Annotate the two non-empty-query tests:
@Test
@WithMockUser(username = "1")
void search_keywordMode_returnsSongs() throws Exception { ... }

@Test
@WithMockUser(username = "1")
void search_moodMode_passesCorrectMode() throws Exception { ... }
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
cd moodfm-backend
mvn test -pl . -Dtest=SearchControllerTest -q
```

Expected: `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add moodfm-backend/src/test/java/com/moodfm/controller/SearchControllerTest.java
git commit -m "test(search): add SearchControllerTest slice tests"
```

---

## Task 6 — Frontend API client

**Files:**
- Create: `moodfm-frontend/src/api/search.ts`

- [ ] **Step 1: Create search.ts**

```typescript
import api from './client'
import type { SongVO } from '@/types'

export type SearchMode = 'keyword' | 'mood'

export interface SearchResult {
  mode: SearchMode
  query: string
  songs: SongVO[]
  notice?: string
}

export const searchApi = {
  search(q: string, mode: SearchMode = 'keyword', limit = 20): Promise<SearchResult> {
    return api.get('/search', { params: { q, mode, limit } })
  },
}
```

> **Note:** `SongVO` must exist in `src/types/index.ts`. Check what's there — if `Song` is already defined (it is, in `song.ts`'s inline interface), add a shared `SongVO` type to `src/types/index.ts` or reuse the inline `Song` shape. The backend `SongVO` fields are: `id, title, artist, album, durationSeconds, coverUrl, platform, platformSongId, playUrl, recommendReason`. Map the existing `Song` type in `song.ts` to this shape by updating `src/types/index.ts`:

```typescript
// Add to src/types/index.ts (alongside existing types):
export interface SongVO {
  id: number
  title: string
  artist: string
  album?: string
  durationSeconds?: number
  coverUrl?: string
  platform?: string
  platformSongId?: string
  playUrl?: string
  recommendReason?: string
}
```

- [ ] **Step 2: Check types compile**

```bash
cd moodfm-frontend
npx vue-tsc --noEmit 2>&1 | head -30
```

Expected: no errors on the new files.

- [ ] **Step 3: Commit**

```bash
git add moodfm-frontend/src/api/search.ts moodfm-frontend/src/types/index.ts
git commit -m "feat(search): add searchApi client and SongVO type"
```

---

## Task 7 — Search.vue page

**Files:**
- Create: `moodfm-frontend/src/views/Search.vue`

Design direction: **editorial / utilitarian** — consistent with the existing ink-on-paper MoodFM aesthetic. Two-tab mode switcher at the top, full-width search bar, results in a list (not grid — search results are scanned linearly). Empty / loading / no-result states.

- [ ] **Step 1: Create Search.vue**

```vue
<template>
  <div class="search-page">
    <div class="mood-blob drift" style="width:600px;height:600px;right:-180px;top:-180px;opacity:0.25;z-index:0;" />

    <NavBar />

    <div class="search-wrap">
      <!-- Mode tabs -->
      <div class="mode-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.mode"
          class="mode-tab"
          :class="{ 'mode-tab--active': activeMode === tab.mode }"
          @click="switchMode(tab.mode)"
        >
          <span class="mono" style="font-size:10px;letter-spacing:.14em;margin-right:6px;">{{ tab.en }}</span>
          {{ tab.label }}
        </button>
      </div>

      <!-- Search input -->
      <div class="search-bar-wrap">
        <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="7.5"/><line x1="16.8" y1="16.8" x2="22" y2="22"/>
        </svg>
        <input
          ref="inputEl"
          v-model="query"
          class="search-input"
          :placeholder="activePlaceholder"
          autocomplete="off"
          @keydown.esc="query = ''"
        />
        <button v-if="query" class="search-clear" @click="query = ''; inputEl?.focus()">×</button>
      </div>

      <!-- Mode hint -->
      <div class="meta search-hint">{{ activeHint }}</div>

      <!-- Results -->
      <div v-if="loading" class="search-state">
        <div class="mono" style="font-size:12px;letter-spacing:.14em;color:var(--ink-3)">SEARCHING…</div>
      </div>

      <div v-else-if="query.length >= 2 && songs.length === 0 && !loading" class="search-state">
        <div style="font-family:var(--serif-en);font-style:italic;font-size:28px;color:var(--ink-3)">Nothing.</div>
        <div v-if="notice" class="meta" style="margin-top:8px;color:var(--ink-3)">· {{ notice }}</div>
        <div v-else class="meta" style="margin-top:8px;color:var(--ink-3)">· 换个词试试</div>
      </div>

      <div v-else-if="songs.length > 0" class="results-list">
        <div
          v-for="(song, i) in songs"
          :key="song.platform + ':' + song.platformSongId"
          class="result-row"
          @click="playSong(song)"
        >
          <div class="result-index mono">{{ String(i + 1).padStart(2, '0') }}</div>
          <img v-if="song.coverUrl" :src="song.coverUrl" class="result-cover" :alt="song.title" />
          <div v-else class="result-cover result-cover--empty" />
          <div class="result-info">
            <div class="result-title">{{ song.title }}</div>
            <div class="meta result-sub">{{ song.artist }}<span v-if="song.album"> · {{ song.album }}</span></div>
          </div>
          <div class="result-meta">
            <span class="mono" style="font-size:10px;color:var(--ink-3);">{{ song.platform?.toUpperCase() }}</span>
            <span v-if="song.durationSeconds" class="mono" style="font-size:11px;color:var(--ink-3);margin-left:12px;">{{ formatDur(song.durationSeconds) }}</span>
          </div>
        </div>
      </div>

      <div v-else-if="!query" class="search-state">
        <div style="font-family:var(--serif-en);font-style:italic;font-size:28px;color:var(--ink-3)">Search.</div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRadioStore } from '@/stores/radio'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { searchApi } from '@/api/search'
import type { SearchMode } from '@/api/search'
import type { SongVO } from '@/types'

const router = useRouter()
const radio = useRadioStore()

type TabDef = { mode: SearchMode; label: string; en: string; placeholder: string; hint: string }

const tabs: TabDef[] = [
  {
    mode: 'keyword',
    label: '关键词',
    en: 'KW',
    placeholder: '歌名、艺人、专辑…',
    hint: '精确搜索 · 结果来自你绑定的平台',
  },
  {
    mode: 'mood',
    label: '心情',
    en: 'MOOD',
    placeholder: '描述此刻的感受，比如：深夜忧郁、想要振奋…',
    hint: '语义搜索 · 用情绪找到对应的歌',
  },
]

const activeMode = ref<SearchMode>('keyword')
const activeTab = computed(() => tabs.find(t => t.mode === activeMode.value)!)
const activePlaceholder = computed(() => activeTab.value.placeholder)
const activeHint = computed(() => activeTab.value.hint)

const query = ref('')
const loading = ref(false)
const songs = ref<SongVO[]>([])
const notice = ref<string | undefined>()
const inputEl = ref<HTMLInputElement | null>(null)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch([query, activeMode], () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (query.value.length < 2) {
    songs.value = []
    notice.value = undefined
    return
  }
  loading.value = true
  debounceTimer = setTimeout(async () => {
    try {
      const res = await searchApi.search(query.value.trim(), activeMode.value)
      songs.value = res.songs
      notice.value = res.notice
    } catch {
      songs.value = []
    } finally {
      loading.value = false
    }
  }, 400)
})

function switchMode(m: SearchMode) {
  activeMode.value = m
  songs.value = []
  notice.value = undefined
  inputEl.value?.focus()
}

function formatDur(secs: number): string {
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

async function playSong(song: SongVO) {
  radio.setMoodText(song.title)
  try {
    await radio.startRadio({ moodText: song.title })
  } catch {}
  router.push('/player')
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  padding-bottom: 100px;
}

.search-wrap {
  position: relative;
  z-index: 2;
  max-width: 760px;
  margin: 0 auto;
  padding: 40px 56px 80px;
}

@media (max-width: 768px) {
  .search-wrap { padding: 24px 22px 80px; }
}

/* Mode tabs */
.mode-tabs {
  display: flex;
  gap: 0;
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 3px;
  width: fit-content;
  margin-bottom: 24px;
}

.mode-tab {
  padding: 7px 18px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--serif-cn);
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
}

.mode-tab--active {
  background: var(--ink);
  color: var(--bg);
}

/* Search bar */
.search-bar-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 16px;
  color: var(--ink-3);
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 56px;
  padding: 0 48px;
  font-family: var(--serif-cn);
  font-size: 18px;
  background: var(--paper);
  border: 1px solid var(--rule);
  border-radius: 16px;
  color: var(--ink);
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: var(--ink-3);
}

.search-clear {
  position: absolute;
  right: 14px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: var(--bg-2);
  color: var(--ink-2);
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-hint {
  margin-top: 10px;
  margin-bottom: 32px;
  color: var(--ink-3);
}

/* State placeholders */
.search-state {
  text-align: center;
  padding: 60px 0;
}

/* Results */
.results-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.12s;
}

.result-row:hover {
  background: var(--bg-2);
}

.result-index {
  width: 22px;
  text-align: right;
  font-size: 11px;
  color: var(--ink-3);
  flex-shrink: 0;
}

.result-cover {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.result-cover--empty {
  background: var(--bg-2);
  border: 1px solid var(--rule);
}

.result-info {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-family: var(--serif-cn);
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-sub {
  margin-top: 2px;
  color: var(--ink-2);
}

.result-meta {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add moodfm-frontend/src/views/Search.vue
git commit -m "feat(search): add Search.vue with dual-mode UI"
```

---

## Task 8 — Register /search route

**Files:**
- Modify: `moodfm-frontend/src/router/index.ts` (line ~11, after `/bind`)

- [ ] **Step 1: Add the route**

In `src/router/index.ts`, add after the `/bind` line:

```typescript
{ path: '/search', component: () => import('@/views/Search.vue'), meta: { requiresAuth: true } },
```

- [ ] **Step 2: Type-check**

```bash
cd moodfm-frontend
npx vue-tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 3: Smoke test in browser**

```bash
cd moodfm-frontend
npm run dev
```

Open `http://localhost:5173/search`. Verify:
- Page loads, NavBar present
- Mode tab switches between 关键词 / 心情
- Typing < 2 chars shows the "Search." placeholder
- Typing 2+ chars triggers loading state (backend may not be running — that's fine)
- ESC clears input
- Clicking ×  clears input

- [ ] **Step 4: Commit**

```bash
git add moodfm-frontend/src/router/index.ts
git commit -m "feat(search): register /search route"
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|-------------|------|
| Mode A: keyword → platform search | Task 3 (SearchServiceImpl.keywordSearch) |
| Mode C: mood → Qdrant vector search | Task 3 (SearchServiceImpl.moodSearch) |
| Mode C fallback to keyword when < 5 results | Task 3 (moodSearch tail) |
| No bound platform → helpful notice | Task 3 (keywordSearch empty-bindings branch) |
| Backend endpoint `GET /api/search` | Task 4 (SearchController) |
| Empty query guard | Task 4 (SearchController blank check) |
| Backend tests | Task 5 (SearchControllerTest) |
| Frontend API client | Task 6 (search.ts) |
| Search UI with mode toggle | Task 7 (Search.vue) |
| Route registration | Task 8 (router/index.ts) |
| Debounce (400 ms, min 2 chars) | Task 7 (watch with setTimeout) |
| NavBar search icon already routes to /search | Already wired in NavBar.vue |

### Placeholder scan

No TBDs, no "implement later", no "similar to Task N" — all tasks include complete code.

### Type consistency

- `SongVO` defined in Task 6 (`src/types/index.ts`) and referenced in Task 7 (`Search.vue`) ✓
- `SearchMode`, `SearchResult` defined in Task 6 and imported in Task 7 ✓
- `SearchResultVO.songs` is `List<SongVO>` in Task 1, populated in Task 3 ✓
- `SearchService.search(Long, String, String, int)` in Task 2 matches call in Task 4 (`searchService.search(uid(ud), q.trim(), mode, limit)`) ✓
- `SearchControllerTest` mocks `searchService.search(anyLong(), eq(...), eq(...), eq(20))` matching the controller's default `limit=20` ✓
