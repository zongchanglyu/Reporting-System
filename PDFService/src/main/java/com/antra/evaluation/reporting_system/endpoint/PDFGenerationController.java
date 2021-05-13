package com.antra.evaluation.reporting_system.endpoint;

import com.antra.evaluation.reporting_system.pojo.api.PDFRequest;
import com.antra.evaluation.reporting_system.pojo.api.PDFResponse;
import com.antra.evaluation.reporting_system.pojo.report.PDFFile;
import com.antra.evaluation.reporting_system.service.PDFService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
public class PDFGenerationController {

    private static final Logger log = LoggerFactory.getLogger(PDFGenerationController.class);

    private PDFService pdfService;

    @Autowired
    public PDFGenerationController(PDFService pdfService) {
        this.pdfService = pdfService;
    }


    @PostMapping("/pdf")
    public ResponseEntity<PDFResponse> createPDF(@RequestBody @Validated PDFRequest request) {
        log.info("Got request to generate PDF: {}", request);

        PDFResponse response = new PDFResponse();
        PDFFile file = null;
        response.setReqId(request.getReqId());

        try {
            file = pdfService.createPDF(request, null);
            response.setFileId(file.getId());
            response.setFileLocation(file.getFileLocation());
            response.setFileSize(file.getFileSize());
            log.info("Generated: {}", file);
        } catch (Exception e) {
            response.setFailed(true);
            log.error("Error in generating pdf", e);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/pdf/{id}")
    public ResponseEntity<String> deletePDF(@PathVariable String id) {
        log.debug("Got Request to Delete File:{}", id);
        var response = new PDFResponse();
        pdfService.deleteFile(id);

        log.debug("File Deleted:{}", id);
        return new ResponseEntity<>("Deleting PDF file Success!", HttpStatus.OK);
    }

    @PutMapping("/pdf/{id}")
    public ResponseEntity<PDFResponse> updatePDF(@PathVariable String id,
                                                 HttpEntity<PDFRequest> httpEntity) {
        PDFRequest request = httpEntity.getBody();
        log.info("Got request to update PDF: {}", request);

        PDFResponse response = new PDFResponse();
        response.setReqId(request.getReqId());

        try {
            PDFFile file = pdfService.updatePDF(request, id);
            response.setFileId(file.getId());
            response.setFileLocation(file.getFileLocation());
            response.setFileSize(file.getFileSize());
            log.info("Updated: {}", file);
        } catch (Exception e) {
            response.setFailed(true);
            log.error("Error in updating pdf", e);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
