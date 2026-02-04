package com.hisabx.controller;

import com.hisabx.model.Receipt;
import com.hisabx.model.Sale;
import com.hisabx.model.Customer;
import com.hisabx.service.CustomerService;
import com.hisabx.service.AuthService;
import com.hisabx.service.ReceiptService;
import com.hisabx.util.TabManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReceiptListController {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptListController.class);

    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TreeTableView<ReceiptTreeRow> receiptsTable;
    @FXML private TreeTableColumn<ReceiptTreeRow, String> receiptNumberColumn;
    @FXML private TreeTableColumn<ReceiptTreeRow, String> customerColumn;
    @FXML private TreeTableColumn<ReceiptTreeRow, String> dateColumn;
    @FXML private TreeTableColumn<ReceiptTreeRow, String> totalColumn;
    @FXML private TreeTableColumn<ReceiptTreeRow, String> printedColumn;
    @FXML private TreeTableColumn<ReceiptTreeRow, Void> actionsColumn;
    @FXML private Label totalReceiptsLabel;
    @FXML private Label printedCountLabel;
    @FXML private Label todayCountLabel;

    private final ReceiptService receiptService;
    private final CustomerService customerService;
    private final AuthService authService;
    private ObservableList<Receipt> allReceipts;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private com.hisabx.MainApp mainApp;
    private boolean tabMode = false;
    private String tabId;

    public void setMainApp(com.hisabx.MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public ReceiptListController() {
        this.receiptService = new ReceiptService();
        this.customerService = new CustomerService();
        this.authService = new AuthService();
    }

    @FXML
    private void initialize() {
        setupTable();
        setupFilters();
        loadReceipts();
    }

    private void setupTable() {
        receiptNumberColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getValue() != null
                        ? data.getValue().getValue().getTreeText()
                        : ""));
        customerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getValue() != null
                        ? data.getValue().getValue().getCustomerName()
                        : ""));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getValue() != null
                        ? data.getValue().getValue().getDateText(dateFormatter)
                        : ""));
        totalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getValue() != null
                        ? data.getValue().getValue().getTotalText()
                        : ""));
        printedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue() != null && data.getValue().getValue() != null
                        ? data.getValue().getValue().getPrintedText()
                        : ""));

        printedColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String printed, boolean empty) {
                super.updateItem(printed, empty);
                if (empty || printed == null || printed.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(printed);
                    if ("✓".equals(printed)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 16px;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 16px;");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(col -> new TreeTableCell<>() {
            private final Button viewBtn = new Button("👁");
            private final Button printBtn = new Button("🖨");
            private final Button deleteBtn = new Button("🗑");
            private final javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, viewBtn, printBtn, deleteBtn);

            {
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                printBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

                viewBtn.setTooltip(new Tooltip("عرض الوصل"));
                printBtn.setTooltip(new Tooltip("طباعة"));
                deleteBtn.setTooltip(new Tooltip("حذف"));

                viewBtn.setOnAction(e -> {
                    Receipt r = getReceiptForCurrentRow();
                    if (r != null) handleViewReceipt(r);
                });
                printBtn.setOnAction(e -> {
                    Receipt r = getReceiptForCurrentRow();
                    if (r != null) handlePrintReceipt(r);
                });
                deleteBtn.setOnAction(e -> {
                    Receipt r = getReceiptForCurrentRow();
                    if (r != null) handleDeleteReceipt(r);
                });
            }

            private Receipt getReceiptForCurrentRow() {
                TreeTableRow<ReceiptTreeRow> row = getTreeTableRow();
                ReceiptTreeRow v = row != null ? row.getItem() : null;
                return v != null && v.getType() == RowType.RECEIPT ? v.getReceipt() : null;
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                TreeTableRow<ReceiptTreeRow> row = getTreeTableRow();
                ReceiptTreeRow v = row != null ? row.getItem() : null;
                setGraphic(v != null && v.getType() == RowType.RECEIPT ? hbox : null);
            }
        });

        receiptsTable.setRowFactory(tv -> {
            TreeTableRow<ReceiptTreeRow> row = new TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (row.isEmpty()) return;
                ReceiptTreeRow v = row.getItem();
                if (event.getClickCount() == 2) {
                    if (v != null && v.getType() == RowType.RECEIPT && v.getReceipt() != null) {
                        handleViewReceipt(v.getReceipt());
                    }
                    return;
                }
                if (event.getClickCount() == 1) {
                    if (v != null && (v.getType() == RowType.CUSTOMER || v.getType() == RowType.PROJECT)) {
                        TreeItem<ReceiptTreeRow> item = row.getTreeItem();
                        if (item != null) item.setExpanded(!item.isExpanded());
                    }
                }
            });
            return row;
        });

        receiptsTable.setShowRoot(false);
    }

    private void setupFilters() {
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());
    }

    private void loadReceipts() {
        List<Receipt> receipts = receiptService.getAllReceipts();
        allReceipts = FXCollections.observableArrayList(receipts);
        applyFilters();
        updateSummary();
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    @FXML
    private void handleDateFilter() {
        applyFilters();
    }

    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        List<Receipt> filtered = allReceipts.stream()
                .filter(receipt -> {
                    if (!searchText.isEmpty()) {
                        boolean matchesNumber = receipt.getReceiptNumber().toLowerCase().contains(searchText);
                        Sale sale = receipt.getSale();
                        String customerName = sale != null && sale.getCustomer() != null && sale.getCustomer().getName() != null
                            ? sale.getCustomer().getName().toLowerCase()
                            : "";
                        String customerPhone = sale != null && sale.getCustomer() != null && sale.getCustomer().getPhoneNumber() != null
                            ? sale.getCustomer().getPhoneNumber().toLowerCase()
                            : "";
                        String projectLocation = sale != null && sale.getProjectLocation() != null
                            ? sale.getProjectLocation().toLowerCase()
                            : "";

                        boolean matchesCustomer = customerName.contains(searchText);
                        boolean matchesPhone = customerPhone.contains(searchText);
                        boolean matchesProject = projectLocation.contains(searchText);

                        if (!matchesNumber && !matchesCustomer && !matchesPhone && !matchesProject) return false;
                    }

                    if (fromDate != null && receipt.getReceiptDate() != null) {
                        if (receipt.getReceiptDate().toLocalDate().isBefore(fromDate)) return false;
                    }

                    if (toDate != null && receipt.getReceiptDate() != null) {
                        if (receipt.getReceiptDate().toLocalDate().isAfter(toDate)) return false;
                    }

                    return true;
                })
                .toList();

        receiptsTable.setRoot(buildTree(filtered));
        updateSummaryForFiltered(filtered);
    }

    private TreeItem<ReceiptTreeRow> buildTree(List<Receipt> receipts) {
        TreeItem<ReceiptTreeRow> root = new TreeItem<>(ReceiptTreeRow.root());
        if (receipts == null || receipts.isEmpty()) {
            return root;
        }

        Map<Customer, Map<String, List<Receipt>>> grouped = new LinkedHashMap<>();
        for (Receipt r : receipts) {
            Sale sale = r.getSale();
            Customer customer = sale != null ? sale.getCustomer() : null;
            if (customer == null) {
                continue;
            }
            String project = sale != null ? sale.getProjectLocation() : null;
            if (project == null || project.trim().isEmpty()) {
                project = "-";
            }
            grouped
                    .computeIfAbsent(customer, k -> new LinkedHashMap<>())
                    .computeIfAbsent(project, k -> new ArrayList<>())
                    .add(r);
        }

        List<Map.Entry<Customer, Map<String, List<Receipt>>>> customers = new ArrayList<>(grouped.entrySet());
        customers.sort(Comparator.comparing(e -> safeLower(e.getKey() != null ? e.getKey().getName() : null)));

        for (Map.Entry<Customer, Map<String, List<Receipt>>> cEntry : customers) {
            Customer c = cEntry.getKey();
            TreeItem<ReceiptTreeRow> customerItem = new TreeItem<>(ReceiptTreeRow.customer(c));

            List<Map.Entry<String, List<Receipt>>> projects = new ArrayList<>(cEntry.getValue().entrySet());
            projects.sort(Comparator.comparing(e -> safeLower(e.getKey())));

            for (Map.Entry<String, List<Receipt>> pEntry : projects) {
                String project = pEntry.getKey();
                TreeItem<ReceiptTreeRow> projectItem = new TreeItem<>(ReceiptTreeRow.project(c, project));

                List<Receipt> pr = new ArrayList<>(pEntry.getValue());
                pr.sort(Comparator.comparing((Receipt rr) -> rr.getReceiptDate() != null ? rr.getReceiptDate() : java.time.LocalDateTime.MIN).reversed());
                for (Receipt r : pr) {
                    projectItem.getChildren().add(new TreeItem<>(ReceiptTreeRow.receipt(r)));
                }

                customerItem.getChildren().add(projectItem);
            }

            root.getChildren().add(customerItem);
        }

        return root;
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private void updateSummary() {
        updateSummaryForFiltered(allReceipts);
    }

    private void updateSummaryForFiltered(List<Receipt> receipts) {
        int totalCount = receipts.size();
        long printedCount = receipts.stream()
                .filter(r -> r.getIsPrinted() != null && r.getIsPrinted())
                .count();
        long todayCount = receipts.stream()
                .filter(r -> r.getReceiptDate() != null && 
                            r.getReceiptDate().toLocalDate().equals(LocalDate.now()))
                .count();

        totalReceiptsLabel.setText(String.valueOf(totalCount));
        printedCountLabel.setText(String.valueOf(printedCount));
        todayCountLabel.setText(String.valueOf(todayCount));
    }

    private void handleViewReceipt(Receipt receipt) {
        File pdfFile = receipt.getFilePath() != null ? new File(receipt.getFilePath()) : null;
        boolean canOpenExisting = pdfFile != null && pdfFile.exists();

        if (!canOpenExisting) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("ملف الوصل غير موجود");
            alert.setHeaderText("لا يمكن العثور على ملف PDF لهذا الوصل");
            alert.setContentText("هل تريد إعادة إنشاء ملف الوصل الآن؟");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        Receipt updated = receiptService.regenerateReceiptPdf(receipt.getId(), "System");
                        if (updated.getFilePath() != null) {
                            File regenerated = new File(updated.getFilePath());
                            if (regenerated.exists()) {
                                if (mainApp != null) {
                                    mainApp.showPdfPreview(regenerated);
                                } else if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(regenerated);
                                }
                                loadReceipts();
                                return;
                            }
                        }
                        showError("خطأ", "تمت إعادة الإنشاء لكن لم يتم العثور على الملف");
                    } catch (Exception e) {
                        logger.error("Failed to regenerate receipt PDF", e);
                        showError("خطأ", "فشل في إعادة إنشاء الوصل: " + e.getMessage());
                    }
                }
            });
            return;
        }

        if (mainApp != null) {
            mainApp.showPdfPreview(pdfFile);
        } else {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pdfFile);
                }
            } catch (Exception e) {
                logger.error("Failed to open PDF", e);
                showError("خطأ", "فشل في فتح ملف PDF");
            }
        }
    }

    private void handlePrintReceipt(Receipt receipt) {
        if (receipt.getFilePath() != null) {
            File pdfFile = new File(receipt.getFilePath());
            if (pdfFile.exists()) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().print(pdfFile);
                        showSuccess("تمت الطباعة", "تم إرسال الوصل للطباعة");
                    }
                } catch (Exception e) {
                    logger.error("Failed to print PDF", e);
                    showError("خطأ", "فشل في طباعة الوصل");
                }
            } else {
                showError("خطأ", "ملف الوصل غير موجود، يرجى إعادة إنشاء الوصل");
            }
        } else {
            showError("خطأ", "لا يوجد ملف PDF للوصل");
        }
    }

    private void handleDeleteReceipt(Receipt receipt) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الحذف");
        alert.setHeaderText("هل تريد حذف هذا الوصل؟");
        alert.setContentText("رقم الوصل: " + receipt.getReceiptNumber());

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Dialog<String> pinDialog = new Dialog<>();
                    pinDialog.setTitle("تأكيد الأدمن");
                    pinDialog.setHeaderText("أدخل رمز PIN الأدمن لتأكيد الحذف");
                    ButtonType okBtn = new ButtonType("تأكيد", ButtonBar.ButtonData.OK_DONE);
                    pinDialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

                    PasswordField pinField = new PasswordField();
                    pinField.setPromptText("PIN");
                    pinDialog.getDialogPane().setContent(pinField);

                    pinDialog.setResultConverter(btn -> btn == okBtn ? pinField.getText() : null);

                    var pinResult = pinDialog.showAndWait();
                    if (pinResult.isEmpty()) {
                        return;
                    }

                    String pin = pinResult.get();
                    if (!authService.verifyAdminPin(pin)) {
                        showError("غير مسموح", "رمز الأدمن غير صحيح");
                        return;
                    }

                    receiptService.deleteReceipt(receipt.getId());
                    loadReceipts();
                    showSuccess("تم الحذف", "تم حذف الوصل بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to delete receipt", e);
                    showError("خطأ", "فشل في حذف الوصل");
                }
            }
        });
    }

    @FXML
    private void handleAccountStatement() {
        List<Customer> customers;
        try {
            customers = customerService.getAllCustomers();
        } catch (Exception e) {
            logger.error("Failed to load customers", e);
            showError("خطأ", "فشل في تحميل قائمة العملاء");
            return;
        }

        if (customers == null || customers.isEmpty()) {
            showInfo("معلومة", "لا يوجد عملاء");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("كشف حساب");
        dialog.setHeaderText("إنشاء كشف حساب لعميل مع فلترة المشروع والفترة");

        ButtonType generateBtn = new ButtonType("إنشاء", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(generateBtn, ButtonType.CANCEL);

        ComboBox<Customer> customerCombo = new ComboBox<>(FXCollections.observableArrayList(customers));
        customerCombo.setPrefWidth(360);
        customerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Customer c) {
                return c != null ? c.getName() + " (" + c.getCustomerCode() + ")" : "";
            }

            @Override
            public Customer fromString(String s) {
                return null;
            }
        });
        customerCombo.setValue(customers.get(0));

        ComboBox<String> projectCombo = new ComboBox<>();
        projectCombo.setPrefWidth(360);

        DatePicker from = new DatePicker(LocalDate.now().minusMonths(1));
        DatePicker to = new DatePicker(LocalDate.now());

        ToggleGroup periodGroup = new ToggleGroup();
        RadioButton yearRadio = new RadioButton("سنة");
        RadioButton rangeRadio = new RadioButton("مدى");
        yearRadio.setToggleGroup(periodGroup);
        rangeRadio.setToggleGroup(periodGroup);
        rangeRadio.setSelected(true);

        ComboBox<Integer> yearCombo = new ComboBox<>();
        yearCombo.setPrefWidth(120);
        int currentYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = currentYear; y >= currentYear - 10; y--) {
            years.add(y);
        }
        yearCombo.setItems(FXCollections.observableArrayList(years));
        yearCombo.setValue(currentYear);

        CheckBox includeItems = new CheckBox("إظهار تفاصيل المواد داخل كل فاتورة");

        ComboBox<String> currencyCombo = new ComboBox<>(FXCollections.observableArrayList("الكل", "دينار", "دولار"));
        currencyCombo.setPrefWidth(150);
        currencyCombo.setValue("الكل");

        Runnable updateProjects = () -> {
            Customer c = customerCombo.getValue();
            projectCombo.getItems().clear();
            projectCombo.setValue(null);
            if (c == null) {
                return;
            }
            String locationsText = c.getProjectLocation();
            if (locationsText == null || locationsText.trim().isEmpty()) {
                return;
            }
            List<String> locations = locationsText.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            projectCombo.setItems(FXCollections.observableArrayList(locations));
            if (locations.size() == 1) {
                projectCombo.setValue(locations.get(0));
            }
        };
        updateProjects.run();
        customerCombo.valueProperty().addListener((obs, o, n) -> updateProjects.run());

        Runnable updatePeriodUI = () -> {
            boolean isYear = yearRadio.isSelected();
            yearCombo.setDisable(!isYear);
            from.setDisable(isYear);
            to.setDisable(isYear);
        };
        updatePeriodUI.run();
        periodGroup.selectedToggleProperty().addListener((obs, o, n) -> updatePeriodUI.run());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        int r = 0;
        grid.add(new Label("العميل:"), 0, r);
        grid.add(customerCombo, 1, r++);

        grid.add(new Label("المشروع:"), 0, r);
        grid.add(projectCombo, 1, r++);

        javafx.scene.layout.HBox periodBox = new javafx.scene.layout.HBox(10, rangeRadio, yearRadio, new Label("السنة:"), yearCombo);
        grid.add(new Label("الفترة:"), 0, r);
        grid.add(periodBox, 1, r++);

        javafx.scene.layout.HBox rangeBox = new javafx.scene.layout.HBox(10, new Label("من:"), from, new Label("إلى:"), to);
        grid.add(new Label("مدى:"), 0, r);
        grid.add(rangeBox, 1, r++);

        grid.add(new Label("العملة:"), 0, r);
        grid.add(currencyCombo, 1, r++);

        grid.add(includeItems, 1, r);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result != generateBtn) {
                return;
            }

            Customer c = customerCombo.getValue();
            if (c == null) {
                showError("خطأ", "الرجاء اختيار العميل");
                return;
            }

            String project = projectCombo.getValue();
            LocalDate fromDate = null;
            LocalDate toDate = null;

            if (yearRadio.isSelected()) {
                Integer y = yearCombo.getValue();
                if (y == null) {
                    showError("خطأ", "الرجاء اختيار السنة");
                    return;
                }
                fromDate = LocalDate.of(y, 1, 1);
                toDate = LocalDate.of(y, 12, 31);
            } else {
                fromDate = from.getValue();
                toDate = to.getValue();
                if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
                    showError("خطأ", "تاريخ (إلى) يجب أن يكون بعد (من)");
                    return;
                }
            }

            String selectedCurrency = currencyCombo.getValue();
            String currencyFilter = "الكل".equals(selectedCurrency) ? null : selectedCurrency;

            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("حفظ كشف الحساب");
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                String customerName = c.getName() != null ? c.getName() : "customer";
                fileChooser.setInitialFileName("statement_" + customerName + ".pdf");

                Stage owner = (Stage) receiptsTable.getScene().getWindow();
                File selectedFile = fileChooser.showSaveDialog(owner);
                if (selectedFile == null) {
                    return;
                }

                File pdfFile = receiptService.generateAccountStatementPdf(c, project, fromDate, toDate, includeItems.isSelected(), currencyFilter, selectedFile);
                if (pdfFile != null && pdfFile.exists()) {
                    if (mainApp != null) {
                        mainApp.showPdfPreview(pdfFile);
                    } else if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to generate account statement", e);
                showError("خطأ", "فشل في إنشاء كشف الحساب: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleClose() {
        if (tabMode) {
            if (tabId != null && !tabId.isBlank()) {
                TabManager.getInstance().closeTab(tabId);
                return;
            }

            if (TabManager.getInstance().getTabPane() != null) {
                TabManager.getInstance().getTabPane().getTabs().remove(
                        TabManager.getInstance().getTabPane().getSelectionModel().getSelectedItem()
                );
                return;
            }
        }

        Stage stage = (Stage) receiptsTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private enum RowType {
        ROOT,
        CUSTOMER,
        PROJECT,
        RECEIPT
    }

    private static class ReceiptTreeRow {
        private final RowType type;
        private final Customer customer;
        private final String project;
        private final Receipt receipt;

        private ReceiptTreeRow(RowType type, Customer customer, String project, Receipt receipt) {
            this.type = type;
            this.customer = customer;
            this.project = project;
            this.receipt = receipt;
        }

        public static ReceiptTreeRow root() {
            return new ReceiptTreeRow(RowType.ROOT, null, null, null);
        }

        public static ReceiptTreeRow customer(Customer customer) {
            return new ReceiptTreeRow(RowType.CUSTOMER, customer, null, null);
        }

        public static ReceiptTreeRow project(Customer customer, String project) {
            return new ReceiptTreeRow(RowType.PROJECT, customer, project, null);
        }

        public static ReceiptTreeRow receipt(Receipt receipt) {
            Sale sale = receipt != null ? receipt.getSale() : null;
            Customer c = sale != null ? sale.getCustomer() : null;
            String p = sale != null ? sale.getProjectLocation() : null;
            return new ReceiptTreeRow(RowType.RECEIPT, c, p, receipt);
        }

        public RowType getType() {
            return type;
        }

        public Receipt getReceipt() {
            return receipt;
        }

        public String getTreeText() {
            return switch (type) {
                case CUSTOMER -> customer != null && customer.getName() != null ? customer.getName() : "-";
                case PROJECT -> project != null && !project.trim().isEmpty() ? project : "-";
                case RECEIPT -> receipt != null && receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : "-";
                default -> "";
            };
        }

        public String getCustomerName() {
            if (type != RowType.RECEIPT) {
                return "";
            }
            Sale sale = receipt != null ? receipt.getSale() : null;
            Customer c = sale != null ? sale.getCustomer() : null;
            return c != null && c.getName() != null ? c.getName() : "-";
        }

        public String getDateText(DateTimeFormatter formatter) {
            if (type != RowType.RECEIPT || receipt == null || receipt.getReceiptDate() == null) {
                return "";
            }
            return receipt.getReceiptDate().format(formatter);
        }

        public String getTotalText() {
            if (type != RowType.RECEIPT || receipt == null) {
                return "";
            }
            Sale sale = receipt.getSale();
            Double total = sale != null ? sale.getFinalAmount() : null;
            return total != null ? String.format("%.2f", total) : "-";
        }

        public String getPrintedText() {
            if (type != RowType.RECEIPT || receipt == null) {
                return "";
            }
            return receipt.getIsPrinted() != null && receipt.getIsPrinted() ? "✓" : "✗";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReceiptTreeRow that)) return false;
            return type == that.type
                    && Objects.equals(customer != null ? customer.getId() : null, that.customer != null ? that.customer.getId() : null)
                    && Objects.equals(project, that.project)
                    && Objects.equals(receipt != null ? receipt.getId() : null, that.receipt != null ? that.receipt.getId() : null);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type,
                    customer != null ? customer.getId() : null,
                    project,
                    receipt != null ? receipt.getId() : null);
        }
    }
}
