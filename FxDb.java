import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class FxDb extends Application {
    private Stage primaryStage;
    private ListView<String> tableListView;
    private TableView<ObservableList<String>> dataTableView;
    private TextArea logArea;
    private Label currentTableLabel;
    private VBox insertForm;
    private TabPane actionTabPane;
    private TextField updateSetField, updateWhereField;
    private TextField deleteWhereField;
    private TextArea customSqlArea;
    private TableView<ColumnDefinition> createTableDefView;
    private TextField newTableNameField;
    private ComboBox<String> dropColumnComboBox;
    private Button dropColumnButton;
    private final Map<ObservableList<String>, SimpleBooleanProperty> rowSelectionMap = new HashMap<>();
    private final DatabaseHelper dbHelper = new DatabaseHelper();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("JavaFX Dynamic DB Manager");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel());
        root.setRight(createRightPanel());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setText("Welcome! Connect to the database and select a table to begin.\n");
        root.setBottom(logArea);

        // --- DYNAMIC WINDOW SIZING LOGIC ---
        // Get the primary screen's visual bounds (the usable area excluding the taskbar)
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

        // Set the scene size to 85% of the screen's width and height for a good fit
        double sceneWidth = screenBounds.getWidth() * 0.85;
        double sceneHeight = screenBounds.getHeight() * 0.85;

        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        // --- END OF DYNAMIC SIZING ---

        primaryStage.setScene(scene);
        primaryStage.show();

        refreshTableList();
    }

    private TabPane createRightPanel() {
        actionTabPane = new TabPane();
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab createTab = new Tab("Create Table", createCreateTableTab());
        Tab insertTab = new Tab("Insert", createInsertTab());
        Tab updateTab = new Tab("Update", createUpdateTab());
        Tab deleteTab = new Tab("Delete", createDeleteTab());
        Tab structureTab = new Tab("Structure", createStructureTab());
        Tab sqlTab = new Tab("Execute SQL", createSqlTab());
        actionTabPane.getTabs().addAll(createTab, insertTab, updateTab, deleteTab, structureTab, sqlTab);
        actionTabPane.setPrefWidth(500);
        return actionTabPane;
    }

    private VBox createCenterPanel() {
        currentTableLabel = new Label("No Table Selected");
        currentTableLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button deleteSelectedButton = new Button("Delete Selected Rows");
        deleteSelectedButton.setOnAction(e -> handleDeleteSelectedRows());
        deleteSelectedButton.setStyle("-fx-background-color: #ff8c8c; -fx-text-fill: white; -fx-font-weight: bold;");
        HBox topBar = new HBox(20, currentTableLabel, deleteSelectedButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        dataTableView = new TableView<>();
        dataTableView.setEditable(true);
        dataTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        dataTableView.setPlaceholder(new Label("Select a table from the list on the left to view its data."));
        VBox centerPanel = new VBox(10, topBar, dataTableView);
        centerPanel.setPadding(new Insets(10));
        VBox.setVgrow(dataTableView, Priority.ALWAYS);
        return centerPanel;
    }

    @SuppressWarnings("unchecked")
    private void loadTableData(String tableName) {
        try {
            dataTableView.getColumns().clear();
            dataTableView.getItems().clear();
            rowSelectionMap.clear();
            DatabaseHelper.TableData tableData = dbHelper.getTableData(tableName);
            TableColumn<ObservableList<String>, Boolean> selectCol = new TableColumn<>("Select");
            selectCol.setCellValueFactory(cellData -> rowSelectionMap.computeIfAbsent(cellData.getValue(), k -> new SimpleBooleanProperty(false)));
            selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
            selectCol.setEditable(true);
            selectCol.setPrefWidth(50);
            dataTableView.getColumns().add(selectCol);
            for (int i = 0; i < tableData.headers.size(); i++) {
                final int colIndex = i;
                TableColumn<ObservableList<String>, String> column = new TableColumn<>(tableData.headers.get(i));
                column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(colIndex)));
                column.setCellFactory(TextFieldTableCell.forTableColumn());
                column.setOnEditCommit(event -> {
                    try {
                        ObservableList<String> row = event.getRowValue();
                        row.set(colIndex, event.getNewValue());
                        String primaryKeyColumn = tableData.headers.get(0);
                        String primaryKeyValue = row.get(0);
                        dbHelper.updateCellValue(tableName, tableData.headers.get(colIndex), event.getNewValue(), primaryKeyColumn, primaryKeyValue);
                        log("Updated cell in '" + tableName + "'.");
                    } catch (SQLException e) {
                        showError("Update Error", "Could not update the cell in the database.", e.getMessage());
                        loadTableData(tableName);
                    }
                });
                column.setPrefWidth(120);
                dataTableView.getColumns().add(column);
            }
            dataTableView.setItems(tableData.rows);
            log("Displayed data for table '" + tableName + "'. Found " + tableData.rows.size() + " rows.");
        } catch (SQLException e) {
            showError("Data Load Error", "Could not load data for table '" + tableName + "'.", e.getMessage());
            log("Error loading data for '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleDeleteSelectedRows() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        List<ObservableList<String>> rowsToDelete = rowSelectionMap.entrySet().stream()
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (rowsToDelete.isEmpty()) {
            showError("No Selection", "No rows selected.", "Please check the boxes next to the rows you wish to delete.");
            return;
        }
        try {
            String pkColumnName = dbHelper.getColumnNames(tableName).get(0);
            List<String> pkValues = rowsToDelete.stream()
                    .map(row -> row.get(0))
                    .collect(Collectors.toList());
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Deletion");
            confirm.setHeaderText("Delete " + pkValues.size() + " row(s) from table '" + tableName + "'?");
            confirm.setContentText("This action cannot be undone. The rows with " + pkColumnName + " in (" + String.join(", ", pkValues) + ") will be deleted.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                dbHelper.deleteMultipleRows(tableName, pkColumnName, pkValues);
                log("Successfully deleted " + pkValues.size() + " rows from '" + tableName + "'.");
                loadTableData(tableName);
            }
        } catch (Exception e) {
            showError("Deletion Error", "Could not delete the selected rows.", e.getMessage());
            log("Error deleting rows: " + e.getMessage());
        }
    }

    // --- The rest of the methods are unchanged ---

    private VBox createLeftPanel() {
        Label label = new Label("Database Tables");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        tableListView = new ListView<>();
        Button refreshBtn = new Button("Refresh List");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> refreshTableList());
        VBox leftPanel = new VBox(10, label, tableListView, refreshBtn);
        tableListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadTableData(newVal);
                updateActionPanelForTable(newVal);
            }
        });
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(200);
        VBox.setVgrow(tableListView, Priority.ALWAYS);
        return leftPanel;
    }
    
    @SuppressWarnings("unchecked")
    private VBox createCreateTableTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));
        Label title = new Label("Create New Table");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        newTableNameField = new TextField();
        HBox tableNameBox = new HBox(10, new Label("Table Name:"), newTableNameField);
        tableNameBox.setAlignment(Pos.CENTER_LEFT);
        createTableDefView = new TableView<>();
        createTableDefView.setEditable(true);
        createTableDefView.setPrefHeight(350);
        createTableDefView.setItems(FXCollections.observableArrayList(new ColumnDefinition("id", "INT", "11", true, true, true)));
        ObservableList<String> dataTypes = FXCollections.observableArrayList("INT", "VARCHAR", "TEXT", "DATE", "DATETIME", "DECIMAL", "BOOLEAN", "DOUBLE", "TIMESTAMP", "BIGINT", "FLOAT", "BLOB");
        TableColumn<ColumnDefinition, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));
        TableColumn<ColumnDefinition, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("dataType"));
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(dataTypes));
        typeCol.setOnEditCommit(e -> e.getRowValue().setDataType(e.getNewValue()));
        TableColumn<ColumnDefinition, String> sizeCol = new TableColumn<>("Size/Len");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        sizeCol.setOnEditCommit(e -> e.getRowValue().setSize(e.getNewValue()));
        TableColumn<ColumnDefinition, Boolean> pkCol = new TableColumn<>("PK");
        pkCol.setCellValueFactory(cellData -> cellData.getValue().primaryKeyProperty());
        pkCol.setCellFactory(CheckBoxTableCell.forTableColumn(pkCol));
        TableColumn<ColumnDefinition, Boolean> nnCol = new TableColumn<>("Not Null");
        nnCol.setCellValueFactory(cellData -> cellData.getValue().notNullProperty());
        nnCol.setCellFactory(CheckBoxTableCell.forTableColumn(nnCol));
        TableColumn<ColumnDefinition, Boolean> aiCol = new TableColumn<>("Auto Incr");
        aiCol.setCellValueFactory(cellData -> cellData.getValue().autoIncrementProperty());
        aiCol.setCellFactory(CheckBoxTableCell.forTableColumn(aiCol));
        createTableDefView.getColumns().addAll(nameCol, typeCol, sizeCol, pkCol, nnCol, aiCol);
        createTableDefView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Button addColBtn = new Button("Add Column");
        addColBtn.setOnAction(e -> createTableDefView.getItems().add(new ColumnDefinition("", "VARCHAR", "255", false, false, false)));
        Button removeColBtn = new Button("Remove Selected");
        removeColBtn.setOnAction(e -> {
            ColumnDefinition selected = createTableDefView.getSelectionModel().getSelectedItem();
            if (selected != null) createTableDefView.getItems().remove(selected);
        });
        HBox colButtons = new HBox(10, addColBtn, removeColBtn);
        Button executeCreateBtn = new Button("Execute CREATE TABLE");
        executeCreateBtn.setMaxWidth(Double.MAX_VALUE);
        executeCreateBtn.setStyle("-fx-font-weight: bold;");
        executeCreateBtn.setOnAction(e -> handleCreateTable());
        container.getChildren().addAll(title, tableNameBox, createTableDefView, colButtons, new Separator(), executeCreateBtn);
        return container;
    }

    private void handleCreateTable() {
        String tableName = newTableNameField.getText();
        if (tableName == null || tableName.trim().isEmpty()) {
            showError("Validation Error", "Table Name is required.", "Please enter a name for the new table.");
            return;
        }
        ObservableList<ColumnDefinition> columns = createTableDefView.getItems();
        if (columns.isEmpty()) {
            showError("Validation Error", "At least one column is required.", "Please define one or more columns for the table.");
            return;
        }
        try {
            String sql = generateCreateTableSql(tableName, columns);
            log("Generated SQL:\n" + sql);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm SQL Execution");
            confirm.setHeaderText("Execute the following SQL statement?");
            confirm.setContentText(sql);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                dbHelper.executeUpdateOrDelete(sql);
                log("SUCCESS: Table '" + tableName + "' created successfully.");
                newTableNameField.clear();
                createTableDefView.getItems().setAll(new ColumnDefinition("id", "INT", "11", true, true, true));
                refreshTableList();
            } else {
                log("Create table operation cancelled by user.");
            }
        } catch (Exception e) {
            showError("SQL Generation Error", "Could not create the SQL for the table.", e.getMessage());
            log("Error during table creation: " + e.getMessage());
        }
    }

    private String generateCreateTableSql(String tableName, List<ColumnDefinition> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tableName.trim()).append(" (\n");
        List<String> columnDefs = new ArrayList<>();
        for (ColumnDefinition col : columns) {
            if (col.getName() == null || col.getName().trim().isEmpty()) continue;
            StringBuilder colDef = new StringBuilder();
            colDef.append("  ").append(col.getName()).append(" ").append(col.getDataType());
            if (col.getSize() != null && !col.getSize().trim().isEmpty() && col.getDataType().contains("CHAR")) {
                colDef.append("(").append(col.getSize()).append(")");
            }
            if (col.isNotNull()) colDef.append(" NOT NULL");
            if (col.isAutoIncrement()) colDef.append(" AUTO_INCREMENT");
            columnDefs.add(colDef.toString());
        }
        sql.append(String.join(",\n", columnDefs));
        List<String> pkColumns = columns.stream().filter(ColumnDefinition::isPrimaryKey).map(c -> "" + c.getName() + "").collect(Collectors.toList());
        if (!pkColumns.isEmpty()) {
            sql.append(",\n  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
        }
        sql.append("\n) ENGINE=InnoDB;");
        return sql.toString();
    }

    private VBox createStructureTab() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(15));
        container.setAlignment(Pos.TOP_LEFT);
        Label dropTitle = new Label("Drop Column");
        dropTitle.setStyle("-fx-font-weight: bold;");
        dropColumnComboBox = new ComboBox<>();
        dropColumnComboBox.setPromptText("Select column to drop");
        dropColumnComboBox.setMaxWidth(Double.MAX_VALUE);
        dropColumnButton = new Button("Drop Selected Column");
        dropColumnButton.setMaxWidth(Double.MAX_VALUE);
        dropColumnButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white;");
        dropColumnButton.setOnAction(e -> handleDropColumn());
        VBox dropBox = new VBox(10, dropTitle, dropColumnComboBox, dropColumnButton);
        container.getChildren().addAll(dropBox);
        return container;
    }
    
    private void handleDropColumn() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String columnToDrop = dropColumnComboBox.getSelectionModel().getSelectedItem();
        if (columnToDrop == null || columnToDrop.isEmpty()) {
            showError("No Selection", "No column selected.", "Please select a column from the dropdown list to drop.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm DROP COLUMN");
        confirm.setHeaderText("PERMANENTLY DELETE column '" + columnToDrop + "' from table '" + tableName + "'?");
        confirm.setContentText("This is a destructive operation and cannot be undone. All data in this column will be lost.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnToDrop;
                dbHelper.executeUpdateOrDelete(sql);
                log("Successfully dropped column '" + columnToDrop + "' from '" + tableName + "'.");
                loadTableData(tableName);
                updateActionPanelForTable(tableName);
            } catch (SQLException e) {
                showError("Drop Column Error", "Could not drop the column.", e.getMessage());
                log("Error dropping column: " + e.getMessage());
            }
        }
    }

    private VBox createInsertTab() {
        insertForm = new VBox(10);
        insertForm.setPadding(new Insets(15));
        insertForm.setAlignment(Pos.TOP_LEFT);
        insertForm.getChildren().add(new Label("Select a table to see insert form."));
        return insertForm;
    }

    private VBox createUpdateTab() {
        Label title = new Label("Update Rows (by WHERE clause)");
        title.setStyle("-fx-font-weight: bold;");
        updateSetField = new TextField();
        updateWhereField = new TextField();
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.add(new Label("SET:"), 0, 0);
        grid.add(updateSetField, 1, 0);
        grid.add(new Label("WHERE:"), 0, 1);
        grid.add(updateWhereField, 1, 1);
        updateSetField.setPromptText("e.g., name = 'New Name', age = 30");
        updateWhereField.setPromptText("e.g., id = 5 (required)");
        Button updateButton = new Button("Execute Update");
        updateButton.setMaxWidth(Double.MAX_VALUE);
        updateButton.setOnAction(e -> handleUpdate());
        VBox updateBox = new VBox(15, title, grid, updateButton);
        updateBox.setPadding(new Insets(15));
        return updateBox;
    }

    private VBox createDeleteTab() {
        Label title = new Label("Delete Rows (by WHERE clause)");
        title.setStyle("-fx-font-weight: bold;");
        deleteWhereField = new TextField();
        deleteWhereField.setPromptText("e.g., id > 100 (Leave empty to delete ALL)");
        Button deleteButton = new Button("Execute Delete");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(e -> handleDelete());
        Button dropTableButton = new Button("Drop (Delete) Entire Table");
        dropTableButton.setMaxWidth(Double.MAX_VALUE);
        dropTableButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white;");
        dropTableButton.setOnAction(e -> handleDropTable());
        VBox deleteBox = new VBox(15, title, new Label("WHERE Clause:"), deleteWhereField, deleteButton, new Separator(), dropTableButton);
        deleteBox.setPadding(new Insets(15));
        return deleteBox;
    }

    private VBox createSqlTab() {
        Label title = new Label("Execute Custom SQL");
        title.setStyle("-fx-font-weight: bold;");
        customSqlArea = new TextArea();
        customSqlArea.setPromptText("Enter any SQL command (SELECT, INSERT, CREATE TABLE, etc.)");
        customSqlArea.setPrefRowCount(10);
        Button executeSqlButton = new Button("Execute");
        executeSqlButton.setMaxWidth(Double.MAX_VALUE);
        executeSqlButton.setOnAction(e -> handleExecuteCustomSql());
        Button executeFromFileButton = new Button("Execute from File...");
        executeFromFileButton.setMaxWidth(Double.MAX_VALUE);
        executeFromFileButton.setOnAction(e -> handleExecuteSqlFromFile());
        VBox sqlBox = new VBox(15, title, customSqlArea, executeSqlButton, executeFromFileButton);
        sqlBox.setPadding(new Insets(15));
        return sqlBox;
    }

    private void refreshTableList() {
        try {
            List<String> tableNames = dbHelper.getTableNames();
            tableListView.setItems(FXCollections.observableArrayList(tableNames));
            log("Successfully fetched table list from the database.");
        } catch (SQLException e) {
            showError("Database Error", "Could not fetch table list.", e.getMessage());
            log("Error fetching table list: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void updateActionPanelForTable(String tableName) {
        currentTableLabel.setText("Table: " + tableName);
        insertForm.getChildren().clear();
        GridPane insertGrid = new GridPane();
        insertGrid.setHgap(10);
        insertGrid.setVgap(10);
        try {
            List<String> columnNames = dbHelper.getColumnNames(tableName);
            dropColumnComboBox.setItems(FXCollections.observableArrayList(columnNames));
            dropColumnComboBox.getSelectionModel().clearSelection();
            for (int i = 0; i < columnNames.size(); i++) {
                Label label = new Label(columnNames.get(i) + ":");
                TextField field = new TextField();
                field.setId("insertField_" + columnNames.get(i));
                insertGrid.add(label, 0, i);
                insertGrid.add(field, 1, i);
            }
            Button insertButton = new Button("Insert New Row");
            insertButton.setMaxWidth(Double.MAX_VALUE);
            insertButton.setOnAction(e -> handleInsert(tableName, columnNames, insertGrid));
            insertForm.getChildren().addAll(insertGrid, insertButton);
        } catch (SQLException e) {
            insertForm.getChildren().add(new Label("Error loading form: " + e.getMessage()));
            log("Error creating insert form for '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleInsert(String tableName, List<String> columnNames, GridPane grid) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String colName : columnNames) {
            TextField field = (TextField) grid.lookup("#insertField_" + colName);
            if (field.getText() != null && !field.getText().isEmpty()) {
                values.put(colName, field.getText());
            }
        }
        if (values.isEmpty()) {
            showError("Insert Error", "No values provided.", "Please enter data in at least one field.");
            return;
        }
        try {
            dbHelper.insertRow(tableName, values);
            log("Successfully inserted a new row into '" + tableName + "'.");
            loadTableData(tableName);
        } catch (SQLException e) {
            showError("Insert Error", "Could not insert the new row.", e.getMessage());
            log("Error inserting row into '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleUpdate() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String setClause = updateSetField.getText();
        String whereClause = updateWhereField.getText();
        if (setClause.isEmpty() || whereClause.isEmpty()) {
            showError("Update Error", "Both SET and WHERE clauses are required.", "Please provide values for both fields.");
            return;
        }
        try {
            int rowsAffected = dbHelper.executeUpdateOrDelete("UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause);
            log("Update successful. " + rowsAffected + " row(s) affected in '" + tableName + "'.");
            loadTableData(tableName);
        } catch (SQLException e) {
            showError("Update Error", "The SQL update statement failed.", e.getMessage());
            log("Error executing update on '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleDelete() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String whereClause = deleteWhereField.getText();
        if (whereClause.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Full Table Deletion");
            confirm.setHeaderText("You are about to delete ALL rows from table '" + tableName + "'.");
            confirm.setContentText("This action cannot be undone. Are you sure you want to proceed?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                log("Delete operation cancelled by user.");
                return;
            }
        }
        String sql = "DELETE FROM " + tableName + (whereClause.isEmpty() ? "" : " WHERE " + whereClause);
        try {
            int rowsAffected = dbHelper.executeUpdateOrDelete(sql);
            log("Delete successful. " + rowsAffected + " row(s) deleted from '" + tableName + "'.");
            loadTableData(tableName);
        } catch (SQLException e) {
            showError("Delete Error", "The SQL delete statement failed.", e.getMessage());
            log("Error executing delete on '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleDropTable() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm DROP TABLE");
        confirm.setHeaderText("You are about to PERMANENTLY DELETE the entire table '" + tableName + "'.");
        confirm.setContentText("This is a destructive operation and cannot be undone. Are you sure?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                dbHelper.executeUpdateOrDelete("DROP TABLE " + tableName);
                log("Table '" + tableName + "' was successfully dropped.");
                refreshTableList();
                dataTableView.getColumns().clear();
                dataTableView.getItems().clear();
                currentTableLabel.setText("No Table Selected");
            } catch (SQLException e) {
                showError("Drop Table Error", "Could not drop table '" + tableName + "'.", e.getMessage());
                log("Error dropping table '" + tableName + "': " + e.getMessage());
            }
        } else {
            log("Drop table operation cancelled.");
        }
    }

    private void handleExecuteCustomSql() {
        String sql = customSqlArea.getText();
        if (sql.trim().isEmpty()) {
            showError("SQL Error", "No SQL command entered.", "Please type a command in the text area.");
            return;
        }
        try {
            if (sql.trim().toLowerCase().startsWith("select")) {
                DatabaseHelper.TableData resultData = dbHelper.executeGenericQuery(sql);
                displayQueryResult(resultData);
                log("Executed SELECT query. " + resultData.rows.size() + " rows returned.");
            } else {
                int rowsAffected = dbHelper.executeUpdateOrDelete(sql);
                log("Executed non-query command. " + rowsAffected + " row(s) affected.");
                refreshTableList();
            }
        } catch (SQLException e) {
            showError("SQL Execution Error", "The SQL command failed.", e.getMessage());
            log("Error executing custom SQL: " + e.getMessage());
        }
    }

    private void handleExecuteSqlFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open SQL Script File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Files", "*.sql"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                String[] statements = content.split(";\\s*");
                int successCount = 0;
                for (String stmt : statements) {
                    if (!stmt.trim().isEmpty()) {
                        try {
                            dbHelper.executeUpdateOrDelete(stmt);
                            successCount++;
                        } catch (SQLException e) {
                            log("Error in script '" + file.getName() + "': " + e.getMessage() + " [SQL: " + stmt.substring(0, Math.min(50, stmt.length())) + "...]");
                        }
                    }
                }
                log("Executed " + successCount + " statements from '" + file.getName() + "'.");
                refreshTableList();
            } catch (IOException e) {
                showError("File Read Error", "Could not read the selected file.", e.getMessage());
            }
        }
    }

    private String getSelectedTable() {
        String selected = tableListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Table Selected", "You must select a table from the list on the left.", "Please select a table to perform this operation.");
            return null;
        }
        return selected;
    }

    private void displayQueryResult(DatabaseHelper.TableData tableData) {
        dataTableView.getColumns().clear();
        dataTableView.getItems().clear();
        currentTableLabel.setText("Custom Query Result");
        actionTabPane.getSelectionModel().select(0);
        for (int i = 0; i < tableData.headers.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(tableData.headers.get(i));
            column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(colIndex)));
            column.setEditable(false);
            dataTableView.getColumns().add(column);
        }
        dataTableView.setItems(tableData.rows);
    }

    private void log(String message) {
        logArea.appendText(message + "\n");
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    public static class ColumnDefinition {
        private final SimpleStringProperty name;
        private final SimpleStringProperty dataType;
        private final SimpleStringProperty size;
        private final SimpleBooleanProperty primaryKey;
        private final SimpleBooleanProperty notNull;
        private final SimpleBooleanProperty autoIncrement;

        public ColumnDefinition(String name, String dataType, String size, boolean pk, boolean nn, boolean ai) {
            this.name = new SimpleStringProperty(name);
            this.dataType = new SimpleStringProperty(dataType);
            this.size = new SimpleStringProperty(size);
            this.primaryKey = new SimpleBooleanProperty(pk);
            this.notNull = new SimpleBooleanProperty(nn);
            this.autoIncrement = new SimpleBooleanProperty(ai);
        }
        public String getName() { return name.get(); }
        public void setName(String name) { this.name.set(name); }
        public SimpleStringProperty nameProperty() { return name; }
        public String getDataType() { return dataType.get(); }
        public void setDataType(String dataType) { this.dataType.set(dataType); }
        public SimpleStringProperty dataTypeProperty() { return dataType; }
        public String getSize() { return size.get(); }
        public void setSize(String size) { this.size.set(size); }
        public SimpleStringProperty sizeProperty() { return size; }
        public boolean isPrimaryKey() { return primaryKey.get(); }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey.set(primaryKey); }
        public SimpleBooleanProperty primaryKeyProperty() { return primaryKey; }
        public boolean isNotNull() { return notNull.get(); }
        public void setNotNull(boolean notNull) { this.notNull.set(notNull); }
        public SimpleBooleanProperty notNullProperty() { return notNull; }
        public boolean isAutoIncrement() { return autoIncrement.get(); }
        public void setAutoIncrement(boolean autoIncrement) { this.autoIncrement.set(autoIncrement); }
        public SimpleBooleanProperty autoIncrementProperty() { return autoIncrement; }
    }
}