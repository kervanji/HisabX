package com.hisabx.controller;

import com.hisabx.model.Sale;
import com.hisabx.model.SaleItem;
import com.hisabx.service.SalesService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class SalesReportController {
    @FXML private ComboBox<String> periodComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Label totalSalesLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label avgSaleLabel;
    @FXML private Label totalDiscountLabel;
    @FXML private VBox paymentBreakdownBox;
    @FXML private TableView<ProductStat> topProductsTable;
    @FXML private TableColumn<ProductStat, String> productNameColumn;
    @FXML private TableColumn<ProductStat, Double> quantitySoldColumn;
    @FXML private TableColumn<ProductStat, Double> productTotalColumn;
    @FXML private TableView<CustomerStat> topCustomersTable;
    @FXML private TableColumn<CustomerStat, String> customerNameColumn;
    @FXML private TableColumn<CustomerStat, Double> customerTotalColumn;

    private final SalesService salesService;
    private List<Sale> reportData;

    public SalesReportController() {
        this.salesService = new SalesService();
    }

    @FXML
    private void initialize() {
        setupTables();
        setupDefaults();
        handleGenerateReport();
    }

    private void setupTables() {
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantitySoldColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getQuantitySold()).asObject());
        productTotalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalAmount()).asObject());
        quantitySoldColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });
        productTotalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });

        customerNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCustomerName()));
        customerTotalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalAmount()).asObject());
        customerTotalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });
    }

    private String formatNumber(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return df.format(value);
    }

    private void setupDefaults() {
        periodComboBox.setValue("الشهر");
        LocalDate now = LocalDate.now();
        fromDatePicker.setValue(now.withDayOfMonth(1));
        toDatePicker.setValue(now);
    }

    @FXML
    private void handlePeriodChange() {
        String period = periodComboBox.getValue();
        LocalDate now = LocalDate.now();

        switch (period) {
            case "اليوم" -> {
                fromDatePicker.setValue(now);
                toDatePicker.setValue(now);
            }
            case "الأسبوع" -> {
                fromDatePicker.setValue(now.minusWeeks(1));
                toDatePicker.setValue(now);
            }
            case "الشهر" -> {
                fromDatePicker.setValue(now.withDayOfMonth(1));
                toDatePicker.setValue(now);
            }
            case "السنة" -> {
                fromDatePicker.setValue(now.withDayOfYear(1));
                toDatePicker.setValue(now);
            }
            case "مخصص" -> {
                // Keep current values, let user customize
            }
        }

        if (!"مخصص".equals(period)) {
            handleGenerateReport();
        }
    }

    @FXML
    private void handleDateChange() {
        periodComboBox.setValue("مخصص");
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        if (fromDate == null || toDate == null) {
            showError("خطأ", "الرجاء تحديد فترة التقرير");
            return;
        }

        if (fromDate.isAfter(toDate)) {
            showError("خطأ", "تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
            return;
        }

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(23, 59, 59);

        reportData = salesService.getAllSales().stream()
                .filter(sale -> sale.getSaleDate() != null &&
                        !sale.getSaleDate().isBefore(startDateTime) &&
                        !sale.getSaleDate().isAfter(endDateTime))
                .toList();

        updateSummary();
        updatePaymentBreakdown();
        updateTopProducts();
        updateTopCustomers();
    }

    private void updateSummary() {
        double totalSales = reportData.stream().mapToDouble(Sale::getFinalAmount).sum();
        int invoiceCount = reportData.size();
        double avgSale = invoiceCount > 0 ? totalSales / invoiceCount : 0;
        double totalDiscount = reportData.stream().mapToDouble(Sale::getDiscountAmount).sum();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        totalSalesLabel.setText(df.format(totalSales));
        invoiceCountLabel.setText(String.valueOf(invoiceCount));
        avgSaleLabel.setText(df.format(avgSale));
        totalDiscountLabel.setText(df.format(totalDiscount));
    }

    private void updatePaymentBreakdown() {
        paymentBreakdownBox.getChildren().clear();

        Map<String, Double> paymentTotals = reportData.stream()
                .collect(Collectors.groupingBy(
                        sale -> getPaymentMethodArabic(sale.getPaymentMethod()),
                        Collectors.summingDouble(Sale::getFinalAmount)
                ));

        double total = reportData.stream().mapToDouble(Sale::getFinalAmount).sum();

        for (Map.Entry<String, Double> entry : paymentTotals.entrySet()) {
            double percentage = total > 0 ? (entry.getValue() / total) * 100 : 0;

            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label methodLabel = new Label(entry.getKey());
            methodLabel.setPrefWidth(80);
            methodLabel.setStyle("-fx-font-weight: bold;");

            ProgressBar progressBar = new ProgressBar(percentage / 100);
            progressBar.setPrefWidth(120);
            progressBar.setStyle("-fx-accent: " + getColorForMethod(entry.getKey()) + ";");

            java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
            Label valueLabel = new Label(df.format(entry.getValue()) + " دينار (" + String.format("%.1f%%", percentage) + ")");
            valueLabel.setStyle("-fx-text-fill: #7f8c8d;");

            row.getChildren().addAll(methodLabel, progressBar, valueLabel);
            paymentBreakdownBox.getChildren().add(row);
        }

        if (paymentTotals.isEmpty()) {
            Label noDataLabel = new Label("لا توجد بيانات");
            noDataLabel.setStyle("-fx-text-fill: #bdc3c7;");
            paymentBreakdownBox.getChildren().add(noDataLabel);
        }
    }

    private void updateTopProducts() {
        Map<String, ProductStat> productStats = new HashMap<>();

        for (Sale sale : reportData) {
            if (sale.getSaleItems() != null) {
                for (SaleItem item : sale.getSaleItems()) {
                    String productName = item.getProduct() != null ? item.getProduct().getName() : "غير معروف";
                    productStats.computeIfAbsent(productName, k -> new ProductStat(productName))
                            .addSale(item.getQuantity(), item.getTotalPrice());
                }
            }
        }

        List<ProductStat> topProducts = productStats.values().stream()
                .sorted(Comparator.comparingDouble(ProductStat::getTotalAmount).reversed())
                .limit(10)
                .toList();

        topProductsTable.setItems(FXCollections.observableArrayList(topProducts));
    }

    private void updateTopCustomers() {
        Map<String, CustomerStat> customerStats = new HashMap<>();

        for (Sale sale : reportData) {
            String customerName = sale.getCustomer() != null ? sale.getCustomer().getName() : "غير معروف";
            customerStats.computeIfAbsent(customerName, k -> new CustomerStat(customerName))
                    .addSale(sale.getFinalAmount());
        }

        List<CustomerStat> topCustomers = customerStats.values().stream()
                .sorted(Comparator.comparingDouble(CustomerStat::getTotalAmount).reversed())
                .limit(10)
                .toList();

        topCustomersTable.setItems(FXCollections.observableArrayList(topCustomers));
    }

    private String getPaymentMethodArabic(String method) {
        if (method == null) return "غير محدد";
        return switch (method) {
            case "CASH" -> "نقدي";
            case "DEBT" -> "دين";
            case "CARD" -> "بطاقة";
            default -> method;
        };
    }

    private String getColorForMethod(String method) {
        return switch (method) {
            case "نقدي" -> "#27ae60";
            case "دين" -> "#e74c3c";
            case "بطاقة" -> "#3498db";
            default -> "#95a5a6";
        };
    }

    @FXML
    private void handleExportPDF() {
        showInfo("تصدير PDF", "ميزة تصدير PDF قيد التطوير\n\n" + generateTextReport());
    }

    @FXML
    private void handleExportExcel() {
        showInfo("تصدير Excel", "ميزة تصدير Excel قيد التطوير");
    }

    @FXML
    private void handlePrint() {
        showInfo("طباعة التقرير", generateTextReport());
    }

    private String generateTextReport() {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        report.append("=== تقرير المبيعات ===\n\n");
        report.append("الفترة: ").append(fromDatePicker.getValue().format(formatter))
                .append(" إلى ").append(toDatePicker.getValue().format(formatter)).append("\n\n");

        report.append("--- الملخص ---\n");
        report.append("إجمالي المبيعات: ").append(totalSalesLabel.getText()).append(" دينار\n");
        report.append("عدد الفواتير: ").append(invoiceCountLabel.getText()).append("\n");
        report.append("متوسط الفاتورة: ").append(avgSaleLabel.getText()).append(" دينار\n");
        report.append("إجمالي الخصومات: ").append(totalDiscountLabel.getText()).append(" دينار\n");

        return report.toString();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) topProductsTable.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void styleAlert(Alert alert) {
        if (alert == null || alert.getDialogPane() == null) {
            return;
        }

        String css = getClass().getResource("/styles/main.css") != null
                ? getClass().getResource("/styles/main.css").toExternalForm()
                : null;
        if (css != null && !alert.getDialogPane().getStylesheets().contains(css)) {
            alert.getDialogPane().getStylesheets().add(css);
        }
        alert.getDialogPane().setStyle(
                "-fx-font-family: 'Geeza Pro', 'SF Arabic', 'Arial', 'Tahoma';"
        );
    }

    public static class ProductStat {
        private final String productName;
        private double quantitySold = 0;
        private double totalAmount = 0;

        public ProductStat(String productName) {
            this.productName = productName;
        }

        public void addSale(double quantity, double amount) {
            this.quantitySold += quantity;
            this.totalAmount += amount;
        }

        public String getProductName() { return productName; }
        public double getQuantitySold() { return quantitySold; }
        public double getTotalAmount() { return totalAmount; }
    }

    public static class CustomerStat {
        private final String customerName;
        private double totalAmount = 0;

        public CustomerStat(String customerName) {
            this.customerName = customerName;
        }

        public void addSale(double amount) {
            this.totalAmount += amount;
        }

        public String getCustomerName() { return customerName; }
        public double getTotalAmount() { return totalAmount; }
    }
}
