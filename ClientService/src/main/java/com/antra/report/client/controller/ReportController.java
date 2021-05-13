package com.antra.report.client.controller;

import com.antra.report.client.entity.ReportRequestEntity;
import com.antra.report.client.exception.RequestNotFoundException;
import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ErrorResponse;
import com.antra.report.client.pojo.reponse.GeneralResponse;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.pojo.request.ReportRequest;
import com.antra.report.client.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

@RestController
public class ReportController {
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/report")
    public ResponseEntity<GeneralResponse> listReport() {
        log.info("Got Request to list all report");
        return ResponseEntity.ok(new GeneralResponse(reportService.getReportList()));
    }

    @PostMapping("/report/sync")
    public ResponseEntity<GeneralResponse> createReportDirectly(@RequestBody @Validated ReportRequest request) {
        log.info("Got Request to generate report - sync: {}", request);
        request.setDescription(String.join(" - ", "Sync", request.getDescription()));
        return ResponseEntity.ok(new GeneralResponse(reportService.generateReportsSync(request)));
    }

    @PostMapping("/report/async")
    public ResponseEntity<GeneralResponse> createReportAsync(@RequestBody @Validated ReportRequest request) {
        log.info("Got Request to generate report - async: {}", request);
        request.setDescription(String.join(" - ", "Async", request.getDescription()));
        reportService.generateReportsAsync(request);
        return ResponseEntity.ok(new GeneralResponse());
    }

    @GetMapping("/report/content/{reqId}/{type}")
    public void downloadFile(@PathVariable String reqId, @PathVariable FileType type, HttpServletResponse response) throws IOException {
        log.debug("Got Request to Download File - type: {}, reqid: {}", type, reqId);
        InputStream fis = reportService.getFileBodyByReqId(reqId, type);
        String fileType = null;
        String fileName = null;
        if(type == FileType.PDF) {
            fileType = "application/pdf";
            fileName = "report.pdf";
        } else if (type == FileType.EXCEL) {
            fileType = "application/vnd.ms-excel";
            fileName = "report.xls";
        }
        response.setHeader("Content-Type", fileType);
        response.setHeader("fileName", fileName);
        if (fis != null) {
            FileCopyUtils.copy(fis, response.getOutputStream());
        } else{
            response.setStatus(500);
        }
        log.debug("Downloaded File:{}", reqId);
    }

//   @DeleteMapping
    @DeleteMapping("/report/{reqId}")
    public ResponseEntity<GeneralResponse> deleteReport(@PathVariable String reqId){
        ReportRequestEntity entity = reportService.findFileById(reqId);
        if (entity == null) {
            throw new RequestNotFoundException();
        }
        log.info("Got Request to delete report -  reqid: {}", reqId);
        reportService.delteFileById(reqId);
        return ResponseEntity.ok(new GeneralResponse());
    }

//   @PutMapping
    @PutMapping("/report/{id}/sync")
    public ResponseEntity<GeneralResponse> updateReportDirectly(@PathVariable String id,
                                                                @RequestBody @Validated ReportRequest request) {
        request.setReqId(id);
        log.info("Got Request to update report - sync: {}", request);
        request.setDescription(String.join(" - ", "Sync", request.getDescription()));

        return ResponseEntity.ok(new GeneralResponse(reportService.updateReportsSync(request)));
    }

    @PutMapping("/report/{id}/async")
    public ResponseEntity<GeneralResponse> updateReportAsync(@PathVariable String id,
                                                             @RequestBody @Validated ReportRequest request) {
        request.setReqId(id);
        log.info("Got Request to update report - async: {}", request);
        request.setDescription(String.join(" - ", "Async", request.getDescription()));
        reportService.updateReportsAsync(request);
        return ResponseEntity.ok(new GeneralResponse());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GeneralResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Input Data invalid: {}", e.getMessage());
        String errorFields = e.getBindingResult().getFieldErrors().stream().map(fe -> String.join(" ",fe.getField(),fe.getDefaultMessage())).collect(Collectors.joining(", "));
        return new ResponseEntity<>(new ErrorResponse(HttpStatus.BAD_REQUEST, errorFields), HttpStatus.BAD_REQUEST);
    }
}
