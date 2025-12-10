package org.maksaad.recouvchatbot_rag.tools;

import jdk.jfr.Description;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component("sqlExecutor")
@Description("Executes an SQL query against the MySQL database and returns the result.")
public class SqlTool{

    private final JdbcTemplate jdbcTemplate;

    public SqlTool(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "Executes a SQL query against the MySQL database and returns the result. Use this when the user asks for data.")
    public String executeQuery(String query) {
        try {
            if (!query.trim().toUpperCase().startsWith("SELECT")) {
                return "Error: Only SELECT queries are allowed.";
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
            return rows.toString();
        } catch (Exception e) {
            return "SQL Error: " + e.getMessage();
        }
    }



}
