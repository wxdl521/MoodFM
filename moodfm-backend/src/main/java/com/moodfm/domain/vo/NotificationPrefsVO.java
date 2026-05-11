package com.moodfm.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefsVO {
    /** 每周报告通知 */
    private boolean weeklyReport;
    /** Cookie 即将过期通知 */
    private boolean cookieExpiry;
    /** 新功能通知 */
    private boolean newFeatures;
    /** 周报推送日（0=周一, 1=周二, ..., 6=周日，默认 0） */
    private Integer weeklyReportDay;
    /** 周报推送小时（0-23，默认 9） */
    private Integer weeklyReportHour;
}
