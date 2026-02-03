package com.hisabx.controller;

import com.hisabx.model.*;
import com.hisabx.service.VoucherService;
import com.hisabx.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class VoucherListController implements Initializable {
    
    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TableView<Voucher> vouchersTable;
    @FXML private TableColumn<Voucher, String> voucherNumberCol;
    @FXML private TableColumn<Voucher, String> dateCol;
    @FXML private TableColumn<Voucher, String> customerCol;
    @FXML private TableColumn<Voucher, String> amountCol;
    @FXML private TableColumn<Voucher, String> currencyCol;
    @FXML private TableColumn<Voucher, String> descriptionCol;
    @FXML private TableColumn<Voucher, String> createdByCol;
    @FXML private Label totalCountLabel;
    @FXML private Label totalIqdLabel;
    @FXML private Label totalUsdLabel;
    
    private final VoucherService voucherService = new VoucherService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    
    private VoucherType voucherType = VoucherType.RECEIPT;
    private ObservableList<Voucher> vouchers = FXCollections.observableArrayList();
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupDatePickers();
    }
    
    public void setVoucherType(VoucherType type) {
        this.voucherType = type;
        updateTitle();
        loadVouchers();
    }
    
    private void setupTable() {
        voucherNumberCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getVoucherNumber()));
        
        dateCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getVoucherDate().toLocalDate().format(dateFormatter)));
        
        customerCol.setCellValueFactory(data -> {
            Customer customer = data.getValue().getCustomer();
            return new SimpleStringProperty(customer != null ? customer.getName() : "نقدي");
        });
        
        amountCol.setCellValueFactory(data -> 
            new SimpleStringProperty(numberFormat.format(data.getValue().getNetAmount())));
        
        currencyCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getCurrency()));
        
        descriptionCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getDescription()));
        
        createdByCol.setCellValueFactory(data -> 
            new SimpleStringProperty(data.getValue().getCreatedBy()));
        
        vouchersTable.setItems(vouchers);
        
        // Row styling for cancelled vouchers
        vouchersTable.setRowFactory(tv -> new TableRow<Voucher>() {
            @Override
            protected void updateItem(Voucher voucher, boolean empty) {
                super.updateItem(voucher, empty);
                if (voucher == null || empty) {
                    setStyle("");
                } else if (voucher.getIsCancelled()) {
                    setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;");
                } else {
                    setStyle("");
                }
            }
        });
    }
    
    private void setupDatePickers() {
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());
    }
    
    private void updateTitle() {
        if (voucherType == VoucherType.RECEIPT) {
            titleLabel.setText("سندات القبض");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #166534;");
        } else {
            titleLabel.setText("سندات الدفع");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #991b1b;");
        }
    }
    
    private void loadVouchers() {
        List<Voucher> voucherList = voucherService.getVouchersByType(voucherType);
        vouchers.setAll(voucherList);
        updateSummary();
    }
    
    @FXML
    private void handleSearch() {
        String searchTerm = searchField.getText();
        LocalDateTime from = fromDatePicker.getValue() != null ? 
            fromDatePicker.getValue().atStartOfDay() : null;
        LocalDateTime to = toDatePicker.getValue() != null ? 
            toDatePicker.getValue().atTime(23, 59, 59) : null;
        
        List<Voucher> results = voucherService.searchVouchers(searchTerm, voucherType, from, to);
        vouchers.setAll(results);
        updateSummary();
    }
    
    private void updateSummary() {
        int count = vouchers.size();
        double totalIqd = vouchers.stream()
            .filter(v -> "دينار".equals(v.getCurrency()))
            .mapToDouble(Voucher::getNetAmount)
            .sum();
        double totalUsd = vouchers.stream()
            .filter(v -> "دولار".equals(v.getCurrency()))
            .mapToDouble(Voucher::getNetAmount)
            .sum();
        
        totalCountLabel.setText(String.valueOf(count));
        totalIqdLabel.setText(numberFormat.format(totalIqd) + " د.ع");
        totalUsdLabel.setText(numberFormat.format(totalUsd) + " $");
    }
    
    @FXML
    private void viewVoucherDetails() {
        Voucher selected = vouchersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سند");
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append("رقم السند: ").append(selected.getVoucherNumber()).append("\n");
        details.append("التاريخ: ").append(selected.getVoucherDate().toLocalDate()).append("\n");
        details.append("الحساب: ").append(selected.getCustomer() != null ? selected.getCustomer().getName() : "نقدي").append("\n");
        details.append("المبلغ: ").append(numberFormat.format(selected.getAmount())).append(" ").append(selected.getCurrency()).append("\n");
        details.append("الخصم: ").append(numberFormat.format(selected.getDiscountAmount())).append("\n");
        details.append("الصافي: ").append(numberFormat.format(selected.getNetAmount())).append("\n");
        details.append("المبلغ كتابةً: ").append(selected.getAmountInWords()).append("\n");
        details.append("البيان: ").append(selected.getDescription()).append("\n");
        details.append("بواسطة: ").append(selected.getCreatedBy()).append("\n");
        
        if (selected.getIsCancelled()) {
            details.append("\n*** ملغي ***\n");
            details.append("سبب الإلغاء: ").append(selected.getCancelReason()).append("\n");
            details.append("ألغي بواسطة: ").append(selected.getCancelledBy()).append("\n");
        }
        
        showAlert(Alert.AlertType.INFORMATION, "تفاصيل السند", details.toString());
    }
    
    @FXML
    private void printVoucher() {
        Voucher selected = vouchersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سند للطباعة");
            return;
        }

        try {
            File pdfFile = voucherService.generateVoucherReceiptPdf(selected.getId(), SessionManager.getInstance().getCurrentDisplayName());
            if (pdfFile != null && pdfFile.exists()) {
                showPdfPreview(pdfFile);
            } else {
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إنشاء ملف الإيصال");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في طباعة السند: " + e.getMessage());
        }
    }

    private void showPdfPreview(File pdfFile) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/PdfPreview.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("معاينة الطباعة");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(vouchersTable.getScene().getWindow());
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            PdfPreviewController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPdfFile(pdfFile);

            dialogStage.showAndWait();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في عرض معاينة الطباعة: " + e.getMessage());
        }
    }
    
    @FXML
    private void cancelVoucher() {
        Voucher selected = vouchersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار سند للإلغاء");
            return;
        }
        
        if (selected.getIsCancelled()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "هذا السند ملغي مسبقاً");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("إلغاء السند");
        dialog.setHeaderText("إلغاء السند: " + selected.getVoucherNumber());
        dialog.setContentText("سبب الإلغاء:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(reason -> {
            try {
                String cancelledBy = SessionManager.getInstance().getCurrentUser() != null ? 
                    SessionManager.getInstance().getCurrentUser().getDisplayName() : "System";
                voucherService.cancelVoucher(selected.getId(), cancelledBy, reason);
                showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم إلغاء السند بنجاح");
                loadVouchers();
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في إلغاء السند: " + e.getMessage());
            }
        });
    }
    
    @FXML
    private void handleClose() {
        Stage stage = (Stage) vouchersTable.getScene().getWindow();
        stage.close();
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
