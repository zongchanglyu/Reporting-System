package com.antra.report.client.service;

import com.amazonaws.services.s3.AmazonS3;
import com.antra.report.client.entity.ExcelReportEntity;
import com.antra.report.client.entity.PDFReportEntity;
import com.antra.report.client.entity.ReportRequestEntity;
import com.antra.report.client.entity.ReportStatus;
import com.antra.report.client.exception.RequestNotFoundException;
import com.antra.report.client.pojo.EmailType;
import com.antra.report.client.pojo.FileType;
import com.antra.report.client.pojo.reponse.ExcelResponse;
import com.antra.report.client.pojo.reponse.PDFResponse;
import com.antra.report.client.pojo.reponse.ReportVO;
import com.antra.report.client.pojo.reponse.SqsResponse;
import com.antra.report.client.pojo.request.ReportRequest;
import com.antra.report.client.repository.ReportRequestRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportRequestRepo reportRequestRepo;
    private final SNSService snsService;
    private final AmazonS3 s3Client;
    private final EmailService emailService;

    public ReportServiceImpl(ReportRequestRepo reportRequestRepo, SNSService snsService, AmazonS3 s3Client, EmailService emailService) {
        this.reportRequestRepo = reportRequestRepo;
        this.snsService = snsService;
        this.s3Client = s3Client;
        this.emailService = emailService;
    }

    private ReportRequestEntity persistToLocal(ReportRequest request) {
        request.setReqId("Req-"+ UUID.randomUUID().toString());

        ReportRequestEntity entity = new ReportRequestEntity();
        entity.setReqId(request.getReqId());
        entity.setSubmitter(request.getSubmitter());
        entity.setDescription(request.getDescription());
        entity.setCreatedTime(LocalDateTime.now());

        PDFReportEntity pdfReport = new PDFReportEntity();
        pdfReport.setRequest(entity);
        pdfReport.setStatus(ReportStatus.PENDING);
        pdfReport.setCreatedTime(LocalDateTime.now());
        entity.setPdfReport(pdfReport);

        ExcelReportEntity excelReport = new ExcelReportEntity();
        BeanUtils.copyProperties(pdfReport, excelReport);
        entity.setExcelReport(excelReport);

        return reportRequestRepo.save(entity);
    }

    @Override
    public ReportVO generateReportsSync(ReportRequest request) {
        persistToLocal(request);
        try {
            sendDirectRequests(request);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new ReportVO(reportRequestRepo.findById(request.getReqId()).orElseThrow());
    }

    //TODO:Change to parallel process using Threadpool? CompletableFuture?
    private void sendDirectRequests(ReportRequest request) throws ExecutionException, InterruptedException {
        RestTemplate rs = new RestTemplate();
        ExecutorService executor = Executors.newCachedThreadPool();

        CompletableFuture<ExcelResponse> cf1 = CompletableFuture.supplyAsync(()->{
            ExcelResponse excelResponse = new ExcelResponse();
            try {
                excelResponse = rs.postForEntity("http://localhost:8888/excel", request, ExcelResponse.class).getBody();
            } catch(Exception e){
                log.error("Excel Generation Error (Sync) : e", e);
                excelResponse.setReqId(request.getReqId());
                excelResponse.setFailed(true);
            } finally {
                return excelResponse;
            }
        },executor);

        CompletableFuture<PDFResponse> cf2 = CompletableFuture.supplyAsync(()->{
            PDFResponse pdfResponse = new PDFResponse();
            try {
                pdfResponse = rs.postForEntity("http://localhost:9999/pdf", request, PDFResponse.class).getBody();
            } catch(Exception e){
                log.error("PDF Generation Error (Sync) : e", e);
                pdfResponse.setReqId(request.getReqId());
                pdfResponse.setFailed(true);
            } finally {
                return pdfResponse;
            }
        },executor);

        updateLocal(cf1.get());
        updateLocal(cf2.get());
    }

    private void updateLocal(ExcelResponse excelResponse) {
        SqsResponse response = new SqsResponse();
        BeanUtils.copyProperties(excelResponse, response);
        updateAsyncExcelReport(response);
    }
    private void updateLocal(PDFResponse pdfResponse) {
        SqsResponse response = new SqsResponse();
        BeanUtils.copyProperties(pdfResponse, response);
        updateAsyncPDFReport(response);
    }

    @Override
    @Transactional
    public ReportVO generateReportsAsync(ReportRequest request) {
        ReportRequestEntity entity = persistToLocal(request);
        snsService.sendReportNotification(request);
        log.info("Send SNS the message: {}",request);
        return new ReportVO(entity);
    }

    @Override
//    @Transactional // why this? email could fail
    public void updateAsyncPDFReport(SqsResponse response) {
        ReportRequestEntity entity = reportRequestRepo.findById(response.getReqId()).orElseThrow(RequestNotFoundException::new);
        var pdfReport = entity.getPdfReport();
        pdfReport.setUpdatedTime(LocalDateTime.now());
        if (response.isFailed()) {
            pdfReport.setStatus(ReportStatus.FAILED);
        } else{
            pdfReport.setStatus(ReportStatus.COMPLETED);
            pdfReport.setFileId(response.getFileId());
            pdfReport.setFileLocation(response.getFileLocation());
            pdfReport.setFileSize(response.getFileSize());
        }
        entity.setUpdatedTime(LocalDateTime.now());
        reportRequestRepo.save(entity);
        String to = "zongchal@uci.com";
        emailService.sendEmail(to, EmailType.SUCCESS, entity.getSubmitter());
    }

    @Override
//    @Transactional
    public void updateAsyncExcelReport(SqsResponse response) {
        ReportRequestEntity entity = reportRequestRepo.findById(response.getReqId()).orElseThrow(RequestNotFoundException::new);
        var excelReport = entity.getExcelReport();
        excelReport.setUpdatedTime(LocalDateTime.now());
        if (response.isFailed()) {
            excelReport.setStatus(ReportStatus.FAILED);
        } else{
            excelReport.setStatus(ReportStatus.COMPLETED);
            excelReport.setFileId(response.getFileId());
            excelReport.setFileLocation(response.getFileLocation());
            excelReport.setFileSize(response.getFileSize());
        }
        entity.setUpdatedTime(LocalDateTime.now());
        reportRequestRepo.save(entity);
        String to = "zongchal@uci.com";
        emailService.sendEmail(to, EmailType.SUCCESS, entity.getSubmitter());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportVO> getReportList() {
        return reportRequestRepo.findAll().stream().map(ReportVO::new).collect(Collectors.toList());
    }

    @Override
    public InputStream getFileBodyByReqId(String reqId, FileType type) {
        ReportRequestEntity entity = reportRequestRepo.findById(reqId).orElseThrow(RequestNotFoundException::new);
        if (type == FileType.PDF) {
            String fileLocation = entity.getPdfReport().getFileLocation(); // this location is s3 "bucket/key"
            String bucket = fileLocation.split("/")[0];
            String key = fileLocation.split("/")[1];
            return s3Client.getObject(bucket, key).getObjectContent();
        } else if (type == FileType.EXCEL) {
            String fileId = entity.getExcelReport().getFileId();
//            String fileLocation = entity.getExcelReport().getFileLocation();
//            try {
//                return new FileInputStream(fileLocation);// this location is in local, definitely sucks
//            } catch (FileNotFoundException e) {
//                log.error("No file found", e);
//            }
            RestTemplate restTemplate = new RestTemplate();
//            InputStream is = restTemplate.execute(, HttpMethod.GET, null, ClientHttpResponse::getBody, fileId);
            ResponseEntity<Resource> exchange = restTemplate.exchange("http://localhost:8888/excel/{id}/content",
                    HttpMethod.GET, null, Resource.class, fileId);
            try {
                return exchange.getBody().getInputStream();
            } catch (IOException e) {
                log.error("Cannot download excel",e);
            }
        }
        return null;
    }

    @Override
    public ReportRequestEntity findFileById(String reqId) {
        ReportRequestEntity entity = reportRequestRepo.findById(reqId).orElseThrow(RequestNotFoundException::new);
        return entity;
    }


    //delete
    @Transactional
    @Override
    public void deleteFileById(String reqId) {
        sendDeleteRequest(reqId);
        reportRequestRepo.deleteById(reqId);
    }

    public void sendDeleteRequest(String id) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        RestTemplate rs = new RestTemplate();

        CompletableFuture<ExcelResponse> cf1 = CompletableFuture.supplyAsync(()->{
            ExcelResponse excelResponse = new ExcelResponse();
            ReportRequestEntity entity = reportRequestRepo.findById(id).orElseThrow();
            try {
                rs.delete("http://localhost:8888/excel/{id}",entity.getExcelReport().getFileId());
                excelResponse.setReqId(id);
            } catch(Exception e){
                log.error("Excel Deletion Error (Sync) : e", e);
                excelResponse.setReqId(id);
                excelResponse.setFailed(true);
            } finally {
                return excelResponse;
            }
        },executor);

        CompletableFuture<PDFResponse> cf2 = CompletableFuture.supplyAsync(()->{
            PDFResponse pdfResponse = new PDFResponse();
            ReportRequestEntity entity = reportRequestRepo.findById(id).orElseThrow();
            try {
                rs.delete("http://localhost:9999/pdf/{id}",entity.getPdfReport().getFileId());
                pdfResponse.setReqId(id);
            } catch(Exception e){
                log.error("PDF Deletion Error (Sync) : e", e);
                pdfResponse.setReqId(id);
                pdfResponse.setFailed(true);
            } finally {
                return pdfResponse;
            }
        },executor);
    }


    //update
    @Override
    public ReportVO updateReportsSync(ReportRequest request) {
        try {
            sendDirectUpdateRequests(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ReportVO(reportRequestRepo.findById(request.getReqId()).orElseThrow());
    }

    private void sendDirectUpdateRequests(ReportRequest request) throws InterruptedException, ExecutionException{
        ExecutorService executor = Executors.newFixedThreadPool(2);
        RestTemplate rs = new RestTemplate();
        CompletableFuture<ExcelResponse> cf1 = CompletableFuture.supplyAsync(()->{
            ExcelResponse excelResponse = new ExcelResponse();
            ReportRequestEntity entity = reportRequestRepo.findById(request.getReqId()).orElseThrow();
            try {
                HttpEntity<ReportRequest> httpEntity = new HttpEntity<>(request);
                excelResponse = rs.exchange("http://localhost:8888/excel/{id}",HttpMethod.PUT,
                        httpEntity,ExcelResponse.class,entity.getExcelReport().getFileId()).getBody();
            } catch(Exception e){
                log.error("Excel Update Error (Sync) : e", e);
                excelResponse.setReqId(request.getReqId());
                excelResponse.setFailed(true);
            } finally {
                return excelResponse;
            }
        },executor);

        CompletableFuture<PDFResponse> cf2 = CompletableFuture.supplyAsync(()->{
            PDFResponse pdfResponse = new PDFResponse();
            ReportRequestEntity entity = reportRequestRepo.findById(request.getReqId()).orElseThrow();
            try {
                HttpEntity<ReportRequest> httpEntity = new HttpEntity<>(request);
                pdfResponse = rs.exchange("http://localhost:9999/pdf/{id}",HttpMethod.PUT,
                        httpEntity,PDFResponse.class,entity.getPdfReport().getFileId()).getBody();
            } catch(Exception e){
                log.error("PDF Update Error (Sync) : e", e);
                pdfResponse.setReqId(request.getReqId());
                pdfResponse.setFailed(true);
            } finally {
                return pdfResponse;
            }
        },executor);

        updateLocal(cf1.get());
        updateLocal(cf2.get());
    }

    @Override
    @Transactional
    public ReportVO updateReportsAsync(ReportRequest request) {
        ReportRequest req = new ReportRequest();
        BeanUtils.copyProperties(request, req);
        snsService.sendReportNotification(req);
        log.info("Send SNS the message: {}",req);
        return new ReportVO(reportRequestRepo.findById(request.getReqId()).orElseThrow());
    }
}
