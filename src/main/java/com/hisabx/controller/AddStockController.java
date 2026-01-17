package com.hisabx.controller;

import com.hisabx.model.Product;
import com.hisabx.service.InventoryService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;

public class AddStockController {
    @FXML private ComboBox<Product> productComboBox;
    @FXML private Label currentStockLabel;
    @FXML private Label minimumStockLabel;
    @FXML private TextField quantityField;
    @FXML private Label newStockLabel;
    
    private Stage dialogStage;
    private final InventoryService inventoryService = new InventoryService();
    private boolean tabMode = false;
    
    @FXML
    private void initialize() {
        loadProducts();
        setupProductComboBox();
        setupQuantityListener();
    }
    
    private void loadProducts() {
        List<Product> products = inventoryService.getActiveProducts();
        productComboBox.setItems(FXCollections.observableArrayList(products));
    }
    
    private void setupProductComboBox() {
        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) return null;
                return product.getProductCode() + " - " + product.getName();
            }
            
            @Override
            public Product fromString(String string) {
                return null;
            }
        });
        
        productComboBox.setOnAction(e -> updateProductInfo());
    }
    
    private void setupQuantityListener() {
        quantityField.textProperty().addListener((obs, oldVal, newVal) -> updateNewStock());
    }
    
    private void updateProductInfo() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            currentStockLabel.setText(String.valueOf(selected.getQuantityInStock()));
            minimumStockLabel.setText(String.valueOf(selected.getMinimumStock()));
            updateNewStock();
        } else {
            currentStockLabel.setText("0");
            minimumStockLabel.setText("0");
            newStockLabel.setText("0");
        }
    }
    
    private void updateNewStock() {
        Product selected = productComboBox.getValue();
        if (selected != null) {
            try {
                double quantity = Double.parseDouble(quantityField.getText().trim());
                double newStock = selected.getQuantityInStock() + quantity;
                newStockLabel.setText(String.valueOf(newStock));
                
                if (newStock > selected.getMinimumStock()) {
                    newStockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");
                } else {
                    newStockLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #d97706;");
                }
            } catch (NumberFormatException e) {
                newStockLabel.setText(String.valueOf(selected.getQuantityInStock()));
            }
        }
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }
    
    public void setProduct(Product product) {
        productComboBox.setValue(product);
        productComboBox.setDisable(true);
        updateProductInfo();
    }
    
    @FXML
    private void handleAdd() {
        Product selected = productComboBox.getValue();
        
        if (selected == null) {
            showError("خطأ", "يرجى اختيار المنتج");
            return;
        }
        
        String quantityText = quantityField.getText().trim();
        if (quantityText.isEmpty()) {
            showError("خطأ", "يرجى إدخال الكمية");
            quantityField.requestFocus();
            return;
        }
        
        try {
            double quantity = Double.parseDouble(quantityText);
            if (quantity <= 0) {
                showError("خطأ", "الكمية يجب أن تكون أكبر من صفر");
                return;
            }
            
            inventoryService.addStock(selected.getId(), quantity);
            showInfo("تم", "تمت إضافة " + quantity + " وحدة إلى مخزون " + selected.getName());
            closeForm();
            
        } catch (NumberFormatException e) {
            showError("خطأ", "الكمية يجب أن تكون رقماً");
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        closeForm();
    }
    
    private void closeForm() {
        if (tabMode) {
            com.hisabx.util.TabManager.getInstance().closeTab("add-stock");
        } else if (dialogStage != null) {
            dialogStage.close();
        }
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
