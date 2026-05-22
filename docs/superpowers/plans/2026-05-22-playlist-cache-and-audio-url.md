# Playlist Cache + Cross-platform Audio URL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) Cache playlist list data in a Pinia store so returning to `/playlists` is instant; (2) resolve playable audio URLs from the bound platform for any song in a playlist.

**Architecture:** A new `playlistStore` holds the list + smart-playlist data with a 5-minute TTL; `PlaylistList.vue` reads from the store instead of fetching on every mount. For audio URLs, a new backend endpoint `GET /api/songs/{id}/audio-url` (+ batch `POST /api/songs/batch-audio-urls`) looks up the `platform_song_mapping` record, decrypts the user's cookie, calls `MusicApiClient.getSongUrl()`, and returns the URL; `Playlist.vue` calls this before handing the song to the player.

**Tech Stack:** Vue 3 + Pinia (frontend cache), Spring Boot + MyBatis-Plus (backend), existing `MusicApiClient.getSongUrl` / `getSongUrls` (already implemented).

---

## Part A — Playlist Store Cache (Frontend only)

### Files

| Action | Path |
|--------|------|
| Create | `moodfm-frontend/src/stores/playlist.ts` |
| Modify | `moodfm-frontend/src/views/library/PlaylistList.vue` |

---

### Task 1: Create Pinia playlist store

**Files:**
- Create: `moodfm-frontend/src/stores/playlist.ts`

- [ ] **Step 1: Write the store**

```typescript
// moodfm-frontend/src/stores/playlist.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { playlistApi, type SmartPlaylistSummary } from '@/api/playlist'

const TTL_MS = 5 * 60 * 1000

export interface PlaylistItem {
  id: string
  t: string
  en: string
  n: number
  m: number
  src: string
  mood: string
  desc: string
  ai: boolean
}

function toPlatformLabel(p: string): string {
  return p === 'netease' ? '网易云' : p === 'qqmusic' ? 'QQ' : p
}

export const usePlaylistStore = defineStore('playlist', () => {
  const lists = ref<PlaylistItem[]>([])
  const smartPlaylists = ref<SmartPlaylistSummary[]>([])
  const loadedAt = ref<number | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  function isStale(): boolean {
    return loadedAt.value === null || Date.now() - loadedAt.value > TTL_MS
  }

  async function load(force = false) {
    if (!force && !isStale() && lists.value.length > 0) return
    loading.value = true
    error.value = null
    try {
      const [playlistData, smartData] = await Promise.all([
        playlistApi.list(),
        playlistApi.listSmart().catch(() => []),
      ])
      lists.value = playlistData.map(pl => ({
        id: pl.id,
        t: pl.name,
        en: pl.name.toUpperCase().slice(0, 16),
        n: pl.trackCount,
        m: Math.round(pl.trackCount * 3.5),
        src: toPlatformLabel(pl.platform),
        mood: 'calm',
        desc: pl.description ?? '',
        ai: false,
      }))
      smartPlaylists.value = smartData
      loadedAt.value = Date.now()
    } catch (e: any) {
      error.value = e?.message ?? '加载失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    loadedAt.value = null
  }

  return { lists, smartPlaylists, loading, error, isStale, load, invalidate }
})
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd moodfm-frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add moodfm-frontend/src/stores/playlist.ts
git commit -m "feat: add Pinia playlist store with 5-minute TTL cache"
```

---

### Task 2: Wire PlaylistList.vue to the store

**Files:**
- Modify: `moodfm-frontend/src/views/library/PlaylistList.vue`

Current `<script setup>` fetches directly in `onMounted`. Replace with store reads.

- [ ] **Step 1: Replace the script block**

Replace the entire `<script setup lang="ts">` block (lines 1–44) with:

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { usePlaylistStore } from '@/stores/playlist'

const router = useRouter()
const store = usePlaylistStore()

