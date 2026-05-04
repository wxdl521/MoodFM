package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CalendarDayVO {
    private int day;
    private String mood;    // calm / dusk / blue / energy / focus / empty
    private int tracks;
    private int minutes;
    private int sessions;
    private boolean future;
    private boolean empty;
}
