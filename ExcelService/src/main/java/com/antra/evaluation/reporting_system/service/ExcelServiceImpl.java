package com.antra.evaluation.reporting_system.service;

import com.amazonaws.services.s3.AmazonS3;
import com.antra.evaluation.reporting_system.entity.ExcelFileEntity;
import com.antra.evaluation.reporting_system.exception.FileGenerationException;
import com.antra.evaluation.reporting_system.pojo.api.ExcelRequest;
import com.antra.evaluation.reporting_system.pojo.api.MultiSheetExcelRequest;
import com.antra.evaluation.reporting_system.pojo.report.ExcelData;
import com.antra.evaluation.reporting_system.pojo.report.ExcelDataHeader;
import com.antra.evaluation.reporting_system.pojo.report.ExcelDataSheet;
import com.antra.evaluation.reporting_system.pojo.report.ExcelFile;
import com.antra.evaluation.reporting_system.repo.ExcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelServiceImpl implements ExcelService {

    private static final Logger log = LoggerFactory.getLogger(ExcelServiceImpl.class);

    ExcelRepository excelRepository;

    private ExcelGenerationService excelGenerationService;

    private AmazonS3 s3Client;

    @Value("${s3.bucket}")
    private String s3Bucket;

    @Autowired
    public ExcelServiceImpl(ExcelRepository excelRepository, ExcelGenerationService excelGenerationService, AmazonS3 s3Client) {
        this.excelRepository = excelRepository;
        this.excelGenerationService = excelGenerationService;
        this.s3Client = s3Client;
    }

    @Override
    public InputStream getExcelBodyById(String id) throws FileNotFoundException {
        Optional<ExcelFileEntity> fileInfo = excelRepository.findById(id);
        return new FileInputStream(fileInfo.orElseThrow(FileNotFoundException::new).getFileLocation());
    }

    @Override
    public ExcelFileEntity generateFile(ExcelRequest request, boolean multisheet, String id) {
        ExcelFileEntity fileInfo = new ExcelFileEntity();
        fileInfo.setFileId(id == null ? UUID.randomUUID().toString() : id);
        ExcelData data = new ExcelData();
        data.setTitle(request.getDescription());
        data.setFileId(fileInfo.getFileId());
        data.setSubmitter(fileInfo.getSubmitter());
        if(multisheet){
            data.setSheets(generateMultiSheet(request));
        }else {
            data.setSheets(generateSheet(request));
        }
        try {
            File generatedFile = excelGenerationService.generateExcelReport(data);
            fileInfo.setFileLocation(generatedFile.getAbsolutePath());
            fileInfo.setFileName(generatedFile.getName());
            fileInfo.setGeneratedTime(LocalDateTime.now());
            fileInfo.setSubmitter(request.getSubmitter());
            fileInfo.setFileSize(generatedFile.length());
            fileInfo.setDescription(request.getDescription());

            //Upload excel files to S3 Cloud
            File temp = new File(generatedFile.getAbsolutePath());
            log.debug("Upload excel file to s3 {}", generatedFile.getAbsolutePath());
            s3Client.putObject(s3Bucket,fileInfo.getFileId(),temp);
            log.debug("Uploaded excel file to s3");

        } catch (IOException e) {
//            log.error("Error in generateFile()", e);
            throw new FileGenerationException(e);
        }

        excelRepository.save(fileInfo);
        log.debug("Excel File Generated : {}", fileInfo);
        return fileInfo;
    }

    @Override
    public List<ExcelFileEntity> getExcelList() {
        return excelRepository.findAll();
    }

    @Override
    public ExcelFileEntity deleteFile(String id) throws FileNotFoundException {
        ExcelFileEntity excelFile = excelRepository.findById(id).orElse(null);
        if (excelFile == null) {
            throw new FileNotFoundException();
        }
        //fix bug: also delete corresponding file from S3 cloud.
//        String location = id;
//        log.debug("delete file from s3 {}", location);
//        s3Client.deleteObject(s3Bucket, location);
//        log.debug("Deleted Excel file from S3");

        excelRepository.deleteById(id);
        return excelFile;
    }

    //update
    @Transactional
    @Override
    public ExcelFileEntity updateExcel(ExcelRequest request, String id) {
        try {
            deleteFile(id);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ExcelFileEntity file = generateFile(request, false, id);
        return file;
    }

    private List<ExcelDataSheet> generateSheet(ExcelRequest request) {
        List<ExcelDataSheet> sheets = new ArrayList<>();
        ExcelDataSheet sheet = new ExcelDataSheet();
        sheet.setHeaders(request.getHeaders().stream().map(ExcelDataHeader::new).collect(Collectors.toList()));
        sheet.setDataRows(request.getData().stream().map(listOfString -> (List<Object>) new ArrayList<Object>(listOfString)).collect(Collectors.toList()));
        sheet.setTitle("sheet-1");
        sheets.add(sheet);
        return sheets;
    }
    private List<ExcelDataSheet> generateMultiSheet(ExcelRequest request) {
        List<ExcelDataSheet> sheets = new ArrayList<>();
        int index = request.getHeaders().indexOf(((MultiSheetExcelRequest) request).getSplitBy());
        Map<String, List<List<String>>> splittedData = request.getData().stream().collect(Collectors.groupingBy(row -> (String)row.get(index)));
        List<ExcelDataHeader> headers = request.getHeaders().stream().map(ExcelDataHeader::new).collect(Collectors.toList());
        splittedData.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(
                entry ->{
                    ExcelDataSheet sheet = new ExcelDataSheet();
                    sheet.setHeaders(headers);
                    sheet.setDataRows(entry.getValue().stream().map(listOfString -> {
                        List<Object> listOfObject = new ArrayList<>();
                        listOfString.forEach(listOfObject::add);
                        return listOfObject;
                    }).collect(Collectors.toList()));
                    sheet.setTitle(entry.getKey());
                    sheets.add(sheet);
                }
        );
        return sheets;
    }
}