onMounted(() => store.load())
</script>
```

- [ ] **Step 2: Update template bindings**

In the `<template>`, replace every reference to the old local refs:

| Old | New |
|-----|-----|
| `loading` | `store.loading` |
| `error` | `store.error` |
| `smartPlaylists` | `store.smartPlaylists` |
| `lists` | `store.lists` |
| `tab` | keep local (it's UI-only state) — add `const tab = ref('我的')` back at top of script |

The full updated `<script setup>`:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { usePlaylistStore } from '@/stores/playlist'

const router = useRouter()
const tab = ref('我的')
const store = usePlaylistStore()

onMounted(() => store.load())
</script>
```

- [ ] **Step 3: Verify in browser**

Run `npm run dev`, navigate to `/playlists`, wait for load. Navigate away to `/home`, then back to `/playlists`. Second visit should show data instantly (no spinner), because `store.load()` short-circuits when data is fresh.

- [ ] **Step 4: Commit**

```bash
git add moodfm-frontend/src/views/library/PlaylistList.vue
git commit -m "feat: use playlistStore in PlaylistList.vue — instant return navigation"
```

---

## Part B — Cross-platform Audio URL (Backend + Frontend)

### Files

| Action | Path |
|--------|------|
| Modify | `moodfm-backend/src/main/java/com/moodfm/service/song/SongService.java` |
| Modify | `moodfm-backend/src/main/java/com/moodfm/service/song/impl/SongServiceImpl.java` |
| Modify | `moodfm-backend/src/main/java/com/moodfm/controller/SongController.java` |
| Create | `moodfm-backend/src/test/java/com/moodfm/controller/SongControllerAudioUrlTest.java` |
| Modify | `moodfm-frontend/src/api/song.ts` |
| Modify | `moodfm-frontend/src/views/library/Playlist.vue` |

---

### Task 3: Add getAudioUrl / getAudioUrls to SongService

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/song/SongService.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/song/impl/SongServiceImpl.java`

- [ ] **Step 1: Add methods to the interface**

In `SongService.java`, append two method signatures after `getLyrics`:

```java
String getAudioUrl(Long userId, Long songId);

Map<Long, String> getAudioUrls(Long userId, List<Long> songIds);
```

Full updated `SongService.java`:

```java
package com.moodfm.service.song;

import com.moodfm.domain.vo.LyricLineVO;
import com.moodfm.domain.vo.SongVO;

import java.util.List;
import java.util.Map;

public interface SongService {
    List<SongVO> getLikedSongs(Long userId);
    boolean toggleLike(Long userId, Long songId);
    boolean isLiked(Long userId, Long songId);
    SongVO getSongDetail(Long userId, Long songId);
    List<SongVO> getSimilarSongs(Long userId, Long songId);
    List<LyricLineVO> getLyrics(Long userId, Long songId);
    String getAudioUrl(Long userId, Long songId);
    Map<Long, String> getAudioUrls(Long userId, List<Long> songIds);
}
```

- [ ] **Step 2: Implement getAudioUrl in SongServiceImpl**

Add the following method to `SongServiceImpl.java` after the `getLyrics` method:

```java
@Override
public String getAudioUrl(Long userId, Long songId) {
    PlatformSongMapping mapping = platformSongMappingMapper.selectOne(
            new LambdaQueryWrapper<PlatformSongMapping>()
                    .eq(PlatformSongMapping::getSongId, songId)
                    .last("LIMIT 1"));
    if (mapping == null) {
        log.warn("No platform mapping for song {}", songId);
        return null;
    }
    try {
        PlatformBinding binding = bindingService.getValidBinding(userId, mapping.getPlatform());
        if (binding == null) {
            binding = bindingService.getDefaultBinding(userId);
        }
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String url = musicApiClient.getSongUrl(mapping.getPlatform(), mapping.getPlatformSongId(), cookie);
        if (url == null || url.isBlank()) {
            log.warn("Empty audio URL for song {} platform={}", songId, mapping.getPlatform());
        }
        return url;
    } catch (Exception e) {
        log.warn("getAudioUrl failed for song {} user {}", songId, userId, e);
        return null;
    }
}
```

Note: `SongServiceImpl` already imports `MusicApiClient`? Check — the field is named `musicApiClient` in the existing class. If it only has `songApiClient`, use that and call the same `getSongUrl` method (it delegates through). Verify by checking which field is injected.

Actually, looking at the existing `SongServiceImpl`, the field is `songApiClient` (type `SongApiClient`), not `musicApiClient` (type `MusicApiClient`). Check `SongApiClient.java` to see if `getSongUrl` is available there, or inject `MusicApiClient` directly.

- [ ] **Step 3: Check SongApiClient for getSongUrl**

```bash
grep -n "getSongUrl\|MusicApiClient\|musicApiClient" \
  moodfm-backend/src/main/java/com/moodfm/client/music/SongApiClient.java \
  moodfm-backend/src/main/java/com/moodfm/service/song/impl/SongServiceImpl.java
