package com.hisabx.controller;

import com.hisabx.model.Sale;
import com.hisabx.service.ReceiptService;
import com.hisabx.service.SalesService;
import com.hisabx.util.TabManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SaleListController {
    private static final Logger logger = LoggerFactory.getLogger(SaleListController.class);

    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TableView<Sale> salesTable;
    @FXML private TableColumn<Sale, String> saleCodeColumn;
    @FXML private TableColumn<Sale, String> customerColumn;
    @FXML private TableColumn<Sale, String> dateColumn;
    @FXML private TableColumn<Sale, Double> totalColumn;
    @FXML private TableColumn<Sale, String> paymentMethodColumn;
    @FXML private TableColumn<Sale, String> statusColumn;
    @FXML private TableColumn<Sale, Void> actionsColumn;
    @FXML private Label totalSalesLabel;
    @FXML private Label totalAmountLabel;
    @FXML private Label paidAmountLabel;
    @FXML private Label pendingAmountLabel;

    private final SalesService salesService;
    private final ReceiptService receiptService;
    private ObservableList<Sale> allSales;
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

    public SaleListController() {
        this.salesService = new SalesService();
        this.receiptService = new ReceiptService();
    }

    @FXML
    private void initialize() {
        setupTable();
        setupFilters();
        loadSales();
    }

    private void setupTable() {
        saleCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaleCode()));
        customerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCustomer() != null ? data.getValue().getCustomer().getName() : "-"));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSaleDate() != null ? data.getValue().getSaleDate().format(dateFormatter) : "-"));
        totalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getFinalAmount()).asObject());
        paymentMethodColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getPaymentMethodArabic(data.getValue().getPaymentMethod())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getStatusArabic(data.getValue().getPaymentStatus())));

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "مدفوع" -> setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        case "معلق" -> setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        case "متأخر" -> setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        default -> setStyle("");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("👁");
            private final Button receiptBtn = new Button("🧾");
            private final Button payBtn = new Button("💰");
            private final javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, viewBtn, receiptBtn, payBtn);

            {
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                receiptBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
                payBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

                viewBtn.setTooltip(new Tooltip("عرض التفاصيل"));
                receiptBtn.setTooltip(new Tooltip("طباعة الإيصال"));
                payBtn.setTooltip(new Tooltip("تحديث الدفع"));

                viewBtn.setOnAction(e -> handleViewSale(getTableView().getItems().get(getIndex())));
                receiptBtn.setOnAction(e -> handlePrintReceipt(getTableView().getItems().get(getIndex())));
                payBtn.setOnAction(e -> handleUpdatePayment(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    private void setupFilters() {
        statusComboBox.setValue("الكل");
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());
    }

    private void loadSales() {
        List<Sale> sales = salesService.getAllSales();
        allSales = FXCollections.observableArrayList(sales);
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

    @FXML
    private void handleStatusFilter() {
        applyFilters();
    }

    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        String statusFilter = statusComboBox.getValue();
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        List<Sale> filtered = allSales.stream()
                .filter(sale -> {
                    if (!searchText.isEmpty()) {
                        boolean matchesCode = sale.getSaleCode().toLowerCase().contains(searchText);
                        boolean matchesCustomer = sale.getCustomer() != null &&
                                sale.getCustomer().getName().toLowerCase().contains(searchText);
                        if (!matchesCode && !matchesCustomer) return false;
                    }

                    if (statusFilter != null && !"الكل".equals(statusFilter)) {
                        String saleStatus = getStatusArabic(sale.getPaymentStatus());
                        if (!statusFilter.equals(saleStatus)) return false;
                    }

                    if (fromDate != null && sale.getSaleDate() != null) {
                        if (sale.getSaleDate().toLocalDate().isBefore(fromDate)) return false;
                    }

                    if (toDate != null && sale.getSaleDate() != null) {
                        if (sale.getSaleDate().toLocalDate().isAfter(toDate)) return false;
                    }

                    return true;
                })
                .toList();

        salesTable.setItems(FXCollections.observableArrayList(filtered));
        updateSummaryForFiltered(filtered);
    }

    @FXML
    private void handleRefresh() {
        loadSales();
    }

    private void updateSummary() {
        updateSummaryForFiltered(allSales);
    }

    private void updateSummaryForFiltered(List<Sale> sales) {
        int totalCount = sales.size();
        double totalAmount = sales.stream().mapToDouble(Sale::getFinalAmount).sum();
        double paidAmount = sales.stream()
                .filter(s -> "PAID".equals(s.getPaymentStatus()))
                .mapToDouble(Sale::getFinalAmount).sum();
        double pendingAmount = sales.stream()
                .filter(s -> !"PAID".equals(s.getPaymentStatus()))
                .mapToDouble(Sale::getFinalAmount).sum();

        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        totalSalesLabel.setText(String.valueOf(totalCount));
        totalAmountLabel.setText(df.format(totalAmount) + " دينار");
        paidAmountLabel.setText(df.format(paidAmount) + " دينار");
        pendingAmountLabel.setText(df.format(pendingAmount) + " دينار");
    }

    private void handleViewSale(Sale sale) {
        StringBuilder details = new StringBuilder();
        details.append("رقم الفاتورة: ").append(sale.getSaleCode()).append("\n");
        details.append("العميل: ").append(sale.getCustomer() != null ? sale.getCustomer().getName() : "-").append("\n");
        details.append("التاريخ: ").append(sale.getSaleDate().format(dateFormatter)).append("\n");
        details.append("طريقة الدفع: ").append(getPaymentMethodArabic(sale.getPaymentMethod())).append("\n");
        details.append("الحالة: ").append(getStatusArabic(sale.getPaymentStatus())).append("\n\n");
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        details.append("المجموع: ").append(df.format(sale.getTotalAmount())).append(" دينار\n");
        details.append("الخصم: ").append(df.format(sale.getDiscountAmount())).append(" دينار\n");
        details.append("الإجمالي النهائي: ").append(df.format(sale.getFinalAmount())).append(" دينار");

        if (sale.getNotes() != null && !sale.getNotes().isEmpty()) {
            details.append("\n\nملاحظات: ").append(sale.getNotes());
        }

        showInfo("تفاصيل الفاتورة", details.toString());
    }

    private void handlePrintReceipt(Sale sale) {
        try {
            var receipt = receiptService.generateReceipt(sale.getId(), "DEFAULT", "System");
            showSuccess("تم بنجاح", "تم إنشاء الإيصال بنجاح\nرقم الإيصال: " + receipt.getReceiptNumber());

            if (receipt.getFilePath() != null) {
                File pdfFile = new File(receipt.getFilePath());
                if (pdfFile.exists()) {
                    if (mainApp != null) {
                        mainApp.showPdfPreview(pdfFile);
                    } else if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to generate receipt", e);
            showError("خطأ", "فشل في إنشاء الإيصال: " + e.getMessage());
        }
    }

    private void handleUpdatePayment(Sale sale) {
        if ("PAID".equals(sale.getPaymentStatus())) {
            showInfo("معلومة", "هذه الفاتورة مدفوعة بالفعل");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الدفع");
        alert.setHeaderText("هل تريد تحديث حالة الدفع إلى 'مدفوع'؟");
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        alert.setContentText("رقم الفاتورة: " + sale.getSaleCode() + "\nالمبلغ: " + df.format(sale.getFinalAmount()) + " دينار");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    salesService.updatePaymentStatus(sale.getId(), "PAID");
                    loadSales();
                    showSuccess("تم بنجاح", "تم تحديث حالة الدفع بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to update payment status", e);
                    showError("خطأ", "فشل في تحديث حالة الدفع");
                }
            }
        });
    }

    @FXML
    private void handleSalesReport() {
        showInfo("تقرير المبيعات",
                "إجمالي المبيعات: " + totalSalesLabel.getText() + "\n" +
                        "إجمالي المبلغ: " + totalAmountLabel.getText() + "\n" +
                        "المدفوع: " + paidAmountLabel.getText() + "\n" +
                        "المعلق: " + pendingAmountLabel.getText());
    }

    @FXML
    private void handleExportExcel() {
        showInfo("قريباً", "ميزة تصدير Excel قيد التطوير");
    }

    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) salesTable.getScene().getWindow();
        stage.close();
    }

    private String getPaymentMethodArabic(String method) {
        if (method == null) return "-";
        return switch (method) {
            case "CASH" -> "نقدي";
            case "DEBT" -> "دين";
            case "CARD" -> "بطاقة";
            default -> method;
        };
    }

    private String getStatusArabic(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "PAID" -> "مدفوع";
            case "PENDING" -> "معلق";
            case "OVERDUE" -> "متأخر";
            default -> status;
        };
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
}
