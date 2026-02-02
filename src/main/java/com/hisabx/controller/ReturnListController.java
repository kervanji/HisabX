package com.hisabx.controller;

import com.hisabx.model.SaleReturn;
import com.hisabx.service.ReturnService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.File;
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
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("حفظ إيصال المرتجع");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            String defaultName = (saleReturn != null && saleReturn.getReturnCode() != null)
                    ? ("return_" + saleReturn.getReturnCode() + ".pdf")
                    : "return_receipt.pdf";
            fileChooser.setInitialFileName(defaultName);

            Stage owner = (Stage) returnsTable.getScene().getWindow();
            File selectedFile = fileChooser.showSaveDialog(owner);
            if (selectedFile == null) {
                return;
            }

            File pdfFile = returnService.generateReturnReceiptPdf(saleReturn, selectedFile);
            if (pdfFile != null && pdfFile.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdfFile);
                } else {
                    showInfo("تم بنجاح", "تم إنشاء إيصال المرتجع:\n" + pdfFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            showError("خطأ", "فشل في إنشاء إيصال المرتجع: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
