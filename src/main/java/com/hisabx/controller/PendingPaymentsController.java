package com.hisabx.controller;

import com.hisabx.model.Sale;
import com.hisabx.service.SalesService;
import com.hisabx.service.ReceiptService;
import com.hisabx.service.CustomerService;
import com.hisabx.database.Repository.CustomerRepository;
import com.hisabx.model.Customer;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private final ReceiptService receiptService;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private ObservableList<Sale> allPendingSales;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public PendingPaymentsController() {
        this.salesService = new SalesService();
        this.receiptService = new ReceiptService();
        this.customerRepository = new CustomerRepository();
        this.customerService = new CustomerService();
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
            private final javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, payBtn);

            {
                payBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                payBtn.setTooltip(new Tooltip("تسجيل الدفع"));
                payBtn.setOnAction(e -> handleMarkAsPaid(getTableView().getItems().get(getIndex())));
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

        totalPendingLabel.setText(String.format("%,.2f دينار", totalPending));
        invoiceCountLabel.setText(String.valueOf(invoiceCount));
        overdueLabel.setText(String.format("%,.2f دينار", overdue));
    }

    private void handleMarkAsPaid(Sale sale) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الدفع");
        alert.setHeaderText("هل تريد تسجيل الدفع لهذه الفاتورة؟");
        alert.setContentText("رقم الفاتورة: " + sale.getSaleCode() + "\nالمبلغ: " + 
                           String.format("%,.2f", sale.getFinalAmount()) + " دينار");

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


    @FXML
    private void handleDebtReport() {
        Sale selectedSale = pendingTable.getSelectionModel().getSelectedItem();
        if (selectedSale == null || selectedSale.getCustomer() == null) {
            showError("خطأ", "الرجاء اختيار فاتورة أولاً");
            return;
        }

        try {
            java.io.File pdfFile = receiptService.generateAccountStatementPdf(
                selectedSale.getCustomer(),
                null,
                null,
                null,
                false
            );
            if (pdfFile != null && pdfFile.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdfFile);
                } else {
                    showSuccess("تم بنجاح", "تم إنشاء كشف الحساب:\n" + pdfFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to generate account statement", e);
            showError("خطأ", "فشل في إنشاء كشف الحساب: " + e.getMessage());
        }
    }

    @FXML
    private void handlePayToCustomer() {
        Sale selectedSale = pendingTable.getSelectionModel().getSelectedItem();
        if (selectedSale == null || selectedSale.getCustomer() == null) {
            showError("خطأ", "الرجاء اختيار فاتورة أولاً");
            return;
        }

        Customer customer = selectedSale.getCustomer();
        
        if (customer.getCurrentBalance() == null || customer.getCurrentBalance() <= 0) {
            showError("خطأ", "العميل ليس لديه رصيد دائن (نحن لسنا مدينين له)");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("دفع دين للعميل");
        dialog.setHeaderText("دفع مبلغ للعميل: " + customer.getName() + 
                           "\nالرصيد الدائن الحالي: " + currencyFormat.format(customer.getCurrentBalance()) + " د.ع");

        ButtonType payButtonType = new ButtonType("دفع", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField amountField = new TextField();
        amountField.setPromptText("المبلغ");
        amountField.setText(currencyFormat.format(customer.getCurrentBalance()));

        ComboBox<String> paymentMethodCombo = new ComboBox<>();
        paymentMethodCombo.getItems().addAll("نقدي", "تحويل بنكي", "شيك");
        paymentMethodCombo.setValue("نقدي");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("ملاحظات (اختياري)");
        notesArea.setPrefRowCount(3);

        grid.add(new Label("المبلغ:"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("طريقة الدفع:"), 0, 1);
        grid.add(paymentMethodCombo, 1, 1);
        grid.add(new Label("ملاحظات:"), 0, 2);
        grid.add(notesArea, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == payButtonType) {
            try {
                String amountStr = amountField.getText().replaceAll(",", "");
                double amount = Double.parseDouble(amountStr);
                
                customerService.payToCustomer(
                    customer.getId(),
                    amount,
                    paymentMethodCombo.getValue(),
                    notesArea.getText(),
                    "النظام"
                );
                
                loadPendingPayments();
                showSuccess("تم بنجاح", "تم تسجيل الدفع للعميل بنجاح\nالمبلغ: " + 
                          currencyFormat.format(amount) + " د.ع");
            } catch (NumberFormatException e) {
                showError("خطأ", "المبلغ المدخل غير صحيح");
            } catch (Exception e) {
                logger.error("Failed to pay to customer", e);
                showError("خطأ", "فشل في تسجيل الدفع: " + e.getMessage());
            }
        }
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