```

**If `SongApiClient` has `getSongUrl`**: use `songApiClient.getSongUrl(...)` in the impl.

**If only `MusicApiClient` has it**: add `private final MusicApiClient musicApiClient;` to `SongServiceImpl`'s `@RequiredArgsConstructor` field list (Lombok injects it automatically).

- [ ] **Step 4: Implement getAudioUrls in SongServiceImpl**

Add immediately after `getAudioUrl`:

```java
@Override
public Map<Long, String> getAudioUrls(Long userId, List<Long> songIds) {
    if (songIds == null || songIds.isEmpty()) return Map.of();

    List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
            new LambdaQueryWrapper<PlatformSongMapping>()
                    .in(PlatformSongMapping::getSongId, songIds));
    if (mappings.isEmpty()) return Map.of();

    Map<String, List<PlatformSongMapping>> byPlatform = mappings.stream()
            .collect(Collectors.groupingBy(PlatformSongMapping::getPlatform));

    Map<Long, String> result = new java.util.HashMap<>();

    for (Map.Entry<String, List<PlatformSongMapping>> entry : byPlatform.entrySet()) {
        String platform = entry.getKey();
        List<PlatformSongMapping> platformMappings = entry.getValue();
        try {
            PlatformBinding binding = bindingService.getValidBinding(userId, platform);
            if (binding == null) binding = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());

            List<String> platformIds = platformMappings.stream()
                    .map(PlatformSongMapping::getPlatformSongId)
                    .collect(Collectors.toList());
            Map<String, String> urlMap = musicApiClient.getSongUrls(platform, platformIds, cookie);

            for (PlatformSongMapping m : platformMappings) {
                String url = urlMap.get(m.getPlatformSongId());
                if (url != null && !url.isBlank()) {
                    result.put(m.getSongId(), url);
                }
            }
        } catch (Exception e) {
            log.warn("getAudioUrls batch failed for platform {}", platform, e);
        }
    }
    return result;
}
```

- [ ] **Step 5: Build to confirm no compile errors**

```bash
cd moodfm-backend
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/service/song/SongService.java \
        moodfm-backend/src/main/java/com/moodfm/service/song/impl/SongServiceImpl.java
git commit -m "feat: add getAudioUrl / getAudioUrls to SongService"
```

---

### Task 4: Add controller endpoints + test

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/controller/SongController.java`
- Create: `moodfm-backend/src/test/java/com/moodfm/controller/SongControllerAudioUrlTest.java`

- [ ] **Step 1: Write the failing tests first**

Create `SongControllerAudioUrlTest.java`:

```java
package com.moodfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.GlobalExceptionHandler;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.service.song.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SongController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SongControllerAudioUrlTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SongService songService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Test
    @WithMockUser(username = "1")
    void getAudioUrl_returnsUrl_whenResolved() throws Exception {
        when(songService.getAudioUrl(1L, 42L))
                .thenReturn("https://cdn.example.com/song/42.mp3");

        mockMvc.perform(get("/api/songs/42/audio-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/song/42.mp3"));
    }

    @Test
    @WithMockUser(username = "1")
    void getAudioUrl_returns404_whenNotResolved() throws Exception {
        when(songService.getAudioUrl(1L, 99L)).thenReturn(null);

        mockMvc.perform(get("/api/songs/99/audio-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser(username = "1")
    void batchAudioUrls_returnsMap() throws Exception {
        when(songService.getAudioUrls(eq(1L), anyList()))
                .thenReturn(Map.of(1L, "https://cdn.example.com/1.mp3",
                                   2L, "https://cdn.example.com/2.mp3"));

        mockMvc.perform(post("/api/songs/batch-audio-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(1, 2, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['1']").value("https://cdn.example.com/1.mp3"))
                .andExpect(jsonPath("$.data['2']").value("https://cdn.example.com/2.mp3"));
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (endpoints don't exist yet)**

```bash
cd moodfm-backend
./mvnw test -pl . -Dtest=SongControllerAudioUrlTest -q
```

Expected: compilation error or 404 from MockMvc (endpoints not mapped yet).

- [ ] **Step 3: Add endpoints to SongController**

Append to `SongController.java` before the closing `}`:

```java
@Operation(summary = "获取歌曲播放地址")
@GetMapping("/{id}/audio-url")
public R<Map<String, String>> getAudioUrl(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails ud) {
    String url = songService.getAudioUrl(uid(ud), id);
    if (url == null || url.isBlank()) {
        return R.fail(404, "暂无可用播放地址");
    }
    Map<String, String> body = new java.util.HashMap<>();
    body.put("url", url);
    return R.ok(body);
}

