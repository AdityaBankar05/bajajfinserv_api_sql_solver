package com.example.webhooksolver.service;

import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class SqlSolver {

    public Optional<String> solveFromText(String text) {
        if(text == null) return Optional.empty();
        String lower = text.toLowerCase();

        if(lower.contains("last 30 days") || lower.contains("last thirty days") || lower.contains("registered in the last 30 days")) {
            return Optional.of("SELECT * FROM users WHERE registration_date >= CURRENT_DATE - INTERVAL '30' DAY;");
        }

        if(lower.contains("highest salary") || lower.contains("max salary")) {
            return Optional.of("SELECT * FROM employees WHERE salary = (SELECT MAX(salary) FROM employees);");
        }

        if(lower.contains("salary higher than manager") || lower.contains("higher than manager")) {
            return Optional.of("SELECT e.* FROM employees e JOIN employees m ON e.manager_id = m.id WHERE e.salary > m.salary;");
        }

        if(Pattern.compile(".*\\b(\\d+)(st|nd|rd|th) highest\\b.*").matcher(lower).find()) {
            java.util.regex.Matcher m = Pattern.compile("(\\d+)(?:st|nd|rd|th) highest").matcher(lower);
            if(m.find()) {
                String n = m.group(1);
                return Optional.of(String.format("SELECT DISTINCT salary FROM employees ORDER BY salary DESC LIMIT 1 OFFSET %d;", Integer.parseInt(n)-1));
            }
        }

        if(lower.contains("duplicate") || lower.contains("duplicates")) {
            return Optional.of("SELECT col, COUNT(*) FROM your_table GROUP BY col HAVING COUNT(*) > 1;");
        }

        return Optional.empty();
    }
}