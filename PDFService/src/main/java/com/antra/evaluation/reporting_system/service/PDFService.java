package com.antra.evaluation.reporting_system.service;

import com.antra.evaluation.reporting_system.pojo.api.PDFRequest;
import com.antra.evaluation.reporting_system.pojo.report.PDFFile;

public interface PDFService {
    PDFFile createPDF(PDFRequest request, String id);

    void deleteFile(String id);

    PDFFile updatePDF(PDFRequest request, String id);
}
