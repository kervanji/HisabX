package com.hisabx.controller;

import com.hisabx.model.*;
import com.hisabx.service.*;
import com.hisabx.database.Repository.*;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaleFormController {
    private static final Logger logger = LoggerFactory.getLogger(SaleFormController.class);

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<String> projectLocationComboBox;
    @FXML private ComboBox<String> categoryFilterComboBox;
    @FXML private ComboBox<Product> productComboBox;
    @FXML private TextField quantityField;
    @FXML private TextField itemDiscountField;
    @FXML private Label stockLabel;
    @FXML private Label priceLabel;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> productNameColumn;
    @FXML private TableColumn<SaleItemRow, Integer> quantityColumn;
    @FXML private TableColumn<SaleItemRow, Double> unitPriceColumn;
    @FXML private TableColumn<SaleItemRow, Double> discountColumn;
    @FXML private TableColumn<SaleItemRow, Double> totalColumn;
    @FXML private TableColumn<SaleItemRow, Void> actionColumn;
    @FXML private RadioButton cashRadio;
    @FXML private RadioButton creditRadio;
    @FXML private RadioButton cardRadio;
    @FXML private ToggleGroup paymentGroup;
    @FXML private TextField additionalDiscountField;
    @FXML private Label subtotalLabel;
    @FXML private Label discountLabel;
    @FXML private Label finalTotalLabel;
    @FXML private TextField paidAmountField;
    @FXML private Label balanceLabel;
    @FXML private Label balanceStatusLabel;
    @FXML private TextArea notesArea;

    private Stage dialogStage;
    private final SalesService salesService;
    private final ReceiptService receiptService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ObservableList<SaleItemRow> saleItems = FXCollections.observableArrayList();
    private FilteredList<Product> filteredProducts;
    private String productSearchQuery = "";
    private Product selectedProduct = null;
    private final DecimalFormat numberFormatter;
    private com.hisabx.MainApp mainApp;

    public void setMainApp(com.hisabx.MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public SaleFormController() {
        this.salesService = new SalesService();
        this.receiptService = new ReceiptService();
        this.customerRepository = new CustomerRepository();
        this.productRepository = new ProductRepository();
        
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        this.numberFormatter = new DecimalFormat("#,##0.00", symbols);
    }

    @FXML
    private void initialize() {
        setupPaymentToggleGroup();
        setupCustomerComboBox();
        setupProductComboBox();
        setupItemsTable();
        setupDefaults();
    }

    private void setupPaymentToggleGroup() {
        if (paymentGroup == null) {
            paymentGroup = new ToggleGroup();
        }

        cashRadio.setToggleGroup(paymentGroup);
        creditRadio.setToggleGroup(paymentGroup);
        cardRadio.setToggleGroup(paymentGroup);

        // Force single selection (and prevent clearing selection)
        if (paymentGroup.getSelectedToggle() == null) {
            cashRadio.setSelected(true);
        }

        if (paidAmountField != null) {
            paidAmountField.setDisable(creditRadio.isSelected());
        }

        paymentGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }

            if (paidAmountField != null) {
                boolean isDebt = creditRadio.isSelected();
                paidAmountField.setDisable(isDebt);
                if (isDebt) {
                    paidAmountField.setText("0");
                }
                handlePaidAmountChange();
            }
        });
    }

    private void setupCategoryFilter(List<Product> products) {
        if (categoryFilterComboBox == null) {
            return;
        }

        List<String> categories = products.stream()
                .map(Product::getCategory)
                .filter(c -> c != null && !c.trim().isEmpty())
                .distinct()
                .sorted()
                .toList();

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("كل الفئات");
        items.addAll(categories);
        categoryFilterComboBox.setItems(items);
        categoryFilterComboBox.setValue("كل الفئات");

        categoryFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyProductFilters();
            if (!productComboBox.isShowing()) {
                productComboBox.show();
            }
        });
    }

    private void applyProductFilters() {
        String selectedCategory = categoryFilterComboBox != null ? categoryFilterComboBox.getValue() : null;
        boolean allCategories = selectedCategory == null || selectedCategory.equals("كل الفئات");
        String query = productSearchQuery == null ? "" : productSearchQuery.trim();

        filteredProducts.setPredicate(p -> {
            if (!allCategories) {
                String cat = p.getCategory();
                if (cat == null || !cat.equals(selectedCategory)) {
                    return false;
                }
            }

            if (query.isEmpty()) {
                return true;
            }

            String name = p.getName() != null ? p.getName().toLowerCase() : "";
            String code = p.getProductCode() != null ? p.getProductCode().toLowerCase() : "";
            String barcode = p.getBarcode() != null ? p.getBarcode().toLowerCase() : "";
            return name.contains(query) || code.contains(query) || barcode.contains(query);
        });
    }

    private void setupCustomerComboBox() {
        List<Customer> customers = customerRepository.findAll();
        customerComboBox.setItems(FXCollections.observableArrayList(customers));
        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer != null ? customer.getName() + " (" + customer.getCustomerCode() + ")" : "";
            }

            @Override
            public Customer fromString(String s) {
                return null;
            }
        });

        customerComboBox.valueProperty().addListener((obs, oldCustomer, newCustomer) -> {
            updateProjectLocations(newCustomer);
        });

        updateProjectLocations(customerComboBox.getValue());
    }

    private void updateProjectLocations(Customer customer) {
        if (projectLocationComboBox == null) {
            return;
        }

        projectLocationComboBox.getItems().clear();
        projectLocationComboBox.setValue(null);

        if (customer == null) {
            projectLocationComboBox.setDisable(true);
            return;
        }

        String locationsText = customer.getProjectLocation();
        if (locationsText == null || locationsText.trim().isEmpty()) {
            projectLocationComboBox.setDisable(true);
            return;
        }

        List<String> locations = locationsText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        projectLocationComboBox.setItems(FXCollections.observableArrayList(locations));
        projectLocationComboBox.setDisable(locations.isEmpty());

        if (locations.size() == 1) {
            projectLocationComboBox.setValue(locations.get(0));
        }
    }

    private void setupProductComboBox() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> p.getIsActive() && p.getQuantityInStock() > 0)
                .toList();

        filteredProducts = new FilteredList<>(FXCollections.observableArrayList(products), p -> true);
        productComboBox.setItems(filteredProducts);
        productComboBox.setEditable(true);

        setupCategoryFilter(products);

        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                return product != null ? product.getName() + " (" + product.getProductCode() + ")" : "";
            }

            @Override
            public Product fromString(String s) {
                return null;
            }
        });

        if (productComboBox.getEditor() != null) {
            productComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (productComboBox.getValue() != null) {
                    String rendered = productComboBox.getConverter().toString(productComboBox.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }

                productSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                applyProductFilters();

                if (!productComboBox.isShowing()) {
                    productComboBox.show();
                }
            });
        }

        productComboBox.setOnAction(e -> {
            Product selected = productComboBox.getValue();
            if (selected != null) {
                selectedProduct = selected;
                String unit = selected.getUnitOfMeasure();
                if (unit == null || unit.trim().isEmpty()) {
                    unit = "وحدة";
                }
                stockLabel.setText("المخزون المتاح: " + selected.getQuantityInStock() + " " + unit);
                priceLabel.setText("السعر: " + numberFormatter.format(selected.getUnitPrice()) + " دينار");
            }
        });
    }

    private void setupItemsTable() {
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQuantity()).asObject());
        unitPriceColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());
        discountColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getDiscountAmount()).asObject());
        totalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalPrice()).asObject());

        unitPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : numberFormatter.format(item));
            }
        });

        discountColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : numberFormatter.format(item));
            }
        });

        totalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : numberFormatter.format(item));
            }
        });

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑");

            {
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                deleteBtn.setOnAction(e -> {
                    SaleItemRow item = getTableView().getItems().get(getIndex());
                    saleItems.remove(item);
                    updateTotals();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        itemsTable.setItems(saleItems);
    }

    private void setupDefaults() {
        cashRadio.setSelected(true);
        quantityField.setText("1");
        itemDiscountField.setText("0");
        additionalDiscountField.setText("0");
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/CustomerForm.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("إضافة عميل جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            stage.setScene(new Scene(root));

            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();

            if (controller.isSaved()) {
                refreshCustomersAndSelectLast();
            }
        } catch (Exception e) {
            logger.error("Failed to open customer form", e);
            showError("خطأ", "فشل في فتح نافذة إضافة عميل جديد");
        }
    }

    private void refreshCustomersAndSelectLast() {
        List<Customer> customers = customerRepository.findAll();
        customerComboBox.setItems(FXCollections.observableArrayList(customers));

        Customer last = customers.stream()
                .max((a, b) -> Long.compare(a.getId() != null ? a.getId() : 0L, b.getId() != null ? b.getId() : 0L))
                .orElse(null);

        if (last != null) {
            customerComboBox.setValue(last);
            updateProjectLocations(last);
        }
    }

    @FXML
    private void handleAddItem() {
        Product product = selectedProduct != null ? selectedProduct : productComboBox.getValue();
        if (product == null) {
            showError("خطأ", "الرجاء اختيار منتج");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("خطأ", "الرجاء إدخال كمية صحيحة");
            return;
        }

        int availableStock = getAvailableStock(product);
        if (quantity > availableStock) {
            showError("خطأ", "الكمية المطلوبة أكبر من المخزون المتاح (" + availableStock + ")");
            return;
        }

        double discountPercent = 0;
        try {
            discountPercent = Double.parseDouble(itemDiscountField.getText().trim());
            if (discountPercent < 0 || discountPercent > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            discountPercent = 0;
        }

        SaleItemRow existingItem = saleItems.stream()
                .filter(item -> item.getProductId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            int newQty = existingItem.getQuantity() + quantity;
            if (newQty > availableStock) {
                showError("خطأ", "إجمالي الكمية أكبر من المخزون المتاح");
                return;
            }
            existingItem.setQuantity(newQty);
            existingItem.recalculate();
            itemsTable.refresh();
        } else {
            SaleItemRow newItem = new SaleItemRow(
                    product.getId(),
                    product.getName(),
                    quantity,
                    product.getUnitPrice(),
                    discountPercent
            );
            saleItems.add(newItem);
        }

        updateTotals();
        clearProductSelection();
    }

    private int getAvailableStock(Product product) {
        int inTable = saleItems.stream()
                .filter(item -> item.getProductId().equals(product.getId()))
                .mapToInt(SaleItemRow::getQuantity)
                .sum();
        return product.getQuantityInStock() - inTable;
    }

    private void clearProductSelection() {
        selectedProduct = null;
        productComboBox.setValue(null);
        productComboBox.getEditor().clear();
        quantityField.setText("1");
        itemDiscountField.setText("0");
        stockLabel.setText("المخزون المتاح: -");
        priceLabel.setText("السعر: -");
    }

    @FXML
    private void handleDiscountChange() {
        updateTotals();
    }

    private void updateTotals() {
        double subtotal = saleItems.stream().mapToDouble(SaleItemRow::getTotalPrice).sum();
        double itemsDiscount = saleItems.stream().mapToDouble(SaleItemRow::getDiscountAmount).sum();

        double additionalDiscount = 0;
        try {
            additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim());
        } catch (NumberFormatException e) {
            additionalDiscount = 0;
        }

        double totalDiscount = itemsDiscount + additionalDiscount;
        double finalTotal = subtotal - additionalDiscount;

        subtotalLabel.setText(numberFormatter.format(subtotal + itemsDiscount) + " دينار");
        discountLabel.setText(numberFormatter.format(totalDiscount) + " دينار");
        finalTotalLabel.setText(numberFormatter.format(finalTotal) + " دينار");
        
        updateBalance(finalTotal);
    }

    @FXML
    private void handlePaidAmountChange() {
        double subtotal = saleItems.stream().mapToDouble(SaleItemRow::getTotalPrice).sum();
        double additionalDiscount = 0;
        try {
            additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim());
        } catch (NumberFormatException e) {
            additionalDiscount = 0;
        }
        double finalTotal = subtotal - additionalDiscount;
        updateBalance(finalTotal);
    }

    private void updateBalance(double finalTotal) {
        double paidAmount = 0;
        try {
            String paidText = paidAmountField.getText().trim().replace(",", "");
            if (!paidText.isEmpty()) {
                paidAmount = Double.parseDouble(paidText);
            }
        } catch (NumberFormatException e) {
            paidAmount = 0;
        }

        double balance = paidAmount - finalTotal;

        if (balance > 0) {
            balanceLabel.setText(numberFormatter.format(balance) + " دينار");
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            balanceStatusLabel.setText("✅ العميل دفع زيادة - نحن مدينون له بهذا المبلغ");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        } else if (balance < 0) {
            balanceLabel.setText(numberFormatter.format(Math.abs(balance)) + " دينار");
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            balanceStatusLabel.setText("⚠️ العميل مدين لنا بهذا المبلغ");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        } else {
            balanceLabel.setText("0 دينار");
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            balanceStatusLabel.setText("✔️ تم الدفع بالكامل");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        }
    }

    @FXML
    private void handleSaveAndPrint() {
        Sale sale = createSale();
        if (sale != null) {
            try {
                Receipt receipt = receiptService.generateReceipt(sale.getId(), "DEFAULT", "System");
                showSuccess("تم بنجاح", "تم حفظ الفاتورة وإنشاء الإيصال\nرقم الإيصال: " + receipt.getReceiptNumber());
                
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
                
                dialogStage.close();
            } catch (Exception e) {
                logger.error("Failed to generate receipt", e);
                showError("خطأ", "تم حفظ الفاتورة لكن فشل إنشاء الإيصال");
            }
        }
    }

    private Sale createSale() {
        if (customerComboBox.getValue() == null) {
            showError("خطأ", "الرجاء اختيار العميل");
            return null;
        }

        if (projectLocationComboBox == null || projectLocationComboBox.isDisabled() || projectLocationComboBox.getValue() == null || projectLocationComboBox.getValue().trim().isEmpty()) {
            showError("خطأ", "الرجاء اختيار موقع المشروع");
            return null;
        }

        if (saleItems.isEmpty()) {
            showError("خطأ", "الرجاء إضافة منتج واحد على الأقل");
            return null;
        }

        String paymentMethod = "CASH";
        if (creditRadio.isSelected()) paymentMethod = "DEBT";
        else if (cardRadio.isSelected()) paymentMethod = "CARD";

        try {
            SalesService.SaleRequest request = new SalesService.SaleRequest();
            request.setCustomerId(customerComboBox.getValue().getId());
            request.setProjectLocation(projectLocationComboBox.getValue().trim());
            request.setPaymentMethod(paymentMethod);
            request.setNotes(notesArea.getText());
            request.setCreatedBy("System");

            double paidAmount = 0;
            if (!creditRadio.isSelected()) {
                try {
                    String paidText = paidAmountField.getText() != null ? paidAmountField.getText().trim().replace(",", "") : "";
                    if (!paidText.isEmpty()) {
                        paidAmount = Double.parseDouble(paidText);
                    }
                } catch (NumberFormatException e) {
                    paidAmount = 0;
                }
            }
            request.setPaidAmount(paidAmount);

            double additionalDiscount = 0;
            try {
                additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim());
            } catch (NumberFormatException e) {
                additionalDiscount = 0;
            }
            request.setAdditionalDiscount(additionalDiscount);

            List<SalesService.SaleItemRequest> items = new ArrayList<>();
            for (SaleItemRow row : saleItems) {
                SalesService.SaleItemRequest itemRequest = new SalesService.SaleItemRequest();
                itemRequest.setProductId(row.getProductId());
                itemRequest.setQuantity(row.getQuantity());
                itemRequest.setDiscountPercentage(row.getDiscountPercent());
                items.add(itemRequest);
            }
            request.setItems(items);

            return salesService.createSale(request);

        } catch (Exception e) {
            logger.error("Failed to create sale", e);
            showError("خطأ", "فشل في إنشاء الفاتورة: " + e.getMessage());
            return null;
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
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

    @SuppressWarnings("unused")
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class SaleItemRow {
        private Long productId;
        private String productName;
        private int quantity;
        private double unitPrice;
        private double discountPercent;
        private double discountAmount;
        private double totalPrice;

        public SaleItemRow(Long productId, String productName, int quantity, double unitPrice, double discountPercent) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.discountPercent = discountPercent;
            recalculate();
        }

        public void recalculate() {
            double gross = unitPrice * quantity;
            this.discountAmount = gross * (discountPercent / 100.0);
            this.totalPrice = gross - discountAmount;
        }

        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getDiscountPercent() { return discountPercent; }
        public double getDiscountAmount() { return discountAmount; }
        public double getTotalPrice() { return totalPrice; }
    }
}
