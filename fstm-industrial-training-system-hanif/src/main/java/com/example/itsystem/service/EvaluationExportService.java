package com.example.itsystem.service;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EvaluationExportService {

    public static class EvaluationRow {
        public int no;
        public String studentName;
        public String matric;
        public String session;

        public String vlText;       // e.g. "8.00 / 60" or "No data"
        public String industryText; // e.g. "31.00 / 40" or "No data"
        public String total;        // e.g. "45.00" or "-"
        public String grade;        // e.g. "D+"
        public String status;       // DRAFT / SUBMITTED / VERIFIED / FINALIZED
    }

    public void writeXlsx(List<EvaluationRow> rows, HttpServletResponse response) throws Exception {
        String fileName = "evaluations.xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);

        try (Workbook wb = new XSSFWorkbook(); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("Evaluations");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Create header
            String[] headers = {"No", "Student", "Matric", "Session", "VL (60)", "Industry (40)", "Total", "Grade", "Status"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data rows
            int r = 1;
            for (EvaluationRow row : rows) {
                Row rr = sheet.createRow(r++);
                rr.createCell(0).setCellValue(row.no);
                rr.createCell(1).setCellValue(nvl(row.studentName));
                rr.createCell(2).setCellValue(nvl(row.matric));
                rr.createCell(3).setCellValue(nvl(row.session));
                rr.createCell(4).setCellValue(nvl(row.vlText));
                rr.createCell(5).setCellValue(nvl(row.industryText));
                rr.createCell(6).setCellValue(nvl(row.total));
                rr.createCell(7).setCellValue(nvl(row.grade));
                rr.createCell(8).setCellValue(nvl(row.status));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(os);
            os.flush();
        }
    }

    private String nvl(String s) {
        return (s == null) ? "" : s;
    }
}
