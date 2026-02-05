package com.hisabx.controller;

import com.hisabx.model.*;
import com.hisabx.service.CustomerService;
import com.hisabx.service.InventoryService;
import com.hisabx.service.VoucherService;
import com.hisabx.util.SessionManager;
import com.hisabx.util.TabManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class PaymentVoucherController implements Initializable {
    
    @FXML private TextField voucherNumberField;
    @FXML private DatePicker voucherDatePicker;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> amountCurrencyCombo;
    @FXML private TextField discountPercentField;
    @FXML private TextField discountAmountField;
    @FXML private Label amountInWordsLabel;
    @FXML private TextField descriptionField;
    @FXML private TextArea notesArea;
    @FXML private Label previousBalanceLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private Label balanceIqdLabel;
    @FXML private Label balanceUsdLabel;
    @FXML private CheckBox printCheckbox;
    @FXML private VBox otherCurrenciesBox;
    
    // Installment fields
    @FXML private CheckBox installmentCheckbox;
    @FXML private HBox installmentOptionsBox;
    @FXML private TextField installmentCountField;
    @FXML private DatePicker firstInstallmentDatePicker;
    
    // Products table
    @FXML private TitledPane productsPane;
    @FXML private CheckBox addToInventoryCheckbox;
    @FXML private TableView<VoucherItem> itemsTable;
    @FXML private TableColumn<VoucherItem, String> productNameCol;
    @FXML private TableColumn<VoucherItem, Double> quantityCol;
    @FXML private TableColumn<VoucherItem, Double> unitPriceCol;
    @FXML private TableColumn<VoucherItem, Double> totalPriceCol;
    @FXML private TableColumn<VoucherItem, Void> actionsCol;
    @FXML private Label itemsTotalLabel;
    @FXML private Label paidAmountLabel;
    @FXML private Label remainingAmountLabel;
    
    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private static final String DEFAULT_CASH_ACCOUNT = "صندوق 181";
    
    private ObservableList<Customer> customers;
    private ObservableList<VoucherItem> voucherItems = FXCollections.observableArrayList();
    private Customer selectedCustomer;

    private boolean amountManuallyEdited = false;
    private boolean updatingAmountProgrammatically = false;

    private boolean tabMode = false;
    private String tabId;

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupForm();
        loadCustomers();
        setupListeners();
        setupItemsTable();
        handleNew();
    }
    
    private void setupForm() {
        voucherDatePicker.setValue(LocalDate.now());
        
        // تعبئة القوائم المنسدلة
        amountCurrencyCombo.setItems(FXCollections.observableArrayList("دينار", "دولار"));
        amountCurrencyCombo.setValue("دينار");
        firstInstallmentDatePicker.setValue(LocalDate.now().plusMonths(1));
        
        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer customer) {
                if (customer == null) return "";
                return customer.getCustomerCode() + " - " + customer.getName();
            }
            
            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return customers.stream()
                    .filter(c -> (c.getCustomerCode() + " - " + c.getName()).equals(s) || c.getName().contains(s))
                    .findFirst().orElse(null);
            }
        });
    }
    
    private void loadCustomers() {
        customers = FXCollections.observableArrayList(customerService.getAllCustomers());
        customerCombo.setItems(customers);
    }
    
    private void setupListeners() {
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
            updateCustomerBalanceDisplay();
            updateDescription();
        });
        
        amountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingAmountProgrammatically) {
                amountManuallyEdited = true;
            }
            calculateNetAmount();
            updateAmountInWords();
            updatePaidAndRemainingLabels();
        });
        
        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    double percent = Double.parseDouble(newVal);
                    double amount = parseAmount(amountField.getText());
                    double discountAmount = amount * percent / 100;
                    discountAmountField.setText(numberFormat.format(discountAmount));
                    calculateNetAmount();
                } catch (NumberFormatException ignored) {}
            }
        });
        
        discountAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            calculateNetAmount();
            updateAmountInWords();
            updatePaidAndRemainingLabels();
        });

        amountCurrencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateAmountInWords();
            calculateNetAmount();
            updatePaidAndRemainingLabels();
        });
    }
    
    private void setupItemsTable() {
        productNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getQuantity()).asObject());
        unitPriceCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());
        totalPriceCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalPrice()).asObject());
        
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("تعديل");
            private final Button deleteBtn = new Button("حذف");
            private final HBox box = new HBox(6);
            {
                editBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 10;");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 10;");

                box.getChildren().addAll(editBtn, deleteBtn);
                box.setStyle("-fx-alignment: CENTER;");

                editBtn.setOnAction(e -> {
                    VoucherItem item = getTableView().getItems().get(getIndex());
                    editProductItem(item);
                });
                deleteBtn.setOnAction(e -> {
                    VoucherItem item = getTableView().getItems().get(getIndex());
                    voucherItems.remove(item);
                    updateItemsTotal();
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        
        itemsTable.setItems(voucherItems);
    }
    
    private void updateCustomerBalanceDisplay() {
        if (selectedCustomer != null) {
            double balanceIqd = selectedCustomer.getBalanceIqd();
            double balanceUsd = selectedCustomer.getBalanceUsd();
            
            previousBalanceLabel.setText(numberFormat.format(balanceIqd));
            currentBalanceLabel.setText(numberFormat.format(balanceIqd) + " د.ع");
            balanceIqdLabel.setText(numberFormat.format(balanceIqd));
            balanceUsdLabel.setText(numberFormat.format(balanceUsd));
        } else {
            previousBalanceLabel.setText("0");
            currentBalanceLabel.setText("0 د.ع");
            balanceIqdLabel.setText("0");
            balanceUsdLabel.setText("0");
        }
    }
    
    private void updateDescription() {
        if (selectedCustomer != null) {
            descriptionField.setText("دفع لحساب .. " + selectedCustomer.getName());
        } else {
            descriptionField.setText("");
        }
    }
    
    private void calculateNetAmount() {
        try {
            double amount = parseAmount(amountField.getText());
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = amount - discount;
            
            if (selectedCustomer != null) {
                String currency = amountCurrencyCombo.getValue();
                double currentBalance = "دولار".equals(currency) ? 
                    selectedCustomer.getBalanceUsd() : selectedCustomer.getBalanceIqd();
                double newBalance = currentBalance - netAmount;
                currentBalanceLabel.setText(numberFormat.format(newBalance) + 
                    ("دولار".equals(currency) ? " $" : " د.ع"));
            }
        } catch (Exception ignored) {}
    }
    
    private void updateAmountInWords() {
        try {
            double amount = parseAmount(amountField.getText());
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = amount - discount;
            String currency = amountCurrencyCombo.getValue();
            
            String words = convertToWords(netAmount, currency);
            amountInWordsLabel.setText(words);
        } catch (Exception e) {
            amountInWordsLabel.setText("صفر");
        }
    }
    
    private double parseAmount(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private String convertToWords(double amount, String currency) {
        if (amount == 0) return "صفر";
        
        long wholeNumber = (long) amount;
        String currencyName = "دينار".equals(currency) ? "دينار عراقي" : "دولار أمريكي";
        
        return convertNumberToArabic(wholeNumber) + " " + currencyName + " لا غير";
    }
    
    private String convertNumberToArabic(long number) {
        if (number == 0) return "صفر";
        
        String[] ones = {"", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
                "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"};
        String[] tens = {"", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"};
        
        if (number < 20) return ones[(int) number];
        if (number < 100) {
            int remainder = (int) (number % 10);
            if (remainder == 0) return tens[(int) (number / 10)];
            return ones[remainder] + " و" + tens[(int) (number / 10)];
        }
        if (number < 1000) {
            int hundreds = (int) (number / 100);
            int remainder = (int) (number % 100);
            String hundredWord = hundreds == 1 ? "مائة" : hundreds == 2 ? "مائتان" : ones[hundreds] + " مائة";
            if (remainder == 0) return hundredWord;
            return hundredWord + " و" + convertNumberToArabic(remainder);
        }
        if (number < 1000000) {
            int thousands = (int) (number / 1000);
            int remainder = (int) (number % 1000);
            String thousandWord;
            if (thousands == 1) thousandWord = "ألف";
            else if (thousands == 2) thousandWord = "ألفان";
            else if (thousands <= 10) thousandWord = ones[thousands] + " آلاف";
            else thousandWord = convertNumberToArabic(thousands) + " ألف";
            
            if (remainder == 0) return thousandWord;
            return thousandWord + " و" + convertNumberToArabic(remainder);
        }
        
        return String.valueOf(number);
    }
    
    @FXML
    private void toggleInstallmentOptions() {
        boolean show = installmentCheckbox.isSelected();
        installmentOptionsBox.setVisible(show);
        installmentOptionsBox.setManaged(show);
    }
    
    @FXML
    private void addProductItem() {
        VoucherItem item = showVoucherItemForm(null);
        if (item != null) {
            voucherItems.add(item);
            updateItemsTotal();
        }
    }

    private void editProductItem(VoucherItem existingItem) {
        if (existingItem == null) {
            return;
        }
        VoucherItem updated = showVoucherItemForm(existingItem);
        if (updated != null) {
            itemsTable.refresh();
            updateItemsTotal();
        }
    }

    private VoucherItem showVoucherItemForm(VoucherItem existingItem) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherItemForm.fxml"));
            Parent root = loader.load();

            VoucherItemFormController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle(existingItem == null ? "إضافة مادة" : "تعديل مادة");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            controller.setDialogStage(stage);

            boolean defaultAddToInventory = addToInventoryCheckbox != null && addToInventoryCheckbox.isSelected();
            controller.setDefaultAddToInventory(defaultAddToInventory);

            if (existingItem != null) {
                controller.setVoucherItem(existingItem);
            }

            stage.showAndWait();

            if (controller.isSaved()) {
                return controller.getVoucherItem();
            }
            return null;
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نافذة المادة: " + e.getMessage());
            return null;
        }
    }
    
    private void updateItemsTotal() {
        double total = voucherItems.stream()
            .mapToDouble(VoucherItem::getTotalPrice)
            .sum();
        itemsTotalLabel.setText(numberFormat.format(total));

        syncAmountWithItemsTotalIfNeeded(total);
        updatePaidAndRemainingLabels();
    }

    private void syncAmountWithItemsTotalIfNeeded(double itemsTotal) {
        if (amountField == null) {
            return;
        }

        boolean shouldAutoFill = !amountManuallyEdited;
        if (shouldAutoFill) {
            updatingAmountProgrammatically = true;
            amountField.setText(numberFormat.format(itemsTotal));
            updatingAmountProgrammatically = false;
            calculateNetAmount();
            updateAmountInWords();
        }
    }

    private void updatePaidAndRemainingLabels() {
        if (paidAmountLabel == null || remainingAmountLabel == null) {
            return;
        }
        double itemsTotal = 0;
        try {
            String t = itemsTotalLabel != null ? itemsTotalLabel.getText() : "0";
            itemsTotal = Double.parseDouble(t.replace(",", ""));
        } catch (Exception ignored) {
        }

        double paid = parseAmount(amountField != null ? amountField.getText() : "0");
        double discount = parseAmount(discountAmountField != null ? discountAmountField.getText() : "0");
        double netPaid = Math.max(0, paid - discount);
        double remaining = itemsTotal - netPaid;

        paidAmountLabel.setText(numberFormat.format(netPaid));
        remainingAmountLabel.setText(numberFormat.format(remaining));
    }
    
    @FXML
    private void handleSave() {
        try {
            if (selectedCustomer == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار الحساب/المورد");
                return;
            }
            
            double amount = parseAmount(amountField.getText());
            if (amount <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إدخال مبلغ صحيح");
                return;
            }
            
            // Create voucher
            Voucher voucher = new Voucher();
            voucher.setVoucherType(VoucherType.PAYMENT);
            voucher.setVoucherNumber(voucherNumberField.getText());
            voucher.setVoucherDate(voucherDatePicker.getValue().atStartOfDay());
            voucher.setCurrency(amountCurrencyCombo.getValue());
            voucher.setExchangeRate(1.0);
            voucher.setCustomer(selectedCustomer);
            voucher.setCashAccount(DEFAULT_CASH_ACCOUNT);
            voucher.setAmount(amount);
            voucher.setDiscountPercentage(parseAmount(discountPercentField.getText()));
            voucher.setDiscountAmount(parseAmount(discountAmountField.getText()));
            voucher.setNetAmount(amount - parseAmount(discountAmountField.getText()));
            voucher.setAmountInWords(amountInWordsLabel.getText());
            voucher.setDescription(descriptionField.getText());
            if (notesArea != null && notesArea.getText() != null && !notesArea.getText().isEmpty()) {
                voucher.setNotes(notesArea.getText());
            }
            voucher.setCreatedBy(SessionManager.getInstance().getCurrentUser() != null ? 
                SessionManager.getInstance().getCurrentUser().getDisplayName() : "System");
            
            // Add items
            for (VoucherItem item : voucherItems) {
                voucher.addItem(item);
            }
            
            // Save with or without installments
            if (installmentCheckbox.isSelected()) {
                int installmentCount = Integer.parseInt(installmentCountField.getText());
                LocalDate firstDate = firstInstallmentDatePicker.getValue();
                voucher = voucherService.saveVoucherWithInstallments(voucher, installmentCount, firstDate);
            } else {
                voucher = voucherService.saveVoucher(voucher);
            }
            
            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ سند الدفع بنجاح: " + voucher.getVoucherNumber());
            
            if (printCheckbox.isSelected()) {
                File pdfFile = voucherService.generateVoucherReceiptPdf(voucher.getId(), SessionManager.getInstance().getCurrentDisplayName());
                if (pdfFile != null && pdfFile.exists()) {
                    showPdfPreview(pdfFile);
                }
            }
            
            handleNew();
            loadCustomers();
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE") && e.getMessage().contains("voucher_number")) {
                voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PAYMENT));
            }
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حفظ السند: " + e.getMessage());
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
            dialogStage.initOwner(voucherNumberField.getScene().getWindow());
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
    private void handleNew() {
        voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PAYMENT));
        voucherDatePicker.setValue(LocalDate.now());
        customerCombo.setValue(null);
        amountField.setText("");
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        descriptionField.setText("");
        if (notesArea != null) {
            notesArea.setText("");
        }
        amountInWordsLabel.setText("صفر");
        printCheckbox.setSelected(false);
        installmentCheckbox.setSelected(false);
        installmentOptionsBox.setVisible(false);
        installmentOptionsBox.setManaged(false);
        installmentCountField.setText("");
        firstInstallmentDatePicker.setValue(LocalDate.now().plusMonths(1));
        
        voucherItems.clear();
        updateItemsTotal();
        
        selectedCustomer = null;
        updateCustomerBalanceDisplay();
    }
    
    @FXML
    private void handleClose() {
        if (tabMode && tabId != null && !tabId.isBlank()) {
            TabManager.getInstance().closeTab(tabId);
            return;
        }
        Stage stage = (Stage) voucherNumberField.getScene().getWindow();
        stage.close();
    }
    
    @FXML
    private void addNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/CustomerForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إضافة حساب/مورد جديد");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            
            loadCustomers();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نافذة إضافة الحساب");
        }
    }
    
    @FXML
    private void showPreviousVouchers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();
            
            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.PAYMENT);
            
            Stage stage = new Stage();
            stage.setTitle("سندات الدفع السابقة");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
            
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح قائمة السندات");
        }
    }
    
    @FXML
    private void showCustomerStatement() {
        if (selectedCustomer != null) {
            // TODO: Show customer statement for IQD
            showAlert(Alert.AlertType.INFORMATION, "كشف حساب", 
                "كشف حساب " + selectedCustomer.getName() + " بالدينار\nالرصيد: " + 
                numberFormat.format(selectedCustomer.getBalanceIqd()) + " د.ع");
        }
    }
    
    @FXML
    private void showCustomerStatementUsd() {
        if (selectedCustomer != null) {
            showAlert(Alert.AlertType.INFORMATION, "كشف حساب", 
                "كشف حساب " + selectedCustomer.getName() + " بالدولار\nالرصيد: " + 
                numberFormat.format(selectedCustomer.getBalanceUsd()) + " $");
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
