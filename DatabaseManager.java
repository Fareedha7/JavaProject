import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DatabaseManager {
    // Constants remain the same
    private static final String DB_URL = "jdbc:mysql://localhost:3306/fare?createDatabaseIfNotExist=true&allowMultiQueries=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Fareedha@1234"; // Replace with your actual password if needed
    private static final String SETUP_FILE = "javafiles.txt";
    private static final String INSERT_FILE = "dept.txt";

    // The run method now correctly uses the passed-in scanner and manages resources properly.
    public static void run(Scanner scanner) {
        // Use try-with-resources to ensure the connection is always closed.
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            System.out.println("✅ Database connection successful.");
            boolean keepRunning = true;

            while (keepRunning) {
                System.out.println("\nChoose operation: SETUP / INSERT / READ / UPDATE / DELETE / EXIT");
                // Use the shared scanner from MainApplication
                String command = scanner.nextLine().trim().toUpperCase();

                switch (command) {
                    case "SETUP":
                        handleSetup(connection);
                        break;
                    case "INSERT":
                        handleInsert(scanner, connection);
                        break;
                    case "READ":
                        handleRead(scanner, connection);
                        break;
                    case "UPDATE":
                        handleUpdate(scanner, connection);
                        break;
                    case "DELETE":
                        handleDelete(scanner, connection);
                        break;
                    case "EXIT":
                        System.out.println("Returning to Main Menu...");
                        keepRunning = false; // This will exit the loop and the method.
                        break;
                    default:
                        System.err.println("Invalid option. Please try again.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An application error occurred: " + e.getMessage());
            e.printStackTrace();
        }
        // DO NOT close the scanner here.
    }

    private static void handleSetup(Connection connection) {
        System.out.println("Attempting to set up database from '" + SETUP_FILE + "'...");
        StringBuilder sqlBuilder = new StringBuilder();

        // Use try-with-resources for the BufferedReader
        try (BufferedReader reader = new BufferedReader(new FileReader(SETUP_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.contains("--")) {
                    trimmedLine = trimmedLine.substring(0, trimmedLine.indexOf("--")).trim();
                }
                if (!trimmedLine.isEmpty()) {
                    sqlBuilder.append(trimmedLine).append(" ");
                }
            }
        } catch (IOException e) {
            System.err.println("Error: Could not read file '" + SETUP_FILE + "'. " + e.getMessage());
            return;
        }

        String[] queries = sqlBuilder.toString().split(";");
        List<String> createdTables = new ArrayList<>();

        // Use try-with-resources for the Statement
        try (Statement stmt = connection.createStatement()) {
            for (String query : queries) {
                String trimmedQuery = query.trim();
                if (!trimmedQuery.isEmpty()) {
                    stmt.executeUpdate(trimmedQuery);
                    if (trimmedQuery.toUpperCase().startsWith("CREATE TABLE")) {
                        createdTables.add(extractTableName(trimmedQuery));
                    }
                }
            }
            System.out.println("\n✅ Database setup complete.");
            if (!createdTables.isEmpty()) {
                System.out.println("Tables created/verified: " + String.join(", ", createdTables));
            }
        } catch (SQLException e) {
            System.err.println("\n--- ERROR DURING DATABASE SETUP ---\nMessage: " + e.getMessage());
        }
    }

    private static void handleInsert(Scanner scanner, Connection connection) {
        System.out.println("Which table do you want to insert into? (e.g., DEPT, EMP)");
        String tableName = scanner.nextLine().trim().toUpperCase();
        String primaryKeyCol = getPrimaryKeyColumn(tableName);

        // For simplicity, this example assumes you're inserting from the file.
        // A more robust version might ask for values directly.
        System.out.println("Reading insert statements from '" + INSERT_FILE + "' for table " + tableName);
        
        int rowsAffected = 0;
        // Use try-with-resources for file reader and statement
        try (BufferedReader reader = new BufferedReader(new FileReader(INSERT_FILE));
             Statement stmt = connection.createStatement()) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String sql = line.trim();
                if (sql.toUpperCase().startsWith("INSERT INTO " + tableName)) {
                    // This logic prevents inserting duplicate primary keys if the file is run multiple times
                    String pkValue = extractPrimaryKeyValue(sql, tableName);
                    if (primaryKeyCol == null || pkValue == null || !recordExists(connection, tableName, primaryKeyCol, pkValue)) {
                        rowsAffected += stmt.executeUpdate(sql);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading insert file '" + INSERT_FILE + "': " + e.getMessage());
            return;
        } catch (SQLException e) {
            System.err.println("Error during insert: " + e.getMessage());
            return;
        }

        if (rowsAffected > 0) {
            System.out.println("✅ Successfully inserted " + rowsAffected + " new record(s) into " + tableName + ".");
        } else {
            System.out.println("No new records were inserted. They may already exist.");
        }
    }

    private static void handleRead(Scanner scanner, Connection connection) {
        System.out.println("Enter table name to view data:");
        String tableName = scanner.nextLine().trim();
        String sql = "SELECT * FROM " + tableName;

        // Use nested try-with-resources for Statement and ResultSet
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            printTable(rs);
        } catch (SQLException e) {
            System.err.println("Error reading data from table " + tableName + ": " + e.getMessage());
        }
    }

    private static void handleUpdate(Scanner scanner, Connection connection) {
        System.out.println("Enter table name to update (e.g., DEPT, EMP):");
        String tableName = scanner.nextLine().trim().toUpperCase();
        String primaryKeyCol = getPrimaryKeyColumn(tableName);

        if (primaryKeyCol == null) {
            System.err.println("Invalid table or table not supported for update.");
            return;
        }

        System.out.println("Enter " + primaryKeyCol + " of the record to update:");
        String pkValue = scanner.nextLine().trim();
        System.out.println("Enter column to update:");
        String columnToUpdate = scanner.nextLine().trim();
        System.out.println("Enter new value for " + columnToUpdate + ":");
        String newValue = scanner.nextLine().trim();

        String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", tableName, columnToUpdate, primaryKeyCol);

        // Use try-with-resources for PreparedStatement
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newValue);
            pstmt.setString(2, pkValue);
            int rowsAffected = pstmt.executeUpdate();
            System.out.println(rowsAffected > 0 ? "✅ Record updated successfully." : "Record not found or no changes made.");
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
        }
    }
    
    // ... other helper methods (handleDelete, printTable, etc.) would be refactored similarly ...
    // For brevity, I'll include the original logic with better variable names.
    // The key is to use try-with-resources where applicable.
    
    private static void handleDelete(Scanner scanner, Connection connection) {
        System.out.println("Enter table name to delete from (e.g., DEPT, EMP):");
        String tableName = scanner.nextLine().trim().toUpperCase();
        String primaryKeyCol = getPrimaryKeyColumn(tableName);

        if (primaryKeyCol == null) {
            System.err.println("Invalid table or table not supported for deletion.");
            return;
        }
        
        System.out.println("Enter " + primaryKeyCol + " value to delete:");
        String pkValue = scanner.nextLine().trim();
        
        if (hasDependentRecords(connection, tableName, pkValue)) {
            System.err.println("Cannot delete. This record is referenced by other records (foreign key constraint).");
            return;
        }
        
        String sql = String.format("DELETE FROM %s WHERE %s = ?", tableName, primaryKeyCol);
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, pkValue);
            int rowsAffected = pstmt.executeUpdate();
            System.out.println(rowsAffected > 0 ? "✅ Record deleted successfully." : "Record not found.");
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
        }
    }
    
    private static void printTable(ResultSet rs) throws SQLException {
        // This method is complex but correct. We'll just clean up the variables.
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<List<String>> allRows = new ArrayList<>();

        // Read all rows into memory
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                row.add(value != null ? value : "NULL");
            }
            allRows.add(row);
        }

        if (allRows.isEmpty()) {
            System.out.println("(No records found in this table)");
            return;
        }
        
        // Calculate column widths
        List<Integer> columnWidths = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            int width = metaData.getColumnName(i).length();
            for (List<String> row : allRows) {
                width = Math.max(width, row.get(i - 1).length());
            }
            columnWidths.add(width);
        }

        // Print header
        for (int i = 0; i < columnCount; i++) {
            String format = "%-" + (columnWidths.get(i) + 2) + "s";
            System.out.printf(format, metaData.getColumnName(i + 1));
        }
        System.out.println();

        // Print separator line
        for (int width : columnWidths) {
            for (int i = 0; i < width + 2; i++) {
                System.out.print("-");
            }
        }
        System.out.println();

        // Print rows
        for (List<String> row : allRows) {
            for (int i = 0; i < columnCount; i++) {
                String format = "%-" + (columnWidths.get(i) + 2) + "s";
                System.out.printf(format, row.get(i));
            }
            System.out.println();
        }
    }
    
    // The rest of the helper methods are mostly fine, just renamed vars for clarity
    private static String extractTableName(String createQuery) {
        String upperQuery = createQuery.toUpperCase();
        int tableIndex = upperQuery.indexOf("TABLE");
        int bracketIndex = upperQuery.indexOf("(");
        String name = createQuery.substring(tableIndex + 5, bracketIndex).trim();
        if (name.toUpperCase().startsWith("IF NOT EXISTS")) {
            name = name.substring(13).trim();
        }
        return name;
    }

    private static String getPrimaryKeyColumn(String tableName) {
        switch (tableName.toUpperCase()) {
            case "DEPT": return "DEPTNO";
            case "EMP": return "EMPNO";
            case "BONUS": return "ENAME";
            case "SALGRADE": return "GRADE";
            default: return null;
        }
    }
    
    private static String extractPrimaryKeyValue(String insertSql, String tableName) {
        try {
            String[] parts = insertSql.split("VALUES");
            if (parts.length < 2) return null;
            
            String valuesPart = parts[1].replaceAll("[()']", "").trim();
            String[] values = valuesPart.split(",");
            
            switch (tableName.toUpperCase()) {
                case "DEPT":
                case "EMP":
                case "SALGRADE":
                    return values.length >= 1 ? values[0].trim() : null;
                case "BONUS":
                    // Assuming BONUS primary key might be different, as in original
                    return values.length >= 2 ? values[1].trim() : null; 
                default:
                    return null;
            }
        } catch (Exception e) {
            return null; // A problem occurred during parsing
        }
    }
    
    private static boolean recordExists(Connection connection, String tableName, String pkColumn, String pkValue) throws SQLException {
        String sql = String.format("SELECT 1 FROM %s WHERE %s = ?", tableName, pkColumn);
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, pkValue);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private static boolean hasDependentRecords(Connection connection, String tableName, String pkValue) {
        String sql = null;
        switch (tableName.toUpperCase()) {
            case "DEPT":
                sql = "SELECT 1 FROM EMP WHERE DEPTNO = ?";
                break;
            case "EMP":
                sql = "SELECT 1 FROM EMP WHERE MGR = ?";
                break;
            default:
                return false; // No known dependencies
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // This might fail if the pkValue is not an integer, which is a potential bug in the original
            pstmt.setObject(1, pkValue); 
            try(ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException | NumberFormatException e) {
            // If query fails, assume dependency exists to be safe
            return true;
        }
    }
}