package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HistoryPageVO {
    private List<HistoryItemVO> items;
    private long total;
    private int page;
    private int pageSize;
}
