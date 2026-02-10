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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class PurchaseController implements Initializable {

    @FXML private TextField voucherNumberField;
    @FXML private DatePicker voucherDatePicker;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField discountPercentField;
    @FXML private TextField discountAmountField;
    @FXML private TextField descriptionField;
    @FXML private TextArea notesArea;
    @FXML private Label previousBalanceLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private Label itemsTotalLabel;
    @FXML private Label totalAmountLabel;
    @FXML private CheckBox printCheckbox;

    // Items table
    @FXML private TableView<PurchaseItemRow> itemsTable;
    @FXML private TableColumn<PurchaseItemRow, String> itemProductColumn;
    @FXML private TableColumn<PurchaseItemRow, Double> itemQuantityColumn;
    @FXML private TableColumn<PurchaseItemRow, String> itemUnitColumn;
    @FXML private TableColumn<PurchaseItemRow, Double> itemPriceColumn;
    @FXML private TableColumn<PurchaseItemRow, Double> itemSalePriceColumn;
    @FXML private TableColumn<PurchaseItemRow, Double> itemTotalColumn;
    @FXML private TableColumn<PurchaseItemRow, Void> itemDeleteColumn;

    private final VoucherService voucherService = new VoucherService();
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.##");
    private static final String DEFAULT_CASH_ACCOUNT = "صندوق 181";
    private static final String DEFAULT_CURRENCY = "دينار";

    private ObservableList<Customer> customers;
    private ObservableList<Product> products;
    private Customer selectedCustomer;
    private ObservableList<PurchaseItemRow> itemRows = FXCollections.observableArrayList();

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
        loadProducts();
        setupItemsTable();
        setupListeners();
        handleNew();
    }

    private void setupForm() {
        voucherDatePicker.setValue(LocalDate.now());

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

    private void loadProducts() {
        products = FXCollections.observableArrayList(inventoryService.getActiveProducts());
    }

    private void setupItemsTable() {
        itemProductColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        itemQuantityColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getQuantity()).asObject());
        itemUnitColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUnitOfMeasure()));
        itemPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getUnitPrice()).asObject());
        itemSalePriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSalePrice()).asObject());
        itemTotalColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getTotalPrice()).asObject());

        // Inline editing
        itemProductColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemProductColumn.setOnEditCommit(ev -> {
            ev.getRowValue().setProductName(ev.getNewValue());
            recalculateTotal();
        });

        itemUnitColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        itemUnitColumn.setOnEditCommit(ev -> ev.getRowValue().setUnitOfMeasure(ev.getNewValue()));

        itemQuantityColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemQuantityColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setQuantity(v > 0 ? v : 1);
            ev.getRowValue().recalcTotal();
            recalculateTotal();
        });

        itemPriceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemPriceColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setUnitPrice(v);
            ev.getRowValue().recalcTotal();
            recalculateTotal();
        });

        itemSalePriceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemSalePriceColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setSalePrice(v);
        });

        itemTotalColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        itemTotalColumn.setOnEditCommit(ev -> {
            double v = ev.getNewValue() != null ? ev.getNewValue() : 0;
            ev.getRowValue().setTotalPrice(v);
            recalculateTotal();
        });

        // Delete button column
        itemDeleteColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("✕");
            {
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
                deleteBtn.setOnAction(e -> {
                    PurchaseItemRow row = getTableView().getItems().get(getIndex());
                    itemRows.remove(row);
                    recalculateTotal();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        itemsTable.setItems(itemRows);
        itemsTable.setEditable(true);
    }

    private void setupListeners() {
        customerCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal;
            updateCustomerBalanceDisplay();
            updateDescription();
        });

        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    double percent = Double.parseDouble(newVal);
                    double total = calculateItemsTotal();
                    double discountAmount = total * percent / 100;
                    discountAmountField.setText(numberFormat.format(discountAmount));
                    recalculateTotal();
                } catch (NumberFormatException ignored) {}
            }
        });

        discountAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            recalculateTotal();
        });

    }

    private void updateCustomerBalanceDisplay() {
        if (selectedCustomer != null) {
            double balance = selectedCustomer.getBalanceIqd();
            previousBalanceLabel.setText(numberFormat.format(balance));
            currentBalanceLabel.setText(numberFormat.format(balance) + " د.ع");
        } else {
            previousBalanceLabel.setText("0");
            currentBalanceLabel.setText("0 د.ع");
        }
    }

    private void updateDescription() {
        if (selectedCustomer != null) {
            descriptionField.setText("مشتريات من .. " + selectedCustomer.getName());
        } else {
            descriptionField.setText("");
        }
    }

    private double calculateItemsTotal() {
        double total = 0;
        for (PurchaseItemRow row : itemRows) {
            total += row.getTotalPrice();
        }
        return total;
    }

    private void recalculateTotal() {
        double itemsTotal = calculateItemsTotal();
        double discount = parseAmount(discountAmountField.getText());
        double netTotal = itemsTotal - discount;
        String currency = DEFAULT_CURRENCY;
        String suffix = "دولار".equals(currency) ? " $" : " د.ع";

        itemsTotalLabel.setText(numberFormat.format(itemsTotal) + suffix);
        totalAmountLabel.setText(numberFormat.format(netTotal) + suffix);

        // Update current balance preview
        if (selectedCustomer != null) {
            double currentBalance = selectedCustomer.getBalanceIqd();
            double newBalance = currentBalance - netTotal;
            currentBalanceLabel.setText(numberFormat.format(newBalance) + suffix);
        }
    }

    @FXML
    private void handleAddItem() {
        Dialog<PurchaseItemRow> dialog = new Dialog<>();
        dialog.setTitle("إضافة مادة");
        dialog.setHeaderText("أضف مادة جديدة للمشتريات");

        ButtonType addButtonType = new ButtonType("إضافة", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Form layout
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));

        ComboBox<Product> productCombo = new ComboBox<>(FXCollections.observableArrayList(products));
        productCombo.setEditable(true);
        productCombo.setPrefWidth(250);
        productCombo.setPromptText("اختر المادة أو اكتب اسمها");
        productCombo.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                if (p == null) return "";
                return p.getProductCode() + " - " + p.getName();
            }

            @Override
            public Product fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return products.stream()
                    .filter(p -> (p.getProductCode() + " - " + p.getName()).equals(s) || p.getName().contains(s))
                    .findFirst().orElse(null);
            }
        });

        TextField quantityField = new TextField("1");
        quantityField.setPrefWidth(80);

        TextField unitField = new TextField();
        unitField.setPrefWidth(80);
        unitField.setPromptText("(اختياري)");

        TextField priceField = new TextField("0");
        priceField.setPrefWidth(120);

        TextField salePriceField = new TextField("0");
        salePriceField.setPrefWidth(120);

        Label marginLabel = new Label("");
        marginLabel.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");

        ComboBox<String> categoryCombo = new ComboBox<>(FXCollections.observableArrayList(inventoryService.getAllCategories()));
        categoryCombo.setEditable(true);
        categoryCombo.setPrefWidth(250);
        categoryCombo.setPromptText("اختر الفئة");

        // Auto-fill from product selection
        productCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.getCostPrice() != null && newVal.getCostPrice() > 0) {
                    priceField.setText(String.valueOf(newVal.getCostPrice()));
                } else if (newVal.getUnitPrice() != null) {
                    priceField.setText(String.valueOf(newVal.getUnitPrice()));
                }
                if (newVal.getUnitPrice() != null) {
                    salePriceField.setText(String.valueOf(newVal.getUnitPrice()));
                }
                if (newVal.getUnitOfMeasure() != null) {
                    unitField.setText(newVal.getUnitOfMeasure());
                }
                if (newVal.getCategory() != null && !newVal.getCategory().isEmpty()) {
                    categoryCombo.setValue(newVal.getCategory());
                }
            }
        });

        // margin update
        Runnable updateMargin = () -> {
            double cost = parseAmount(priceField.getText());
            double sale = parseAmount(salePriceField.getText());
            if (cost <= 0 || sale <= 0) {
                marginLabel.setText("");
                return;
            }
            double margin = (sale - cost) / cost * 100.0;
            marginLabel.setText(String.format("هامش الربح: %.2f%%", margin));
        };
        priceField.textProperty().addListener((o, a, b) -> updateMargin.run());
        salePriceField.textProperty().addListener((o, a, b) -> updateMargin.run());

        grid.add(new Label("المادة"), 0, 0);
        grid.add(productCombo, 1, 0);
        grid.add(new Label("الفئة"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("الكمية"), 0, 2);
        grid.add(quantityField, 1, 2);
        grid.add(new Label("الوحدة"), 0, 3);
        grid.add(unitField, 1, 3);
        grid.add(new Label("سعر الوحدة"), 0, 4);
        grid.add(priceField, 1, 4);
        grid.add(new Label("سعر البيع"), 0, 5);
        grid.add(salePriceField, 1, 5);
        grid.add(marginLabel, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(450);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Product selectedProduct = productCombo.getValue();
                String productName;
                if (selectedProduct != null) {
                    productName = selectedProduct.getName();
                } else {
                    // Allow manual product name entry
                    String editorText = productCombo.getEditor().getText();
                    if (editorText == null || editorText.trim().isEmpty()) {
                        return null;
                    }
                    productName = editorText.trim();
                }

                double qty = parseAmount(quantityField.getText());
                if (qty <= 0) qty = 1;
                double price = parseAmount(priceField.getText());
                double salePrice = parseAmount(salePriceField.getText());
                String unit = unitField.getText() != null ? unitField.getText().trim() : "";
                String category = categoryCombo.getValue() != null ? categoryCombo.getValue().trim() :
                    (categoryCombo.getEditor().getText() != null ? categoryCombo.getEditor().getText().trim() : "");
                PurchaseItemRow row = new PurchaseItemRow(
                    selectedProduct, productName, qty, unit, price, salePrice, category
                );
                return row;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(row -> {
            itemRows.add(row);
            recalculateTotal();
        });
    }

    @FXML
    private void handleSave() {
        try {
            if (selectedCustomer == null) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى اختيار المورد/الحساب");
                return;
            }

            if (itemRows.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "يرجى إضافة مادة واحدة على الأقل");
                return;
            }

            double itemsTotal = calculateItemsTotal();
            double discount = parseAmount(discountAmountField.getText());
            double netAmount = itemsTotal - discount;

            if (netAmount <= 0) {
                showAlert(Alert.AlertType.WARNING, "تنبيه", "المبلغ الصافي يجب أن يكون أكبر من صفر");
                return;
            }

            // Create voucher
            Voucher voucher = new Voucher();
            voucher.setVoucherType(VoucherType.PURCHASE);
            voucher.setVoucherNumber(voucherNumberField.getText());
            voucher.setVoucherDate(voucherDatePicker.getValue().atStartOfDay());
            voucher.setCurrency(DEFAULT_CURRENCY);
            voucher.setExchangeRate(1.0);
            voucher.setCustomer(selectedCustomer);
            voucher.setCashAccount(DEFAULT_CASH_ACCOUNT);
            voucher.setAmount(itemsTotal);
            voucher.setDiscountPercentage(parseAmount(discountPercentField.getText()));
            voucher.setDiscountAmount(discount);
            voucher.setNetAmount(netAmount);
            voucher.setDescription(descriptionField.getText());
            if (notesArea != null && notesArea.getText() != null && !notesArea.getText().isEmpty()) {
                voucher.setNotes(notesArea.getText());
            }
            voucher.setCreatedBy(SessionManager.getInstance().getCurrentUser() != null ?
                SessionManager.getInstance().getCurrentUser().getDisplayName() : "System");

            // Add items - create new products if needed
            for (PurchaseItemRow row : itemRows) {
                Product product = row.getProduct();

                // If product doesn't exist, create it
                if (product == null) {
                    Product newProduct = new Product();
                    newProduct.setName(row.getProductName());
                    newProduct.setCategory(row.getCategory());
                    newProduct.setUnitOfMeasure(row.getUnitOfMeasure());
                    newProduct.setCostPrice(row.getUnitPrice());
                    newProduct.setUnitPrice(row.getSalePrice() > 0 ? row.getSalePrice() : row.getUnitPrice());
                    newProduct.setQuantityInStock(0.0);
                    newProduct.setMinimumStock(0.0);
                    newProduct.setIsActive(true);
                    product = inventoryService.createProduct(newProduct);
                }

                VoucherItem item = new VoucherItem();
                item.setProduct(product);
                item.setProductName(row.getProductName());
                item.setQuantity(row.getQuantity());
                item.setUnitPrice(row.getUnitPrice());
                item.setTotalPrice(row.getTotalPrice());
                item.setUnitOfMeasure(row.getUnitOfMeasure());
                item.setAddToInventory(true);
                voucher.addItem(item);
            }

            voucher = voucherService.saveVoucher(voucher);

            showAlert(Alert.AlertType.INFORMATION, "نجاح", "تم حفظ المشتريات بنجاح: " + voucher.getVoucherNumber());

            if (printCheckbox.isSelected()) {
                File pdfFile = voucherService.generateVoucherReceiptPdf(voucher.getId(), SessionManager.getInstance().getCurrentDisplayName());
                if (pdfFile != null && pdfFile.exists()) {
                    showPdfPreview(pdfFile);
                }
            }

            handleNew();
            loadCustomers();
            loadProducts();

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE") && e.getMessage().contains("voucher_number")) {
                voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PURCHASE));
            }
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في حفظ المشتريات: " + e.getMessage());
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
        voucherNumberField.setText(voucherService.generateVoucherNumber(VoucherType.PURCHASE));
        voucherDatePicker.setValue(LocalDate.now());
        customerCombo.setValue(null);
        discountPercentField.setText("0");
        discountAmountField.setText("0");
        descriptionField.setText("");
        if (notesArea != null) {
            notesArea.setText("");
        }
        printCheckbox.setSelected(false);
        itemRows.clear();
        itemsTotalLabel.setText("0");
        totalAmountLabel.setText("0");

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
            stage.setTitle("إضافة مورد/حساب جديد");
            Scene scene = new Scene(root);
            com.hisabx.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            loadCustomers();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح نافذة إضافة الحساب");
        }
    }

    @FXML
    private void showPreviousPurchases() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/VoucherList.fxml"));
            Parent root = loader.load();

            VoucherListController controller = loader.getController();
            controller.setVoucherType(VoucherType.PURCHASE);

            Stage stage = new Stage();
            stage.setTitle("المشتريات السابقة");
            Scene scene = new Scene(root);
            com.hisabx.MainApp.applyCurrentFontSize(scene);
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "فشل في فتح قائمة المشتريات");
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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Inner class for table row ==========
    public static class PurchaseItemRow {
        private Product product;
        private String productName;
        private double quantity;
        private String unitOfMeasure;
        private double unitPrice;
        private double salePrice;
        private double totalPrice;
        private String category;

        public PurchaseItemRow(Product product, String productName, double quantity,
                               String unitOfMeasure, double unitPrice, double salePrice, String category) {
            this.product = product;
            this.productName = productName;
            this.quantity = quantity;
            this.unitOfMeasure = unitOfMeasure;
            this.unitPrice = unitPrice;
            this.salePrice = salePrice;
            this.totalPrice = quantity * unitPrice;
            this.category = category;
        }

        public void setProductName(String name) { this.productName = name; }
        public void setQuantity(double q) { this.quantity = q; }
        public void setUnitOfMeasure(String unit) { this.unitOfMeasure = unit; }
        public void setUnitPrice(double price) { this.unitPrice = price; }
        public void setSalePrice(double price) { this.salePrice = price; }
        public void setTotalPrice(double total) { this.totalPrice = total; }
        public void recalcTotal() { this.totalPrice = this.quantity * this.unitPrice; }

        public Product getProduct() { return product; }
        public String getProductName() { return productName; }
        public double getQuantity() { return quantity; }
        public String getUnitOfMeasure() { return unitOfMeasure; }
        public double getUnitPrice() { return unitPrice; }
        public double getSalePrice() { return salePrice; }
        public double getTotalPrice() { return totalPrice; }
        public String getCategory() { return category; }
    }
}
