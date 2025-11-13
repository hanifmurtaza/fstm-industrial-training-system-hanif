package com.example.itsystem.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BulkStudentRow {
  private String matricNo;      // required
  private String name;          // required
  private String session;       // optional (row-level override)
  private LocalDate accessStart; // optional yyyy-MM-dd
  private LocalDate accessEnd;   // optional yyyy-MM-dd

  private int rowNumber;        // for error report/preview
  private String error;         // null => valid
}
