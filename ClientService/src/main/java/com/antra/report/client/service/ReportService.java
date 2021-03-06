package com.antra.report.client.service;

import com.antra.report.client.entity.ReportRequestEntity;
import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ReportVO;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.pojo.request.ReportRequest;

import java.io.InputStream;
import java.util.List;

public interface ReportService {
    ReportVO generateReportsSync(ReportRequest request);

    ReportVO generateReportsAsync(ReportRequest request);

    void updateAsyncPDFReport(SqsResponse response);

    void updateAsyncExcelReport(SqsResponse response);

    List<ReportVO> getReportList();

    InputStream getFileBodyByReqId(String reqId, FileType type);

    ReportRequestEntity findFileById(String reqId);

    void deleteFileById(String reqId);

    ReportVO updateReportsSync(ReportRequest request);

    ReportVO updateReportsAsync(ReportRequest request);
}
