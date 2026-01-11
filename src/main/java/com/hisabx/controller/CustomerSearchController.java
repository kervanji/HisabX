package com.hisabx.controller;

import com.hisabx.MainApp;
import com.hisabx.model.Customer;
import com.hisabx.service.CustomerService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerSearchController {
    private static final Logger logger = LoggerFactory.getLogger(CustomerSearchController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    
    @FXML private TextField searchField;
    @FXML private TableView<Customer> resultsTable;
    @FXML private TableColumn<Customer, String> codeColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, String> addressColumn;
    @FXML private TableColumn<Customer, String> balanceColumn;
    @FXML private Label statusLabel;
    
    private final CustomerService customerService = new CustomerService();
    private Stage dialogStage;
    private MainApp mainApp;
    
    @FXML
    private void initialize() {
        setupTableColumns();
        
        // Enable search on Enter key
        searchField.setOnAction(e -> handleSearch());
        
        // Double-click to view details
        resultsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleViewDetails();
            }
        });
    }
    
    private void setupTableColumns() {
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("customerCode"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        
        balanceColumn.setCellValueFactory(cellData -> {
            Customer customer = cellData.getValue();
            Double balance = customer.getCurrentBalance();
            return new SimpleStringProperty(currencyFormat.format(balance != null ? balance : 0) + " د.ع");
        });
        
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    Customer customer = getTableView().getItems().get(getIndex());
                    Double balance = customer.getCurrentBalance();
                    if (balance != null && balance < 0) {
                        setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    } else if (balance != null && balance > 0) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #6b7280;");
                    }
                }
            }
        });
    }
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }
    
    @FXML
    private void handleSearch() {
        String searchText = searchField.getText().trim().toLowerCase();
        
        if (searchText.isEmpty()) {
            statusLabel.setText("الرجاء إدخال نص للبحث");
            return;
        }
        
        try {
            List<Customer> allCustomers = customerService.getAllCustomers();
            List<Customer> filtered = allCustomers.stream()
                    .filter(c -> 
                        (c.getName() != null && c.getName().toLowerCase().contains(searchText)) ||
                        (c.getCustomerCode() != null && c.getCustomerCode().toLowerCase().contains(searchText)) ||
                        (c.getPhoneNumber() != null && c.getPhoneNumber().contains(searchText)) ||
                        (c.getAddress() != null && c.getAddress().toLowerCase().contains(searchText))
                    )
                    .collect(Collectors.toList());
            
            resultsTable.setItems(FXCollections.observableArrayList(filtered));
            statusLabel.setText("تم العثور على " + filtered.size() + " عميل");
            
        } catch (Exception e) {
            logger.error("Failed to search customers", e);
            showError("خطأ", "فشل في البحث عن العملاء");
        }
    }
    
    @FXML
    private void handleReset() {
        searchField.clear();
        resultsTable.getItems().clear();
        statusLabel.setText("جاهز للبحث");
    }
    
    @FXML
    private void handleViewDetails() {
        Customer selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("تنبيه", "الرجاء اختيار عميل من القائمة");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("تفاصيل العميل");
        alert.setHeaderText(selected.getName());
        
        StringBuilder details = new StringBuilder();
        details.append("كود العميل: ").append(selected.getCustomerCode()).append("\n\n");
        details.append("معلومات الاتصال:\n");
        details.append("  الهاتف: ").append(selected.getPhoneNumber() != null ? selected.getPhoneNumber() : "-").append("\n");
        details.append("  البريد: ").append(selected.getEmail() != null ? selected.getEmail() : "-").append("\n\n");
        details.append("العنوان: ").append(selected.getAddress() != null ? selected.getAddress() : "-").append("\n");
        details.append("موقع المشروع: ").append(selected.getProjectLocation() != null ? selected.getProjectLocation() : "-").append("\n\n");
        details.append("الرصيد الحالي: ").append(currencyFormat.format(selected.getCurrentBalance() != null ? selected.getCurrentBalance() : 0)).append(" د.ع\n");
        details.append("حد الائتمان: ").append(currencyFormat.format(selected.getCreditLimit() != null ? selected.getCreditLimit() : 0)).append(" د.ع");
        
        alert.setContentText(details.toString());
        alert.showAndWait();
    }
    
    @FXML
    private void handleNewSale() {
        Customer selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("تنبيه", "الرجاء اختيار عميل من القائمة");
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/SaleForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("بيع جديد - " + selected.getName());
            stage.initModality(Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            Scene scene = new Scene(root, 1000, 700);
            stage.setScene(scene);
            
            SaleFormController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMainApp(mainApp);
            controller.setSelectedCustomer(selected);
            
            stage.showAndWait();
            
        } catch (IOException e) {
            logger.error("Failed to open sale form", e);
            showError("خطأ", "فشل في فتح نافذة البيع");
        }
    }
    
    @FXML
    private void handleClose() {
        if (dialogStage != null) {
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
    
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
