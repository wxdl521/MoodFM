package com.moodfm.service.search;

import com.moodfm.domain.vo.SearchResultVO;

public interface SearchService {
    /**
     * @param userId  authenticated user ID
     * @param query   search string (keyword or mood description)
     * @param mode    "keyword" | "mood"
     * @param limit   max results (capped at 50 in impl)
     */
    SearchResultVO search(Long userId, String query, String mode, int limit);
}
