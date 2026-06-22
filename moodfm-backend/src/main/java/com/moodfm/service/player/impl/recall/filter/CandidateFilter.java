package com.moodfm.service.player.impl.recall.filter;

import com.moodfm.domain.vo.SongVO;

import java.util.List;

/**
 * One stage of the candidate filter pipeline. The orchestrator injects all
 * implementations as a {@code List<CandidateFilter>} sorted by {@code @Order}
 * ascending and applies them in sequence (negative-feedback → blacklist-keyword
 * → global-blacklist), each consuming the previous stage's output.
 * <p>
 * Implementations that only apply to a specific user must short-circuit
 * (return {@code candidates} unchanged) when {@code userId == null};
 * user-agnostic filters ignore {@code userId}.
 */
public interface CandidateFilter {
    List<SongVO> filter(Long userId, List<SongVO> candidates);
}
