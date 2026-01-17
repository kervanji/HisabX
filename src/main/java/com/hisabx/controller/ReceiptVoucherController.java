package com.hisabx.controller;

import com.hisabx.model.Customer;
import com.hisabx.model.Voucher;
import com.hisabx.model.Voucher.VoucherType;
import com.hisabx.service.VoucherService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class ReceiptVoucherController {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptVoucherController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    @FXML private TextField voucherNumberField;
    @FXML private DatePicker voucherDatePicker;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private ComboBox<String> cashAccountCombo;
    @FXML private TextField amountField;
    @FXML private TextField discountPercentField;
    @FXML private TextArea descriptionArea;
    @FXML private Label currencyLabel;
    @FXML private Label previousBalanceLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private TableView<String[]> balanceTable;
    @FXML private TableColumn<String[], String> currencyCol;
    @FXML private TableColumn<String[], String> balanceCol;

    private Stage dialogStage;
    private final VoucherService voucherService = new VoucherService();
    private Voucher currentVoucher;
    private ObservableList<Customer> customers;
    private boolean tabMode = false;

    @FXML
    private void initialize() {
        try {
            setupCurrencyCombo();
            setupCashAccountCombo();
            setupCustomerCombo();
            setupBalanceTable();
            setupListeners();
            handleNew();
        } catch (Exception e) {
            logger.error("Failed to initialize receipt voucher form", e);
            showError("خطأ", "حدث خطأ أثناء تحميل سند القبض. تحقق من إعدادات قاعدة البيانات.");
        }
    }

    private void setupCurrencyCombo() {
        currencyCombo.setItems(FXCollections.observableArrayList("دينار", "دولار"));
        currencyCombo.setValue("دينار");
        currencyCombo.setOnAction(e -> {
            currencyLabel.setText(currencyCombo.getValue());
            updateCustomerBalance();
        });
    }

    private void setupCashAccountCombo() {
        cashAccountCombo.setItems(FXCollections.observableArrayList(
            "صندوق رئيسي", "بنك", "صندوق فرعي"
        ));
        cashAccountCombo.setValue("صندوق رئيسي");
    }

    private void setupCustomerCombo() {
        customers = FXCollections.observableArrayList(voucherService.getAllCustomers());
        customerCombo.setItems(customers);
        
        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) return "";
                return customer.getCustomerCode() + " - " + customer.getName();
            }

            @Override
            public Customer fromString(String string) {
                if (string == null || string.isEmpty()) return null;
                return customers.stream()
                    .filter(c -> (c.getCustomerCode() + " - " + c.getName()).equals(string))
                    .findFirst()
                    .orElse(null);
            }
        });

        customerCombo.setOnAction(e -> updateCustomerBalance());
    }

    private void setupBalanceTable() {
        currencyCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        balanceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));
    }

    private void setupListeners() {
        amountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                amountField.setText(oldVal);
            }
            updateCurrentBalance();
        });

        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                discountPercentField.setText(oldVal);
            }
            updateCurrentBalance();
        });

    }

    private void updateCustomerBalance() {
        Customer customer = customerCombo.getValue();
        if (customer != null) {
            String selectedCurrency = currencyCombo.getValue();
            double balance;
            if ("دولار".equals(selectedCurrency)) {
                balance = customer.getBalanceUsd();
            } else {
                balance = customer.getBalanceIqd();
            }
            previousBalanceLabel.setText(currencyFormat.format(balance));
            
            ObservableList<String[]> balanceData = FXCollections.observableArrayList();
            balanceData.add(new String[]{"دينار", currencyFormat.format(customer.getBalanceIqd())});
            balanceData.add(new String[]{"دولار", currencyFormat.format(customer.getBalanceUsd())});
            balanceTable.setItems(balanceData);
            
            descriptionArea.setText("قبض من حساب .. " + customer.getName());
        }
        updateCurrentBalance();
    }

    private void updateCurrentBalance() {
        Customer customer = customerCombo.getValue();
        if (customer != null) {
            String selectedCurrency = currencyCombo.getValue();
            double previousBalance;
            if ("دولار".equals(selectedCurrency)) {
                previousBalance = customer.getBalanceUsd();
            } else {
                previousBalance = customer.getBalanceIqd();
            }
            double amount = parseAmount(amountField.getText());
            double discountPercent = parseAmount(discountPercentField.getText());
            double discountAmount = amount * (discountPercent / 100);
            double finalAmount = amount - discountAmount;
            
            double newBalance = previousBalance - finalAmount;
            currentBalanceLabel.setText(currencyFormat.format(newBalance));
        }
    }

    private double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @FXML
    private void handleNew() {
        currentVoucher = null;
        long nextNumber = voucherService.getNextReceiptVoucherNumber();
        voucherNumberField.setText(String.valueOf(nextNumber));
        voucherDatePicker.setValue(LocalDate.now());
        currencyCombo.setValue("دينار");
        customerCombo.setValue(null);
        cashAccountCombo.setValue("صندوق رئيسي");
        amountField.clear();
        discountPercentField.setText("0");
        descriptionArea.clear();
        previousBalanceLabel.setText("0");
        currentBalanceLabel.setText("0");
        balanceTable.getItems().clear();
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) return;

        try {
            Voucher voucher = new Voucher();
            voucher.setVoucherType(VoucherType.RECEIPT);
            voucher.setVoucherDate(LocalDateTime.of(voucherDatePicker.getValue(), LocalTime.now()));
            voucher.setCurrency(currencyCombo.getValue());
            voucher.setExchangeRate(1.0);
            voucher.setCustomer(customerCombo.getValue());
            voucher.setAccountName(customerCombo.getValue() != null ? customerCombo.getValue().getName() : "");
            voucher.setCashAccount(cashAccountCombo.getValue());
            voucher.setAmount(parseAmount(amountField.getText()));
            
            double discountPercent = parseAmount(discountPercentField.getText());
            voucher.setDiscountPercentage(discountPercent);
            voucher.setDiscountAmount(voucher.getAmount() * (discountPercent / 100));
            
            voucher.setDescription(descriptionArea.getText());

            Voucher savedVoucher = voucherService.createReceiptVoucher(voucher);
            
            showSuccess("تم الحفظ", "تم حفظ سند القبض رقم: " + savedVoucher.getVoucherNumber());
            handleNew();
            
        } catch (Exception e) {
            logger.error("Failed to save receipt voucher", e);
            showError("خطأ", "فشل في حفظ سند القبض: " + e.getMessage());
        }
    }

    private boolean validateInput() {
        if (amountField.getText() == null || amountField.getText().trim().isEmpty()) {
            showError("خطأ", "يرجى إدخال المبلغ");
            return false;
        }
        
        double amount = parseAmount(amountField.getText());
        if (amount <= 0) {
            showError("خطأ", "المبلغ يجب أن يكون أكبر من صفر");
            return false;
        }
        
        if (voucherDatePicker.getValue() == null) {
            showError("خطأ", "يرجى اختيار التاريخ");
            return false;
        }
        
        return true;
    }

    @FXML
    private void handlePrint() {
        showInfo("طباعة", "سيتم إضافة ميزة الطباعة قريباً");
    }

    @FXML
    private void handlePreviousVouchers() {
        try {
            List<Voucher> vouchers = voucherService.getReceiptVouchers();
            
            StringBuilder sb = new StringBuilder();
            sb.append("السندات السابقة (سندات القبض):\n\n");
            
            for (Voucher v : vouchers) {
                sb.append("رقم: ").append(v.getVoucherNumber());
                sb.append(" | المبلغ: ").append(currencyFormat.format(v.getAmount()));
                sb.append(" ").append(v.getCurrency());
                if (v.getCustomer() != null) {
                    sb.append(" | الحساب: ").append(v.getCustomer().getName());
                }
                sb.append("\n");
            }
            
            if (vouchers.isEmpty()) {
                sb.append("لا توجد سندات قبض سابقة");
            }
            
            showInfo("السندات السابقة", sb.toString());
            
        } catch (Exception e) {
            logger.error("Failed to load previous vouchers", e);
            showError("خطأ", "فشل في تحميل السندات السابقة");
        }
    }

    @FXML
    private void handleCancel() {
        if (tabMode) {
            com.hisabx.util.TabManager.getInstance().closeTab("receipt-voucher");
        } else if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
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
