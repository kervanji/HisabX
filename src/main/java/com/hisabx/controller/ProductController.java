package com.hisabx.controller;

import com.hisabx.model.Category;
import com.hisabx.model.Product;
import com.hisabx.service.CategoryService;
import com.hisabx.service.InventoryService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ProductController {
    @FXML private TextField productCodeField;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField barcodeField;
    @FXML private TextField costPriceField;
    @FXML private TextField unitPriceField;
    @FXML private TextField profitPercentageField;
    @FXML private RadioButton manualPriceRadio;
    @FXML private RadioButton percentagePriceRadio;
    @FXML private Label profitMarginLabel;
    @FXML private TextField quantityField;
    @FXML private TextField minimumStockField;
    @FXML private TextField maximumStockField;
    @FXML private ComboBox<String> unitOfMeasureComboBox;
    @FXML private CheckBox isActiveCheckBox;
    @FXML private Button deleteButton;
    
    private Stage dialogStage;
    private Product product;
    private boolean isEditMode = false;
    private final InventoryService inventoryService = new InventoryService();
    private final CategoryService categoryService = new CategoryService();
    private final DecimalFormat numberFormat;
    
    public ProductController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        numberFormat = new DecimalFormat("#,##0.00", symbols);
    }
    
    @FXML
    private void initialize() {
        loadCategories();
        loadUnitsOfMeasure();
        setupPriceListeners();
        setupPricingModeToggle();
    }
    
    private void loadCategories() {
        List<String> categories = categoryService.getActiveCategories().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .toList();
        categoryComboBox.setItems(FXCollections.observableArrayList(categories));
    }
    
    private void loadUnitsOfMeasure() {
        List<String> units = Arrays.asList("قطعة", "كيلو", "متر", "لتر", "علبة", "كرتون", "طن", "جرام");
        unitOfMeasureComboBox.setItems(FXCollections.observableArrayList(units));
    }
    
    private void setupPriceListeners() {
        costPriceField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (percentagePriceRadio.isSelected()) {
                calculatePriceFromPercentage();
            }
            updateProfitMargin();
        });
        unitPriceField.textProperty().addListener((obs, oldVal, newVal) -> updateProfitMargin());
        profitPercentageField.textProperty().addListener((obs, oldVal, newVal) -> calculatePriceFromPercentage());
    }
    
    private void setupPricingModeToggle() {
        ToggleGroup pricingGroup = new ToggleGroup();
        manualPriceRadio.setToggleGroup(pricingGroup);
        percentagePriceRadio.setToggleGroup(pricingGroup);
        
        manualPriceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                unitPriceField.setVisible(true);
                unitPriceField.setManaged(true);
                profitPercentageField.setVisible(false);
                profitPercentageField.setManaged(false);
            }
        });
        
        percentagePriceRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                unitPriceField.setVisible(false);
                unitPriceField.setManaged(false);
                profitPercentageField.setVisible(true);
                profitPercentageField.setManaged(true);
                calculatePriceFromPercentage();
            }
        });
    }
    
    private void calculatePriceFromPercentage() {
        try {
            double cost = parseDouble(costPriceField.getText());
            double percentage = parseDouble(profitPercentageField.getText());
            if (cost > 0 && percentage >= 0) {
                double price = cost * (1 + percentage / 100);
                unitPriceField.setText(String.valueOf(price));
            }
        } catch (Exception e) {
            // Ignore parsing errors during input
        }
    }
    
    private void updateProfitMargin() {
        try {
            double cost = parseDouble(costPriceField.getText());
            double price = parseDouble(unitPriceField.getText());
            double profit = price - cost;
            double percentage = cost > 0 ? (profit / cost) * 100 : 0;
            
            String color = profit >= 0 ? "#10b981" : "#ef4444";
            profitMarginLabel.setText(String.format("%s د.ع (%.1f%%)", numberFormat.format(profit), percentage));
            profitMarginLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
        } catch (Exception e) {
            profitMarginLabel.setText("--");
        }
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setProduct(Product product) {
        this.product = product;
        this.isEditMode = true;
        deleteButton.setVisible(true);
        
        productCodeField.setText(product.getProductCode());
        productCodeField.setEditable(false);
        nameField.setText(product.getName());
        descriptionField.setText(product.getDescription());
        categoryComboBox.setValue(product.getCategory());
        barcodeField.setText(product.getBarcode());
        costPriceField.setText(product.getCostPrice() != null ? String.valueOf(product.getCostPrice()) : "");
        unitPriceField.setText(product.getUnitPrice() != null ? String.valueOf(product.getUnitPrice()) : "");
        quantityField.setText(String.valueOf(product.getQuantityInStock()));
        minimumStockField.setText(product.getMinimumStock() != null ? String.valueOf(product.getMinimumStock()) : "");
        maximumStockField.setText(product.getMaximumStock() != null ? String.valueOf(product.getMaximumStock()) : "");
        unitOfMeasureComboBox.setValue(product.getUnitOfMeasure());
        isActiveCheckBox.setSelected(product.getIsActive());
        
        updateProfitMargin();
    }
    
    @FXML
    private void handleAddCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("إضافة فئة جديدة");
        dialog.setHeaderText(null);
        dialog.setContentText("اسم الفئة:");
        
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                String trimmed = name.trim();
                try {
                    categoryService.createCategory(new Category(trimmed));
                } catch (Exception e) {
                    showError("خطأ", e.getMessage());
                    return;
                }
                loadCategories();
                categoryComboBox.setValue(trimmed);
            }
        });
    }
    
    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }
        
        try {
            if (product == null) {
                product = new Product();
            }
            
            String productCode = productCodeField.getText().trim();
            if (productCode.isEmpty()) {
                productCode = generateProductCode();
            }
            product.setProductCode(productCode);
            product.setName(nameField.getText().trim());
            product.setDescription(descriptionField.getText());
            product.setCategory(categoryComboBox.getValue());
            product.setBarcode(barcodeField.getText().trim());
            product.setCostPrice(parseDouble(costPriceField.getText()));
            product.setUnitPrice(parseDouble(unitPriceField.getText()));
            product.setQuantityInStock(parseDouble(quantityField.getText()));
            product.setMinimumStock(parseDouble(minimumStockField.getText()));
            product.setMaximumStock(parseDoubleOrNull(maximumStockField.getText()));
            product.setUnitOfMeasure(unitOfMeasureComboBox.getValue());
            product.setIsActive(isActiveCheckBox.isSelected());
            
            if (isEditMode) {
                inventoryService.updateProduct(product);
                showInfo("تم التحديث", "تم تحديث المنتج بنجاح");
            } else {
                inventoryService.createProduct(product);
                showInfo("تم الإضافة", "تم إضافة المنتج بنجاح");
            }
            
            dialogStage.close();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
    
    @FXML
    private void handleDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText("هل أنت متأكد من حذف هذا المنتج؟");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    inventoryService.deleteProduct(product);
                    showInfo("تم الحذف", "تم حذف المنتج بنجاح");
                    dialogStage.close();
                } catch (Exception e) {
                    showError("خطأ", "فشل في حذف المنتج: " + e.getMessage());
                }
            }
        });
    }
    
    private boolean validateInput() {
        if (nameField.getText().trim().isEmpty()) {
            showError("خطأ", "اسم المنتج مطلوب");
            nameField.requestFocus();
            return false;
        }
        
        if (unitPriceField.getText().trim().isEmpty()) {
            showError("خطأ", "سعر البيع مطلوب");
            unitPriceField.requestFocus();
            return false;
        }
        
        try {
            Double.parseDouble(unitPriceField.getText().trim());
        } catch (NumberFormatException e) {
            showError("خطأ", "سعر البيع يجب أن يكون رقماً");
            unitPriceField.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        // Remove commas for parsing
        return Double.parseDouble(value.trim().replace(",", ""));
    }
    
    private Double parseDoubleOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return Double.parseDouble(value.trim().replace(",", ""));
    }
    
    private String generateProductCode() {
        long timestamp = System.currentTimeMillis();
        return "PRD" + timestamp;
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
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
