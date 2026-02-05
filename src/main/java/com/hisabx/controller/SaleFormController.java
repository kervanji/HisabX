package com.hisabx.controller;

import com.hisabx.model.*;
import com.hisabx.service.*;
import com.hisabx.database.Repository.*;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import javafx.application.Platform;
import com.hisabx.util.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaleFormController {
    private static final Logger logger = LoggerFactory.getLogger(SaleFormController.class);

    @FXML private VBox root;
    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<String> projectLocationComboBox;
    @FXML private TextField newProjectLocationField;
    @FXML private Button addProjectLocationBtn;
    private FilteredList<Customer> filteredCustomers;
    private String customerSearchQuery = "";
    @FXML private ComboBox<String> categoryFilterComboBox;
    @FXML private ComboBox<Product> productComboBox;
    @FXML private TextField quantityField;
    @FXML private Label stockLabel;
    @FXML private Label priceLabel;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> productNameColumn;
    @FXML private TableColumn<SaleItemRow, Double> quantityColumn;
    @FXML private TableColumn<SaleItemRow, Double> unitPriceColumn;
    @FXML private TableColumn<SaleItemRow, Double> discountColumn;
    @FXML private TableColumn<SaleItemRow, Double> totalColumn;
    @FXML private TableColumn<SaleItemRow, Void> editColumn;
    @FXML private TableColumn<SaleItemRow, Void> actionColumn;
    @FXML private RadioButton cashRadio;
    @FXML private RadioButton creditRadio;
    @FXML private ToggleGroup paymentGroup;
    @FXML private ComboBox<String> currencyComboBox;
    @FXML private VBox exchangeRateBox;
    @FXML private TextField exchangeRateField;
    @FXML private TextField additionalDiscountField;
    @FXML private Label additionalDiscountCurrencyLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label discountLabel;
    @FXML private Label finalTotalLabel;
    @FXML private TextField paidAmountField;
    @FXML private Label paidAmountCurrencyLabel;
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
    private boolean tabMode = false;

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
        setupCurrencyComboBox();
        setupCustomerComboBox();
        setupProductComboBox();
        setupItemsTable();
        setupDefaults();

        Platform.runLater(() -> {
            if (root != null && root.getScene() != null && root.getScene().getWindow() instanceof Stage s) {
                dialogStage = s;
            }
        });
    }

    private void setupCurrencyComboBox() {
        if (currencyComboBox == null) {
            return;
        }

        currencyComboBox.setItems(FXCollections.observableArrayList("دينار", "دولار"));
        currencyComboBox.setValue("دينار");
        if (exchangeRateField != null && (exchangeRateField.getText() == null || exchangeRateField.getText().trim().isEmpty())) {
            exchangeRateField.setText("1500");
        }
        updateExchangeRateVisibility("دينار");
        updateCurrencyLabels("دينار");
        currencyComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateExchangeRateVisibility(newVal);
            updateCurrencyLabels(newVal);
            refreshConversionOnRows();
            updateTotals();
        });
    }

    @FXML
    private void handleExchangeRateChange() {
        refreshConversionOnRows();
        updateTotals();
        updateSelectedProductPriceLabel();
    }

    private void updateExchangeRateVisibility(String currency) {
        boolean isUsd = "دولار".equals(currency);
        if (exchangeRateBox != null) {
            exchangeRateBox.setVisible(isUsd);
            exchangeRateBox.setManaged(isUsd);
        }
    }

    private void refreshConversionOnRows() {
        String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";
        double rate = getExchangeRateOrDefault();
        for (SaleItemRow row : saleItems) {
            row.setCurrencyAndRate(currency, rate);
            row.recalculate();
        }
        if (itemsTable != null) {
            itemsTable.refresh();
        }
    }

    private double getExchangeRateOrDefault() {
        double rate = 1500.0;
        if (exchangeRateField == null || exchangeRateField.getText() == null) {
            return rate;
        }

        try {
            String text = exchangeRateField.getText().trim().replace(",", "");
            if (!text.isEmpty()) {
                rate = Double.parseDouble(text);
            }
        } catch (NumberFormatException ignored) {
        }

        if (rate <= 0) {
            rate = 1500.0;
        }
        return rate;
    }

    private void updateCurrencyLabels(String currency) {
        String label = currency != null ? currency : "دينار";
        if (additionalDiscountCurrencyLabel != null) {
            additionalDiscountCurrencyLabel.setText(label);
        }
        if (paidAmountCurrencyLabel != null) {
            paidAmountCurrencyLabel.setText(label);
        }
    }

    private void setupPaymentToggleGroup() {
        if (paymentGroup == null) {
            paymentGroup = new ToggleGroup();
        }

        cashRadio.setToggleGroup(paymentGroup);
        creditRadio.setToggleGroup(paymentGroup);

        // Force single selection (and prevent clearing selection)
        if (paymentGroup.getSelectedToggle() == null) {
            creditRadio.setSelected(true); // default to دين (دين)
        }

        if (paidAmountField != null) {
            // For cash: disable field and auto-fill with total
            // For debt: enable field for partial payment
            paidAmountField.setDisable(cashRadio.isSelected());
        }

        paymentGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }

            if (paidAmountField != null) {
                boolean isCash = cashRadio.isSelected();
                paidAmountField.setDisable(isCash);
                
                if (isCash) {
                    // Auto-fill with full amount for cash
                    double finalTotal = calculateFinalTotal();
                    paidAmountField.setText(String.valueOf(finalTotal));
                } else {
                    // Enable partial payment for debt
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
        filteredCustomers = new FilteredList<>(FXCollections.observableArrayList(customers), c -> true);
        customerComboBox.setItems(filteredCustomers);
        customerComboBox.setEditable(true);
        
        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer != null ? customer.getName() + " (" + customer.getCustomerCode() + ")" : "";
            }

            @Override
            public Customer fromString(String s) {
                if (s == null || s.isEmpty()) return null;
                return filteredCustomers.getSource().stream()
                        .filter(c -> (c.getName() + " (" + c.getCustomerCode() + ")").equals(s))
                        .findFirst()
                        .orElse(null);
            }
        });

        // Enable search in customer ComboBox
        if (customerComboBox.getEditor() != null) {
            customerComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (customerComboBox.getValue() != null) {
                    String rendered = customerComboBox.getConverter().toString(customerComboBox.getValue());
                    if (rendered.equals(newText)) {
                        return;
                    }
                }

                // Handle case where selection updates text but valueProperty hasn't fired yet
                if (newText != null) {
                    boolean isSelection = filteredCustomers.getSource().stream()
                            .anyMatch(c -> {
                                String s = customerComboBox.getConverter().toString(c);
                                return s != null && s.equals(newText);
                            });
                    if (isSelection) {
                        return;
                    }
                }

                customerSearchQuery = newText == null ? "" : newText.trim().toLowerCase();
                filteredCustomers.setPredicate(c -> {
                    if (customerSearchQuery.isEmpty()) return true;
                    
                    String fullString = (c.getName() + " (" + c.getCustomerCode() + ")").toLowerCase();
                    String name = c.getName() != null ? c.getName().toLowerCase() : "";
                    String code = c.getCustomerCode() != null ? c.getCustomerCode().toLowerCase() : "";
                    String phone = c.getPhoneNumber() != null ? c.getPhoneNumber().toLowerCase() : "";
                    
                    return fullString.contains(customerSearchQuery) || 
                           name.contains(customerSearchQuery) || 
                           code.contains(customerSearchQuery) || 
                           phone.contains(customerSearchQuery);
                });

                if (!customerComboBox.isShowing()) {
                    customerComboBox.show();
                }
            });
        }

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

        // Enable ComboBox when customer is selected
        projectLocationComboBox.setDisable(false);

        String locationsText = customer.getProjectLocation();
        if (locationsText == null || locationsText.trim().isEmpty()) {
            // No locations yet, but keep enabled so user can add new ones
            return;
        }

        List<String> locations = locationsText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        projectLocationComboBox.setItems(FXCollections.observableArrayList(locations));

        if (locations.size() == 1) {
            projectLocationComboBox.setValue(locations.get(0));
        }
    }

    private File chooseSaveLocation(File sourceFile) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ الفاتورة PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName(sourceFile.getName());

        File targetFile = dialogStage != null ? fileChooser.showSaveDialog(dialogStage) : fileChooser.showSaveDialog(null);
        if (targetFile == null) {
            return sourceFile;
        }

        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } catch (Exception e) {
            logger.error("Failed to save receipt PDF to selected location", e);
            showError("خطأ", "فشل في حفظ الفاتورة في المسار المحدد");
            return sourceFile;
        }
    }

    private void setupProductComboBox() {
        // Show all active products including out of stock ones
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .toList();

        filteredProducts = new FilteredList<>(FXCollections.observableArrayList(products), p -> true);
        productComboBox.setItems(filteredProducts);
        productComboBox.setEditable(true);

        setupCategoryFilter(products);

        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                return product != null ? product.getName() : "";
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
                double stock = selected.getQuantityInStock();
                if (stock <= 0) {
                    stockLabel.setText("المخزون المتاح: 0 " + unit + " (نفذ المخزون)");
                    stockLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    stockLabel.setText("المخزون المتاح: " + stock + " " + unit);
                    stockLabel.setStyle("-fx-text-fill: #7f8c8d;");
                }
                updateSelectedProductPriceLabel();
            }
        });
    }

    private void updateSelectedProductPriceLabel() {
        if (priceLabel == null) {
            return;
        }

        if (selectedProduct == null) {
            priceLabel.setText("السعر: -");
            return;
        }

        String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";
        double baseIqd = selectedProduct.getUnitPrice();
        if ("دولار".equals(currency)) {
            double rate = getExchangeRateOrDefault();
            double usd = baseIqd / rate;
            priceLabel.setText("السعر: " + numberFormatter.format(usd) + " دولار");
        } else {
            priceLabel.setText("السعر: " + numberFormatter.format(baseIqd) + " دينار");
        }
    }

    private void setupItemsTable() {
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getQuantity()).asObject());
        unitPriceColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());
        discountColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getDiscountAmount()).asObject());
        totalColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getTotalPrice()).asObject());

        // Enable double-click editing for quantity column
        quantityColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(String.valueOf(item));
                    setGraphic(null);
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> commitEdit(Double.parseDouble(textField.getText())));
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText()));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(String.valueOf(getItem()));
                setGraphic(null);
            }

            @Override
            public void commitEdit(Double newValue) {
                super.commitEdit(newValue);
                SaleItemRow row = getTableView().getItems().get(getIndex());
                // Stock validation removed to allow negative inventory/out of stock sales
                if (newValue <= 0) {
                    showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
                    cancelEdit();
                    return;
                }
                row.setQuantity(newValue);
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable double-click editing for unit price column
        unitPriceColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(numberFormatter.format(item));
                    setGraphic(null);
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(numberFormatter.format(getItem()));
                setGraphic(null);
            }

            @Override
            public void commitEdit(Double newValue) {
                super.commitEdit(newValue);
                if (newValue < 0) {
                    showError("خطأ", "السعر لا يمكن أن يكون سالب");
                    cancelEdit();
                    return;
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());
                row.setUnitPrice(newValue);
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable editing for Discount Column
        discountColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : numberFormatter.format(item));
                setGraphic(null);
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());
                textField.setText(String.valueOf(row.getDiscountAmount()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
                setText(numberFormatter.format(getItem()));
            }

            @Override
            public void commitEdit(Double newDiscountAmount) {
                super.commitEdit(newDiscountAmount);
                // Removed negative check to allow negative discount (increasing price)
                SaleItemRow row = getTableView().getItems().get(getIndex());
                double gross = row.getUnitPrice() * row.getQuantity();
                if (gross == 0) return;
                
                double newPercent = (newDiscountAmount / gross) * 100.0;
                row.setDiscountPercent(newPercent);
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Enable editing for Total Column
        totalColumn.setCellFactory(col -> new TableCell<>() {
            private TextField textField;

            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : numberFormatter.format(item));
                setGraphic(null);
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (textField == null) {
                    textField = new TextField();
                    textField.setOnAction(e -> {
                        try {
                            commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                        } catch (NumberFormatException ex) {
                            cancelEdit();
                        }
                    });
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            try {
                                commitEdit(Double.parseDouble(textField.getText().replace(",", "")));
                            } catch (NumberFormatException ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
                textField.setText(String.valueOf(getItem()));
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setGraphic(null);
                setText(numberFormatter.format(getItem()));
            }

            @Override
            public void commitEdit(Double newTotal) {
                super.commitEdit(newTotal);
                if (newTotal < 0) {
                    showError("خطأ", "الإجمالي لا يمكن أن يكون سالب");
                    cancelEdit();
                    return;
                }
                SaleItemRow row = getTableView().getItems().get(getIndex());
                
                // Update Unit Price based on new Total
                // Total = (UnitPrice * Quantity) - DiscountAmount
                // We want to keep DiscountAmount same (or 0?) and update UnitPrice.
                // Or simply: UnitPrice = (Total + DiscountAmount) / Quantity
                
                double currentDiscount = row.getDiscountAmount();
                double newGross = newTotal + currentDiscount;
                
                if (row.getQuantity() != 0) {
                    double newUnitPrice = newGross / row.getQuantity();
                    row.setUnitPrice(newUnitPrice);
                    // Discount percent might change relative to new unit price, let's recalculate it or keep amount fixed?
                    // row.recalculate() calculates discount amount from percent.
                    // If we want to keep discount AMOUNT fixed:
                    // newDiscountPercent = (currentDiscount / newGross) * 100
                    if (newGross != 0) {
                         row.setDiscountPercent((currentDiscount / newGross) * 100.0);
                    } else {
                         row.setDiscountPercent(0);
                    }
                }
                
                row.recalculate();
                itemsTable.refresh();
                updateTotals();
            }
        });

        // Edit button column
        editColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏");

            {
                editBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                editBtn.setOnAction(e -> {
                    SaleItemRow row = getTableView().getItems().get(getIndex());
                    openProductEditForm(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editBtn);
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
        itemsTable.setEditable(true);
    }

    private void openProductEditForm(SaleItemRow row) {
        try {
            Product product = productRepository.findById(row.getProductId()).orElse(null);
            if (product == null) {
                showError("خطأ", "لم يتم العثور على المنتج");
                return;
            }

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/ProductForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("تعديل منتج");
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            stage.setScene(new Scene(root));

            ProductController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setProduct(product);

            stage.showAndWait();

            // Refresh table to reflect any changes
            itemsTable.refresh();
            updateTotals();

            // Refresh selected product info if needed
            if (selectedProduct != null && selectedProduct.getId().equals(product.getId())) {
                 Product updated = productRepository.findById(product.getId()).orElse(null);
                 if (updated != null) {
                     selectedProduct = updated;
                     // Update labels
                     String unit = updated.getUnitOfMeasure();
                     if (unit == null || unit.trim().isEmpty()) unit = "وحدة";
                     double stock = updated.getQuantityInStock();
                    if (stock <= 0) {
                        stockLabel.setText("المخزون المتاح: " + stock + " " + unit + " (نفذ المخزون)");
                        stockLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    } else {
                        stockLabel.setText("المخزون المتاح: " + stock + " " + unit);
                        stockLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    }
                     priceLabel.setText("السعر: " + numberFormatter.format(updated.getUnitPrice()) + " دينار");
                 }
            }
        } catch (Exception e) {
            logger.error("Failed to open product form", e);
            showError("خطأ", "فشل في فتح نافذة تعديل المنتج");
        }
    }

    @FXML
    private void handleAddProjectLocation() {
        if (newProjectLocationField == null) return;
        
        String newLocation = newProjectLocationField.getText().trim();
        if (newLocation.isEmpty()) {
            showError("خطأ", "الرجاء إدخال اسم موقع المشروع");
            return;
        }

        Customer customer = customerComboBox.getValue();
        if (customer == null) {
            showError("خطأ", "الرجاء اختيار العميل أولاً");
            return;
        }

        // Add new location to customer's project locations
        String existingLocations = customer.getProjectLocation();
        String updatedLocations;
        if (existingLocations == null || existingLocations.trim().isEmpty()) {
            updatedLocations = newLocation;
        } else {
            updatedLocations = existingLocations + "\n" + newLocation;
        }
        customer.setProjectLocation(updatedLocations);
        
        // Save customer with new location
        try {
            customerRepository.save(customer);
            updateProjectLocations(customer);
            projectLocationComboBox.setValue(newLocation);
            newProjectLocationField.clear();
            showSuccess("تم", "تمت إضافة موقع المشروع بنجاح");
        } catch (Exception e) {
            logger.error("Failed to save project location", e);
            showError("خطأ", "فشل في حفظ موقع المشروع");
        }
    }

    private void setupDefaults() {
        creditRadio.setSelected(true); // default to دين (دين)
        quantityField.setText("1");
        additionalDiscountField.setText("0");
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }

    public void setSelectedCustomer(Customer customer) {
        if (customer != null) {
            customerComboBox.setValue(customer);
            updateProjectLocations(customer);
        }
    }

    @FXML
    private void handleNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/views/CustomerForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("إضافة عميل جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            stage.setScene(new Scene(root));
            stage.setMaximized(false);

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

        double quantity;
        try {
            quantity = Double.parseDouble(quantityField.getText().trim());
            if (quantity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("خطأ", "الرجاء إدخال كمية صحيحة");
            return;
        }

        // Stock validation checks removed to allow adding out-of-stock products

        double discountPercent = 0;

        SaleItemRow existingItem = saleItems.stream()
                .filter(item -> item.getProductId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            double newQty = existingItem.getQuantity() + quantity;
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
            String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";
            newItem.setCurrencyAndRate(currency, getExchangeRateOrDefault());
            saleItems.add(newItem);
        }

        updateTotals();
        clearProductSelection();
    }

    private void clearProductSelection() {
        selectedProduct = null;
        productComboBox.setValue(null);
        productComboBox.getEditor().clear();
        quantityField.setText("1");
        stockLabel.setText("المخزون المتاح: -");
        priceLabel.setText("السعر: -");
    }

    @FXML
    private void handleDiscountChange() {
        updateTotals();
    }

    private double calculateFinalTotal() {
        double subtotal = saleItems.stream().mapToDouble(SaleItemRow::getTotalPrice).sum();
        double additionalDiscount = 0;
        try {
            additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            additionalDiscount = 0;
        }
        return subtotal - additionalDiscount;
    }

    private void updateTotals() {
        double subtotal = saleItems.stream().mapToDouble(SaleItemRow::getTotalPrice).sum();
        double itemsDiscount = saleItems.stream().mapToDouble(SaleItemRow::getDiscountAmount).sum();

        double additionalDiscount = 0;
        try {
            additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            additionalDiscount = 0;
        }

        double totalDiscount = itemsDiscount + additionalDiscount;
        double finalTotal = subtotal - additionalDiscount;

        String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";
        subtotalLabel.setText(numberFormatter.format(subtotal + itemsDiscount) + " " + currency);
        discountLabel.setText(numberFormatter.format(totalDiscount) + " " + currency);
        finalTotalLabel.setText(numberFormatter.format(finalTotal) + " " + currency);
        
        // Auto-update paid amount for cash payment
        if (cashRadio.isSelected() && paidAmountField != null) {
            paidAmountField.setText(String.valueOf(finalTotal));
        }
        
        updateBalance(finalTotal);
    }

    @FXML
    private void handlePaidAmountChange() {
        double finalTotal = calculateFinalTotal();
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
        String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";

        if (balance > 0) {
            balanceLabel.setText(numberFormatter.format(balance) + " " + currency);
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            balanceStatusLabel.setText("✅ العميل دفع زيادة - نحن مدينون له بهذا المبلغ");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
        } else if (balance < 0) {
            balanceLabel.setText(numberFormatter.format(Math.abs(balance)) + " " + currency);
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            balanceStatusLabel.setText("⚠️ العميل مدين لنا بهذا المبلغ");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        } else {
            balanceLabel.setText("0 " + currency);
            balanceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            balanceStatusLabel.setText("✔️ تم الدفع بالكامل");
            balanceStatusLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        }
    }

    @FXML
    private void handleSaveAndPrint(javafx.event.ActionEvent e) {
        Sale sale = createSale();
        if (sale != null) {
            try {
                Receipt receipt = receiptService.generateReceipt(sale.getId(), "DEFAULT", "System");
                showSuccess("تم بنجاح", "تم حفظ الفاتورة وإنشاء الإيصال\nرقم الإيصال: " + receipt.getReceiptNumber());
                
                if (receipt.getFilePath() != null) {
                    File pdfFile = new File(receipt.getFilePath());
                    if (pdfFile.exists()) {
                        File savedFile = chooseSaveLocation(pdfFile);
                        File previewFile = savedFile != null ? savedFile : pdfFile;
                        if (mainApp != null) {
                            mainApp.showPdfPreview(previewFile);
                        } else if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(previewFile);
                        }
                    }
                }
                resetSaleForm();
            } catch (Exception ex) {
                logger.error("Failed to generate receipt", ex);
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

        String currency = currencyComboBox != null ? currencyComboBox.getValue() : "دينار";

        try {
            SalesService.SaleRequest request = new SalesService.SaleRequest();
            request.setCustomerId(customerComboBox.getValue().getId());
            request.setProjectLocation(projectLocationComboBox.getValue().trim());
            request.setPaymentMethod(paymentMethod);
            request.setCurrency(currency);
            request.setNotes(notesArea.getText());
            String createdBy = SessionManager.getInstance().getCurrentDisplayName();
            request.setCreatedBy(createdBy != null ? createdBy : "System");

            double paidAmount = 0;
            try {
                String paidText = paidAmountField.getText() != null ? paidAmountField.getText().trim().replace(",", "") : "";
                if (!paidText.isEmpty()) {
                    paidAmount = Double.parseDouble(paidText);
                }
            } catch (NumberFormatException e) {
                paidAmount = 0;
            }
            
            // For cash payment, ensure full amount is paid
            if (cashRadio.isSelected()) {
                double finalTotal = calculateFinalTotal();
                paidAmount = finalTotal;
            }
            
            request.setPaidAmount(paidAmount);

            double additionalDiscount = 0;
            try {
                additionalDiscount = Double.parseDouble(additionalDiscountField.getText().trim().replace(",", ""));
            } catch (NumberFormatException e) {
                additionalDiscount = 0;
            }
            request.setAdditionalDiscount(additionalDiscount);

            List<SalesService.SaleItemRequest> items = new ArrayList<>();
            for (SaleItemRow row : saleItems) {
                SalesService.SaleItemRequest itemRequest = new SalesService.SaleItemRequest();
                itemRequest.setProductId(row.getProductId());
                itemRequest.setQuantity(row.getQuantity());
                itemRequest.setUnitPrice(row.getUnitPrice());
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
        if (tabMode) {
            com.hisabx.util.TabManager.getInstance().closeTab("new-sale");
        } else if (dialogStage != null) {
            dialogStage.close();
        } else {
            resetSaleForm();
        }
    }

    private void resetSaleForm() {
        customerComboBox.setValue(null);
        projectLocationComboBox.getSelectionModel().clearSelection();
        newProjectLocationField.clear();
        categoryFilterComboBox.getSelectionModel().clearSelection();
        productComboBox.getSelectionModel().clearSelection();
        quantityField.clear();
        stockLabel.setText("");
        priceLabel.setText("");
        saleItems.clear();
        itemsTable.refresh();
        additionalDiscountField.setText("0");
        additionalDiscountCurrencyLabel.setText("");
        subtotalLabel.setText("0");
        discountLabel.setText("0");
        finalTotalLabel.setText("0");
        paidAmountField.clear();
        paidAmountCurrencyLabel.setText("");
        balanceLabel.setText("0");
        balanceStatusLabel.setText("");
        notesArea.clear();
        if (paymentGroup != null) {
            paymentGroup.selectToggle(creditRadio); // reset to دين
        }
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
        private double quantity;
        private double baseUnitPriceIqd;
        private double discountPercent;
        private double discountAmount;
        private double totalPrice;

        private String currency = "دينار";
        private double exchangeRate = 1500.0;

        public SaleItemRow(Long productId, String productName, double quantity, double unitPriceIqd, double discountPercent) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.baseUnitPriceIqd = unitPriceIqd;
            this.discountPercent = discountPercent;
            recalculate();
        }

        public void setCurrencyAndRate(String currency, double exchangeRate) {
            this.currency = currency != null ? currency : "دينار";
            if (exchangeRate > 0) {
                this.exchangeRate = exchangeRate;
            }
        }

        public void recalculate() {
            double unitPrice = getUnitPrice();
            double gross = unitPrice * quantity;
            this.discountAmount = gross * (discountPercent / 100.0);
            this.totalPrice = gross - discountAmount;
        }

        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public double getQuantity() { return quantity; }
        public void setQuantity(double quantity) { this.quantity = quantity; }

        public double getUnitPrice() {
            if ("دولار".equals(currency)) {
                return baseUnitPriceIqd / exchangeRate;
            }
            return baseUnitPriceIqd;
        }

        public void setUnitPrice(double unitPrice) {
            if (unitPrice < 0) {
                return;
            }
            if ("دولار".equals(currency)) {
                this.baseUnitPriceIqd = unitPrice * exchangeRate;
            } else {
                this.baseUnitPriceIqd = unitPrice;
            }
        }

        public double getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }
        public double getDiscountAmount() { return discountAmount; }
        public double getTotalPrice() { return totalPrice; }
    }
}
