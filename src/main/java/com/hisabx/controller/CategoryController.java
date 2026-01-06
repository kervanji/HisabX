package com.hisabx.controller;

import com.hisabx.model.Category;
import com.hisabx.service.CategoryService;
import com.hisabx.service.InventoryService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.List;

public class CategoryController {
    @FXML private TextField categoryNameField;
    @FXML private TextField categoryDescField;
    @FXML private Button addButton;
    @FXML private TableView<Category> categoriesTable;
    @FXML private TableColumn<Category, Long> idColumn;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> descriptionColumn;
    @FXML private TableColumn<Category, Integer> productCountColumn;
    @FXML private TableColumn<Category, String> statusColumn;
    @FXML private TableColumn<Category, Void> actionsColumn;
    @FXML private Label totalCategoriesLabel;
    
    private Stage dialogStage;
    private final CategoryService categoryService = new CategoryService();
    private final InventoryService inventoryService = new InventoryService();
    private Category editingCategory = null;
    
    @FXML
    private void initialize() {
        setupTableColumns();
        loadCategories();
    }
    
    private void setupTableColumns() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        
        productCountColumn.setCellValueFactory(cellData -> {
            Category cat = cellData.getValue();
            long count = inventoryService.getProductsByCategory(cat.getName()).size();
            return new SimpleIntegerProperty((int) count).asObject();
        });
        
        statusColumn.setCellValueFactory(cellData -> {
            Category cat = cellData.getValue();
            return new SimpleStringProperty(cat.getIsActive() ? "نشطة" : "غير نشطة");
        });
        
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("نشطة")) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #6b7280;");
                    }
                }
            }
        });
        
        setupActionsColumn();
    }
    
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("تعديل");
            private final Button deleteBtn = new Button("حذف");
            
            {
                editBtn.setStyle("-fx-background-color: #2a8cff; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4;");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4;");
                
                editBtn.setOnAction(e -> handleEditCategory(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteCategory(getTableView().getItems().get(getIndex())));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4, editBtn, deleteBtn);
                    setGraphic(box);
                }
            }
        });
    }
    
    private void loadCategories() {
        List<Category> categories = categoryService.getAllCategories();
        ObservableList<Category> categoriesList = FXCollections.observableArrayList(categories);
        categoriesTable.setItems(categoriesList);
        totalCategoriesLabel.setText("إجمالي الفئات: " + categories.size());
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    @FXML
    private void handleAddCategory() {
        String name = categoryNameField.getText().trim();
        String desc = categoryDescField.getText().trim();
        
        if (name.isEmpty()) {
            showError("خطأ", "اسم الفئة مطلوب");
            categoryNameField.requestFocus();
            return;
        }
        
        try {
            if (editingCategory != null) {
                editingCategory.setName(name);
                editingCategory.setDescription(desc);
                categoryService.updateCategory(editingCategory);
                showInfo("تم", "تم تحديث الفئة بنجاح");
                editingCategory = null;
                addButton.setText("إضافة");
            } else {
                Category category = new Category(name, desc);
                categoryService.createCategory(category);
                showInfo("تم", "تم إضافة الفئة بنجاح");
            }
            
            categoryNameField.clear();
            categoryDescField.clear();
            loadCategories();
        } catch (Exception e) {
            showError("خطأ", e.getMessage());
        }
    }
    
    private void handleEditCategory(Category category) {
        editingCategory = category;
        categoryNameField.setText(category.getName());
        categoryDescField.setText(category.getDescription());
        addButton.setText("تحديث");
        categoryNameField.requestFocus();
    }
    
    private void handleDeleteCategory(Category category) {
        long productCount = inventoryService.getProductsByCategory(category.getName()).size();
        
        String message = productCount > 0 
            ? "هذه الفئة تحتوي على " + productCount + " منتج. هل أنت متأكد من الحذف؟"
            : "هل أنت متأكد من حذف هذه الفئة؟";
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    categoryService.deleteCategory(category);
                    showInfo("تم", "تم حذف الفئة بنجاح");
                    loadCategories();
                } catch (Exception e) {
                    showError("خطأ", "فشل في حذف الفئة: " + e.getMessage());
                }
            }
        });
    }
    
    @FXML
    private void handleClose() {
        dialogStage.close();
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
