import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class DatabaseHelper {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/fare";
    private static final String USER = "root";
    private static final String PASSWORD = "Fareedha@1234"; // Remember to change if needed

    static class TableData {
        final List<String> headers;
        final ObservableList<ObservableList<String>> rows;

        TableData(List<String> headers, ObservableList<ObservableList<String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    public List<String> getTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        }
        return tableNames;
    }

    public List<String> getColumnNames(String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM " + tableName + " LIMIT 0";
            try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    columnNames.add(metaData.getColumnName(i));
                }
            }
        }
        return columnNames;
    }

    public TableData getTableData(String tableName) throws SQLException {
        List<String> headers = new ArrayList<>();
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        String sql = "SELECT * FROM " + tableName + "";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                headers.add(metaData.getColumnName(i));
            }
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.add(rs.getString(i));
                }
                data.add(row);
            }
        }
        return new TableData(headers, data);
    }

    public void insertRow(String tableName, Map<String, String> values) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder placeholders = new StringBuilder();
        for (String colName : values.keySet()) {
            sql.append("").append(colName).append(",");
            placeholders.append("?,");
        }
        sql.setLength(sql.length() - 1);
        placeholders.setLength(placeholders.length() - 1);
        sql.append(") VALUES (").append(placeholders).append(")");
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int i = 1;
            for (String value : values.values()) {
                pstmt.setString(i++, value);
            }
            pstmt.executeUpdate();
        }
    }

    public int executeUpdateOrDelete(String sql) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    public TableData executeGenericQuery(String sql) throws SQLException {
        List<String> headers = new ArrayList<>();
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                headers.add(metaData.getColumnName(i));
            }
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.add(rs.getString(i));
                }
                data.add(row);
            }
        }
        return new TableData(headers, data);
    }

    public void updateCellValue(String tableName, String columnName, String newValue, String pkColumn, String pkValue) throws SQLException {
        String sql = "UPDATE " + tableName + " SET " + columnName + " = ? WHERE " + pkColumn + " = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newValue);
            pstmt.setString(2, pkValue);
            pstmt.executeUpdate();
        }
    }

    // THIS IS THE METHOD THAT PERFORMS THE MULTI-ROW DELETE
    public void deleteMultipleRows(String tableName, String pkColumnName, List<String> pkValues) throws SQLException {
        if (pkValues == null || pkValues.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(pkValues.size(), "?"));
        String sql = "DELETE FROM " + tableName + " WHERE " + pkColumnName + " IN (" + placeholders + ")";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < pkValues.size(); i++) {
                pstmt.setString(i + 1, pkValues.get(i));
            }
            pstmt.executeUpdate();
        }
    }
}