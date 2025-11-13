package com.example.itsystem.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface GradingService {
    void exportXlsx(String semester, HttpServletResponse resp) throws IOException;
}