@Operation(summary = "批量获取歌曲播放地址")
@PostMapping("/batch-audio-urls")
public R<Map<Long, String>> batchAudioUrls(@RequestBody List<Long> songIds,
                                            @AuthenticationPrincipal UserDetails ud) {
    return R.ok(songService.getAudioUrls(uid(ud), songIds));
}
```

Also add the missing import at the top of `SongController.java` if not present:

```java
import java.util.Map;
import java.util.List;
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./mvnw test -pl . -Dtest=SongControllerAudioUrlTest -q
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add moodfm-backend/src/main/java/com/moodfm/controller/SongController.java \
        moodfm-backend/src/test/java/com/moodfm/controller/SongControllerAudioUrlTest.java
git commit -m "feat: add GET /api/songs/{id}/audio-url and POST /api/songs/batch-audio-urls"
```

---

### Task 5: Frontend — wire up audio URL to Playlist.vue

**Files:**
- Modify: `moodfm-frontend/src/api/song.ts`
- Modify: `moodfm-frontend/src/views/library/Playlist.vue`

- [ ] **Step 1: Add API methods to song.ts**

Replace the entire `moodfm-frontend/src/api/song.ts`:

```typescript
import api from './client'
import type { Platform } from '@/types'

export interface SongDetail {
  id: string
  title: string
  artist: string
  album?: string
  platform: Platform
  platformSongId: string
  duration: number
  coverUrl?: string
  audioUrl?: string
}

export interface LyricLine { time: number; text: string }

export const songApi = {
  get:            (id: string): Promise<SongDetail>             => api.get(`/songs/${id}`),
  lyrics:         (id: string): Promise<LyricLine[]>            => api.get(`/songs/${id}/lyrics`),
  getAudioUrl:    (id: string): Promise<{ url: string | null }>  => api.get(`/songs/${id}/audio-url`),
  batchAudioUrls: (ids: number[]): Promise<Record<string, string>> =>
                                                                  api.post('/songs/batch-audio-urls', ids),
}
```

- [ ] **Step 2: Update TrackItem interface and track mapping in Playlist.vue**

In `Playlist.vue`, update the `TrackItem` interface (line 15) and the track mapping inside `onMounted` to preserve `id` and `platformSongId`.

Change the interface from:
```typescript
interface TrackItem { t: string; a: string; al: string; m: string; mood: string; liked: boolean }
```

To:
```typescript
interface TrackItem {
  id: string
  platformSongId: string
  t: string
  a: string
  al: string
  durationSecs: number
  m: string
  mood: string
  liked: boolean
}
```

Change the `.map()` inside `onMounted`:
```typescript
tracks.value = (data.tracks ?? []).map(s => ({
  id: String(s.id),
  platformSongId: s.platformSongId ?? '',
  t: s.title,
  a: s.artist,
  al: s.album ?? '',
  durationSecs: s.duration,
  m: formatDuration(s.duration),
  mood: 'CALM',
  liked: false,
}))
likedMap.value = Object.fromEntries(tracks.value.map((_, i) => [i, false]))
```

- [ ] **Step 3: Add playSong and playAll functions to Playlist.vue**

Add the following imports at the top of `<script setup>`:
```typescript
import { useRouter } from 'vue-router'
import { songApi } from '@/api/song'
import type { Song } from '@/types'
```

Add the `router` and `player` setup:
```typescript
const router = useRouter()
const player = usePlayerStore()
```

If `usePlayerStore` and `useRouter` are not already imported, add them.

Add these functions after `onMounted`:

```typescript
async function playSong(it: TrackItem) {
  try {
    const resp = await songApi.getAudioUrl(it.id)
    if (!resp?.url) {
      alert('该歌曲暂无可用播放地址')
      return
    }
    const song: Song = {
      id: it.id,
      title: it.t,
      artist: it.a,
      album: it.al || undefined,
      platform: (playlist.value?.platform ?? 'netease') as import('@/types').Platform,
      platformSongId: it.platformSongId,
      duration: it.durationSecs,
      coverUrl: playlist.value?.coverUrl,
      audioUrl: resp.url,
    }
    player.setSong(song)
    player.setPlaying(true)
    router.push('/player')
  } catch {
    alert('获取播放地址失败，请稍后重试')
  }
}

