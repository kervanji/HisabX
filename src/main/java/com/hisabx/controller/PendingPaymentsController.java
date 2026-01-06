package com.hisabx.controller;

import com.hisabx.model.Sale;
import com.hisabx.service.SalesService;
import com.hisabx.database.Repository.CustomerRepository;
import com.hisabx.model.Customer;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public class PendingPaymentsController {
    private static final Logger logger = LoggerFactory.getLogger(PendingPaymentsController.class);

    @FXML private Label totalPendingLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label overdueLabel;
    @FXML private ComboBox<String> customerFilterComboBox;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private TableView<Sale> pendingTable;
    @FXML private TableColumn<Sale, String> saleCodeColumn;
    @FXML private TableColumn<Sale, String> customerColumn;
    @FXML private TableColumn<Sale, String> dateColumn;
    @FXML private TableColumn<Sale, Double> amountColumn;
    @FXML private TableColumn<Sale, Long> daysColumn;
    @FXML private TableColumn<Sale, String> statusColumn;
    @FXML private TableColumn<Sale, Void> actionsColumn;

    private final SalesService salesService;
    private final CustomerRepository customerRepository;
    private ObservableList<Sale> allPendingSales;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public PendingPaymentsController() {
        this.salesService = new SalesService();
        this.customerRepository = new CustomerRepository();
    }

    @FXML
    private void initialize() {
        setupTable();
        setupFilters();
        loadPendingPayments();
    }

    private void setupTable() {
        saleCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaleCode()));
        customerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCustomer() != null ? data.getValue().getCustomer().getName() : "-"));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSaleDate() != null ? data.getValue().getSaleDate().format(dateFormatter) : "-"));
        amountColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getFinalAmount()).asObject());
        
        daysColumn.setCellValueFactory(data -> {
            if (data.getValue().getSaleDate() != null) {
                long days = ChronoUnit.DAYS.between(data.getValue().getSaleDate().toLocalDate(), LocalDate.now());
                return new SimpleLongProperty(days).asObject();
            }
            return new SimpleLongProperty(0).asObject();
        });

        statusColumn.setCellValueFactory(data -> {
            if (data.getValue().getSaleDate() != null) {
                long days = ChronoUnit.DAYS.between(data.getValue().getSaleDate().toLocalDate(), LocalDate.now());
                if (days > 30) return new SimpleStringProperty("متأخر");
                else if (days > 14) return new SimpleStringProperty("قريب");
                else return new SimpleStringProperty("معلق");
            }
            return new SimpleStringProperty("معلق");
        });

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
                        case "متأخر" -> setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        case "قريب" -> setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        default -> setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                    }
                }
            }
        });

        daysColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(days + " يوم");
                    if (days > 30) setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    else if (days > 14) setStyle("-fx-text-fill: #f39c12;");
                    else setStyle("");
                }
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button payBtn = new Button("💰 تسديد");
            private final Button reminderBtn = new Button("📧");
            private final javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, payBtn, reminderBtn);

            {
                payBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                reminderBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");

                payBtn.setTooltip(new Tooltip("تسجيل الدفع"));
                reminderBtn.setTooltip(new Tooltip("إرسال تذكير"));

                payBtn.setOnAction(e -> handleMarkAsPaid(getTableView().getItems().get(getIndex())));
                reminderBtn.setOnAction(e -> handleSendReminder(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    private void setupFilters() {
        customerFilterComboBox.setValue("جميع العملاء");
        sortComboBox.setValue("الأحدث أولاً");

        List<Customer> customers = customerRepository.findAll();
        ObservableList<String> customerNames = FXCollections.observableArrayList("جميع العملاء");
        customers.forEach(c -> customerNames.add(c.getName()));
        customerFilterComboBox.setItems(customerNames);
    }

    private void loadPendingPayments() {
        List<Sale> pendingSales = salesService.getPendingPayments();
        allPendingSales = FXCollections.observableArrayList(pendingSales);
        applyFilters();
        updateSummary();
    }

    @FXML
    private void handleCustomerFilter() {
        applyFilters();
    }

    @FXML
    private void handleSort() {
        applyFilters();
    }

    private void applyFilters() {
        String customerFilter = customerFilterComboBox.getValue();
        String sortOption = sortComboBox.getValue();

        List<Sale> filtered = allPendingSales.stream()
                .filter(sale -> {
                    if (customerFilter != null && !"جميع العملاء".equals(customerFilter)) {
                        return sale.getCustomer() != null && 
                               customerFilter.equals(sale.getCustomer().getName());
                    }
                    return true;
                })
                .sorted(getComparator(sortOption))
                .toList();

        pendingTable.setItems(FXCollections.observableArrayList(filtered));
        updateSummaryForFiltered(filtered);
    }

    private Comparator<Sale> getComparator(String sortOption) {
        if (sortOption == null) return Comparator.comparing(Sale::getSaleDate).reversed();
        
        return switch (sortOption) {
            case "الأقدم أولاً" -> Comparator.comparing(Sale::getSaleDate);
            case "الأعلى مبلغاً" -> Comparator.comparing(Sale::getFinalAmount).reversed();
            case "الأقل مبلغاً" -> Comparator.comparing(Sale::getFinalAmount);
            default -> Comparator.comparing(Sale::getSaleDate).reversed();
        };
    }

    @FXML
    private void handleRefresh() {
        loadPendingPayments();
    }

    private void updateSummary() {
        updateSummaryForFiltered(allPendingSales);
    }

    private void updateSummaryForFiltered(List<Sale> sales) {
        double totalPending = sales.stream().mapToDouble(Sale::getFinalAmount).sum();
        int invoiceCount = sales.size();
        double overdue = sales.stream()
                .filter(s -> {
                    if (s.getSaleDate() != null) {
                        long days = ChronoUnit.DAYS.between(s.getSaleDate().toLocalDate(), LocalDate.now());
                        return days > 30;
                    }
                    return false;
                })
                .mapToDouble(Sale::getFinalAmount).sum();

        totalPendingLabel.setText(String.format("%.2f دينار", totalPending));
        invoiceCountLabel.setText(String.valueOf(invoiceCount));
        overdueLabel.setText(String.format("%.2f دينار", overdue));
    }

    private void handleMarkAsPaid(Sale sale) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الدفع");
        alert.setHeaderText("هل تريد تسجيل الدفع لهذه الفاتورة؟");
        alert.setContentText("رقم الفاتورة: " + sale.getSaleCode() + "\nالمبلغ: " + 
                           String.format("%.2f", sale.getFinalAmount()) + " دينار");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    salesService.updatePaymentStatus(sale.getId(), "PAID");
                    loadPendingPayments();
                    showSuccess("تم بنجاح", "تم تسجيل الدفع بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to update payment status", e);
                    showError("خطأ", "فشل في تسجيل الدفع");
                }
            }
        });
    }

    private void handleSendReminder(Sale sale) {
        String customerName = sale.getCustomer() != null ? sale.getCustomer().getName() : "العميل";
        String phone = sale.getCustomer() != null ? sale.getCustomer().getPhoneNumber() : "";
        
        showInfo("إرسال تذكير", 
                "سيتم إرسال تذكير إلى: " + customerName + "\n" +
                (phone != null && !phone.isEmpty() ? "الهاتف: " + phone + "\n" : "") +
                "المبلغ المستحق: " + String.format("%.2f", sale.getFinalAmount()) + " دينار\n\n" +
                "ملاحظة: ميزة إرسال الرسائل قيد التطوير");
    }

    @FXML
    private void handleSendReminders() {
        int count = pendingTable.getItems().size();
        if (count == 0) {
            showInfo("معلومة", "لا توجد مدفوعات معلقة لإرسال تذكيرات");
            return;
        }
        
        showInfo("إرسال تذكيرات", 
                "سيتم إرسال تذكيرات إلى " + count + " عميل\n\n" +
                "ملاحظة: ميزة إرسال الرسائل الجماعية قيد التطوير");
    }

    @FXML
    private void handleDebtReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== تقرير الذمم المعلقة ===\n\n");
        report.append("إجمالي المعلق: ").append(totalPendingLabel.getText()).append("\n");
        report.append("عدد الفواتير: ").append(invoiceCountLabel.getText()).append("\n");
        report.append("المتأخر (أكثر من 30 يوم): ").append(overdueLabel.getText()).append("\n\n");
        
        report.append("--- التفاصيل ---\n");
        for (Sale sale : pendingTable.getItems()) {
            String customerName = sale.getCustomer() != null ? sale.getCustomer().getName() : "-";
            long days = sale.getSaleDate() != null ? 
                       ChronoUnit.DAYS.between(sale.getSaleDate().toLocalDate(), LocalDate.now()) : 0;
            report.append(sale.getSaleCode()).append(" | ")
                  .append(customerName).append(" | ")
                  .append(String.format("%.2f", sale.getFinalAmount())).append(" دينار | ")
                  .append(days).append(" يوم\n");
        }
        
        showInfo("تقرير الذمم", report.toString());
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) pendingTable.getScene().getWindow();
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
}
