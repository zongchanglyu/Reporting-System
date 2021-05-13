package com.antra.evaluation.reporting_system.service;

import com.antra.evaluation.reporting_system.entity.ExcelFileEntity;
import com.antra.evaluation.reporting_system.pojo.api.ExcelRequest;
import com.antra.evaluation.reporting_system.pojo.report.ExcelFile;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public interface ExcelService {
    InputStream getExcelBodyById(String id) throws FileNotFoundException;

    ExcelFileEntity generateFile(ExcelRequest request, boolean multisheet, String id);

    List<ExcelFileEntity> getExcelList();

    ExcelFileEntity deleteFile(String id) throws FileNotFoundException;

    ExcelFileEntity updateExcel(ExcelRequest request, String id);
}