async function playAll() {
  if (tracks.value.length === 0) return
  const ids = tracks.value.map(t => parseInt(t.id)).filter(n => !isNaN(n))
  try {
    const urlMap = await songApi.batchAudioUrls(ids)
    const queue: Song[] = tracks.value
      .filter(t => urlMap[t.id])
      .map(t => ({
        id: t.id,
        title: t.t,
        artist: t.a,
        album: t.al || undefined,
        platform: (playlist.value?.platform ?? 'netease') as import('@/types').Platform,
        platformSongId: t.platformSongId,
        duration: t.durationSecs,
        coverUrl: playlist.value?.coverUrl,
        audioUrl: urlMap[t.id],
      }))
    if (queue.length === 0) {
      alert('该歌单暂无可播放的歌曲')
      return
    }
    player.setSong(queue[0])
    player.setQueue(queue.slice(1))
    player.setPlaying(true)
    router.push('/player')
  } catch {
    alert('获取播放列表失败，请稍后重试')
  }
}
```

- [ ] **Step 4: Wire up play buttons in the template**

In `Playlist.vue`'s template, find the track list rows. Each row should call `playSong(it)` on click. Also wire the "播放全部" button (if present) to `playAll()`.

For per-track play, add `@click="playSong(it)"` to each track row element (or the play icon button within it).

For the header "播放全部" button (find by searching for any play/shuffle button in the header section), add `@click="playAll()"`.

If no "播放全部" button exists in the template, add one in the playlist header area:

```html
<button class="btn" @click="playAll()" style="margin-top:12px;">
  ▶ 播放全部
</button>
```

- [ ] **Step 5: Verify TypeScript**

```bash
cd moodfm-frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 6: Manual smoke test**

1. Start backend: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
2. Start frontend: `npm run dev`
3. Navigate to `/playlists` → click a playlist → click a track
4. Expected: brief loading moment, then redirects to `/player` and song starts playing
5. Click "播放全部" on a playlist
6. Expected: player loads with the full queue

- [ ] **Step 7: Commit**

```bash
git add moodfm-frontend/src/api/song.ts \
        moodfm-frontend/src/views/library/Playlist.vue
git commit -m "feat: resolve platform audio URL on play — single song and play-all"
```

---

## Self-Review

**Spec coverage:**
- ✅ Playlist list cache with TTL — Tasks 1–2
- ✅ Instant return navigation (no spinner second visit) — Task 2, step 3
- ✅ Backend audio URL resolution for single song — Task 3–4
- ✅ Batch audio URL for play-all — Task 3–4
- ✅ Frontend wired to play resolved URLs — Task 5

**Placeholder scan:** None — all steps contain concrete code.

**Type consistency:**
- `TrackItem.id: string` used consistently in `playSong(it.id)`, `batchAudioUrls(ids)`
- `songApi.getAudioUrl` returns `{ url: string | null }` — frontend checks `resp?.url`
- `songApi.batchAudioUrls` returns `Record<string, string>` — frontend indexes with `urlMap[t.id]`
- Backend `Map<Long, String>` serializes to `{ "1": "...", "2": "..." }` in JSON — frontend indexes with string key `t.id`
