package com.example.itsystem.service;

import com.example.itsystem.dto.BulkPreviewResult;
import com.example.itsystem.dto.BulkStudentRow;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.itsystem.model.Department;


import java.io.*;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BulkStudentImportService {

  private final UserRepository userRepository;

  public BulkPreviewResult preview(MultipartFile file, String defaultSession) throws IOException {
    List<BulkStudentRow> rows = parse(file);

    for (BulkStudentRow r : rows) {
      if (blank(r.getSession())) r.setSession(defaultSession);

      if (blank(r.getMatricNo()) || blank(r.getName())) {
        r.setError("Missing Matric No or Name");
      } else if (userRepository.existsByStudentId(r.getMatricNo())) {
        r.setError("Duplicate Matric No (already in system)");
      } else if (r.getAccessStart() != null && r.getAccessEnd() != null &&
                 r.getAccessEnd().isBefore(r.getAccessStart())) {
        r.setError("Access End before Start");
      }
    }

    BulkPreviewResult res = new BulkPreviewResult();
    res.setRows(rows);
    res.setValidCount(rows.stream().filter(x -> x.getError() == null).count());
    res.setErrorCount(rows.size() - res.getValidCount());
    return res;
  }

  public int commit(List<BulkStudentRow> rows,
                    java.util.function.BiConsumer<String,String> ensureUserFn,
                    Department defaultDepartment) {
    int created = 0;
    for (BulkStudentRow r : rows) {
      if (r.getError() != null) continue;

      // Ensure a minimal user exists (by username = matric)
      ensureUserFn.accept(r.getMatricNo(), r.getName());

      // Update the user’s student fields
      Optional<User> maybe = userRepository.findByUsername(r.getMatricNo());
      if (maybe.isPresent()) {
        User u = maybe.get();
        u.setStudentId(r.getMatricNo());
        if (blank(u.getName())) u.setName(r.getName()); // don't overwrite existing non-blank
        u.setSession(r.getSession());
        if (r.getAccessStart() != null) u.setAccessStart(r.getAccessStart());
        if (r.getAccessEnd() != null)   u.setAccessEnd(r.getAccessEnd());

        if (defaultDepartment != null) {
          u.setDepartment(defaultDepartment);
        }
        userRepository.save(u);
        created++;
      }
    }
    return created;
  }

  // ---------- parsing ----------
  private List<BulkStudentRow> parse(MultipartFile file) throws IOException {
    String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    if (fn.endsWith(".csv")) return parseCsv(file.getInputStream());
    if (fn.endsWith(".xlsx")) return parseXlsx(file.getInputStream());
    throw new IllegalArgumentException("Unsupported file type. Use .csv or .xlsx");
  }

  private List<BulkStudentRow> parseCsv(InputStream in) throws IOException {
    List<BulkStudentRow> out = new ArrayList<>();

    try (Reader reader = new InputStreamReader(in);
         CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {

      List<CSVRecord> rows = parser.getRecords();
      if (rows.isEmpty()) {
        return out;
      }

      // 1) Find which row is the header row (contains Matric + Name headers)
      int headerRowIndex = -1;
      int matricCol = -1;
      int nameCol = -1;

      for (int i = 0; i < rows.size(); i++) {
        CSVRecord rec = rows.get(i);
        int rowMatricCol = -1;
        int rowNameCol   = -1;

        for (int c = 0; c < rec.size(); c++) {
          String cell = rec.get(c);
          if (cell == null) continue;
          String norm = cell.trim().toUpperCase();

          if (containsAny(norm,
                  "NO. MATRIK", "NO MATRIK", "MATRIC NO", "MATRIC", "NO. MATRIC")) {
            rowMatricCol = c;
          }
          if (containsAny(norm,
                  "NAMA PELAJAR", "NAMA", "NAME", "STUDENT NAME")) {
            rowNameCol = c;
          }
        }

        // If this row has both headers, it's our header row
        if (rowMatricCol >= 0 && rowNameCol >= 0) {
          headerRowIndex = i;
          matricCol = rowMatricCol;
          nameCol = rowNameCol;
          break;
        }
      }

      if (headerRowIndex < 0) {
        throw new IllegalArgumentException(
                "Could not detect header row with Matric and Name columns. " +
                        "Please ensure there is a row containing 'NO. MATRIK' and 'NAMA PELAJAR'.");
      }

      // 2) Data rows start AFTER the header row
      for (int rIndex = headerRowIndex + 1; rIndex < rows.size(); rIndex++) {
        CSVRecord rec = rows.get(rIndex);

        String matric = getCell(rec, matricCol);
        String name   = getCell(rec, nameCol);

        // skip completely empty lines
        if ((matric == null || matric.isBlank()) &&
                (name == null || name.isBlank())) {
          continue;
        }

        // Session/access dates = null here; defaultSession is applied in preview()
        BulkStudentRow row = mapRow(
                rIndex + 1,  // rowNumber (1-based, roughly file line)
                matric,
                name,
                null,  // session (filled later)
                null,  // accessStart
                null   // accessEnd
        );
        out.add(row);
      }
    }

    return out;
  }



  private List<BulkStudentRow> parseXlsx(InputStream in) throws IOException {
    List<BulkStudentRow> out = new ArrayList<>();
    try (Workbook wb = WorkbookFactory.create(in)) {
      Sheet sh = wb.getSheetAt(0);
      Map<String,Integer> idx = headerIndex(sh.getRow(0));
      for (int r = 1; r <= sh.getLastRowNum(); r++) {
        Row row = sh.getRow(r);
        if (row == null) continue;
        out.add(mapRow(r+1,
            cell(row, idx.get("Matric No")),
            cell(row, idx.get("Name")),
            cell(row, idx.get("Session (optional: e.g., 2025/2026-1)")),
            cell(row, idx.get("Access Start (optional: yyyy-mm-dd)")),
            cell(row, idx.get("Access End (optional: yyyy-mm-dd)"))
        ));
      }
    }
    return out;
  }

  private BulkStudentRow mapRow(int rn, String matric, String name, String session, String start, String end) {
    BulkStudentRow r = new BulkStudentRow();
    r.setRowNumber(rn);
    r.setMatricNo(trim(matric));
    r.setName(trim(name));
    r.setSession(trim(session));
    r.setAccessStart(parseDate(start));
    r.setAccessEnd(parseDate(end));
    return r;
  }

  private static LocalDate parseDate(String s) {
    if (blank(s)) return null;
    return LocalDate.parse(s.trim()); // yyyy-MM-dd
  }

  private static boolean containsAny(String text, String... candidates) {
    for (String c : candidates) {
      if (text.contains(c)) return true;
    }
    return false;
  }

  private static String getCell(CSVRecord rec, int col) {
    if (col < 0 || col >= rec.size()) return null;
    String v = rec.get(col);
    return (v == null || v.isBlank()) ? null : v.trim();
  }


  private static String findHeaderKey(java.util.Set<String> headers, String... candidates) {
    for (String h : headers) {
      if (h == null) continue;
      String normalized = h.trim().toUpperCase();
      for (String c : candidates) {
        if (normalized.contains(c)) {
          return h; // return the actual header name used in the file
        }
      }
    }
    return null;
  }


  private static String get(CSVRecord rec, String key){ return rec.isMapped(key) ? rec.get(key) : null; }
  private static String trim(String s){ return s == null ? null : s.trim(); }
  private static boolean blank(String s){ return s == null || s.trim().isEmpty(); }

  // POI helpers
  private static String cell(Row row, Integer idx){
    if (idx == null) return null;
    Cell c = row.getCell(idx);
    if (c == null) return null;
    c.setCellType(CellType.STRING);
    return c.getStringCellValue();
  }
  private static Map<String,Integer> headerIndex(Row header){
    Map<String,Integer> map = new HashMap<>();
    for (int i=0; i<header.getLastCellNum(); i++){
      Cell c = header.getCell(i);
      if (c != null){
        c.setCellType(CellType.STRING);
        map.put(c.getStringCellValue().trim(), i);
      }
    }
    return map;
  }
}
