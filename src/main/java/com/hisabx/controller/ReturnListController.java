package com.hisabx.controller;

import com.hisabx.model.SaleReturn;
import com.hisabx.service.ReturnService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReturnListController {
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TableView<SaleReturn> returnsTable;
    @FXML private TableColumn<SaleReturn, String> returnCodeColumn;
    @FXML private TableColumn<SaleReturn, String> saleCodeColumn;
    @FXML private TableColumn<SaleReturn, String> customerColumn;
    @FXML private TableColumn<SaleReturn, String> dateColumn;
    @FXML private TableColumn<SaleReturn, Double> amountColumn;
    @FXML private TableColumn<SaleReturn, String> reasonColumn;
    @FXML private TableColumn<SaleReturn, String> statusColumn;
    @FXML private TableColumn<SaleReturn, Void> actionsColumn;
    @FXML private Label totalReturnsLabel;
    @FXML private Label totalAmountLabel;

    private final ReturnService returnService = new ReturnService();
    private ObservableList<SaleReturn> allReturns;

    @FXML
    private void initialize() {
        setupTable();
        setupFilters();
        loadReturns();
    }

    private void setupTable() {
        returnCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReturnCode()));
        saleCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSale() != null ? data.getValue().getSale().getSaleCode() : "-"));
        customerColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCustomer() != null ? data.getValue().getCustomer().getName() : "-"));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getReturnDate() != null ? data.getValue().getReturnDate().format(dateFormatter) : "-"));
        amountColumn.setCellValueFactory(data -> new SimpleDoubleProperty(
                data.getValue().getTotalReturnAmount() != null ? data.getValue().getTotalReturnAmount() : 0.0).asObject());
        reasonColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getReturnReason() != null ? data.getValue().getReturnReason() : "-"));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getStatusArabic(data.getValue().getReturnStatus())));

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
                        case "مكتمل" -> setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        case "معلق" -> setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        case "مرفوض" -> setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        default -> setStyle("");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("👁");
            {
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                viewBtn.setTooltip(new Tooltip("عرض التفاصيل"));
                viewBtn.setOnAction(e -> handleViewReturn(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });
    }

    private void setupFilters() {
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());
    }

    private void loadReturns() {
        List<SaleReturn> returns = returnService.getAllReturns();
        allReturns = FXCollections.observableArrayList(returns);
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
        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();

        List<SaleReturn> filtered = allReturns.stream()
                .filter(ret -> {
                    if (!searchText.isEmpty()) {
                        boolean matchesCode = ret.getReturnCode() != null && 
                                ret.getReturnCode().toLowerCase().contains(searchText);
                        boolean matchesCustomer = ret.getCustomer() != null &&
                                ret.getCustomer().getName().toLowerCase().contains(searchText);
                        if (!matchesCode && !matchesCustomer) return false;
                    }

                    if (fromDate != null && ret.getReturnDate() != null) {
                        if (ret.getReturnDate().toLocalDate().isBefore(fromDate)) return false;
                    }

                    if (toDate != null && ret.getReturnDate() != null) {
                        if (ret.getReturnDate().toLocalDate().isAfter(toDate)) return false;
                    }

                    return true;
                })
                .toList();

        returnsTable.setItems(FXCollections.observableArrayList(filtered));
        updateSummaryForFiltered(filtered);
    }

    @FXML
    private void handleRefresh() {
        loadReturns();
    }

    private void updateSummary() {
        updateSummaryForFiltered(allReturns);
    }

    private void updateSummaryForFiltered(List<SaleReturn> returns) {
        int totalCount = returns.size();
        double totalAmount = returns.stream()
                .mapToDouble(r -> r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0)
                .sum();

        totalReturnsLabel.setText(String.valueOf(totalCount));
        totalAmountLabel.setText(currencyFormat.format(totalAmount) + " د.ع");
    }

    private void handleViewReturn(SaleReturn saleReturn) {
        StringBuilder details = new StringBuilder();
        details.append("رقم المرتجع: ").append(saleReturn.getReturnCode()).append("\n");
        details.append("رقم الفاتورة: ").append(saleReturn.getSale() != null ? saleReturn.getSale().getSaleCode() : "-").append("\n");
        details.append("العميل: ").append(saleReturn.getCustomer() != null ? saleReturn.getCustomer().getName() : "-").append("\n");
        details.append("التاريخ: ").append(saleReturn.getReturnDate() != null ? saleReturn.getReturnDate().format(dateFormatter) : "-").append("\n");
        details.append("السبب: ").append(saleReturn.getReturnReason()).append("\n");
        details.append("الحالة: ").append(getStatusArabic(saleReturn.getReturnStatus())).append("\n\n");
        details.append("المبلغ المرتجع: ").append(currencyFormat.format(saleReturn.getTotalReturnAmount())).append(" د.ع\n\n");

        if (saleReturn.getReturnItems() != null && !saleReturn.getReturnItems().isEmpty()) {
            details.append("--- المواد المرتجعة ---\n");
            saleReturn.getReturnItems().forEach(item -> {
                String productName = item.getProduct() != null ? item.getProduct().getName() : "غير معروف";
                details.append("• ").append(productName)
                        .append(" × ").append(item.getQuantity())
                        .append(" = ").append(currencyFormat.format(item.getTotalPrice())).append(" د.ع\n");
            });
        }

        if (saleReturn.getNotes() != null && !saleReturn.getNotes().isEmpty()) {
            details.append("\nملاحظات: ").append(saleReturn.getNotes());
        }

        showInfo("تفاصيل المرتجع", details.toString());
    }

    private String getStatusArabic(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "COMPLETED" -> "مكتمل";
            case "PENDING" -> "معلق";
            case "APPROVED" -> "موافق عليه";
            case "REJECTED" -> "مرفوض";
            default -> status;
        };
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) returnsTable.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
