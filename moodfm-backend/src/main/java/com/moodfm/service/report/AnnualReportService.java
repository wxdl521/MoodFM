package com.moodfm.service.report;

import com.moodfm.domain.vo.AnnualReportVO;

public interface AnnualReportService {
    AnnualReportVO getAnnualReport(Long userId, int year);
}
