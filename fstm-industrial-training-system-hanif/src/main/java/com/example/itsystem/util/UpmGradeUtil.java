package com.example.itsystem.util;

public final class UpmGradeUtil {

    private UpmGradeUtil() {}

    // Input: total mark out of 100
    public static String gradeFromTotal(double total) {
        if (total < 0) total = 0;
        if (total > 100) total = 100;

        if (total >= 80) return "A";
        if (total >= 75) return "A-";
        if (total >= 70) return "B+";
        if (total >= 65) return "B";
        if (total >= 60) return "B-";
        if (total >= 55) return "C+";
        if (total >= 50) return "C";
        if (total >= 47) return "C-";
        if (total >= 44) return "D+";
        if (total >= 40) return "D";
        return "F";
    }
}
