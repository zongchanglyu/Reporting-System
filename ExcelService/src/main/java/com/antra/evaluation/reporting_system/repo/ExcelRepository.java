package com.antra.evaluation.reporting_system.repo;

import com.antra.evaluation.reporting_system.entity.ExcelFileEntity;
import com.antra.evaluation.reporting_system.pojo.report.ExcelFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExcelRepository extends JpaRepository<ExcelFileEntity, String> {

}
//public interface ExcelRepository {
//    Optional<ExcelFile> getFileById(String id);
//
//    ExcelFile saveFile(ExcelFile file);
//
//    ExcelFile deleteFile(String id);
//
//    List<ExcelFile> getFiles();
//}
