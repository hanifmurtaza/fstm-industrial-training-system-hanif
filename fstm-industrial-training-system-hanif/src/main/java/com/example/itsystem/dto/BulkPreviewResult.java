package com.example.itsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkPreviewResult {
  private List<BulkStudentRow> rows = new ArrayList<>();
  private long validCount;
  private long errorCount;

  public List<BulkStudentRow> validRows() {
    return rows.stream().filter(r -> r.getError() == null).toList();
  }
}
