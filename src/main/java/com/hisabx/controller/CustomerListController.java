package com.hisabx.controller;

import com.hisabx.MainApp;
import com.hisabx.model.Customer;
import com.hisabx.service.CustomerService;
import com.hisabx.service.PrintService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerListController {
    private static final Logger logger = LoggerFactory.getLogger(CustomerListController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    
    @FXML private TextField searchField;
    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, Long> idColumn;
    @FXML private TableColumn<Customer, String> customerCodeColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, String> addressColumn;
    @FXML private TableColumn<Customer, String> projectLocationColumn;
    @FXML private TableColumn<Customer, String> balanceColumn;
    @FXML private TableColumn<Customer, String> balanceIqdColumn;
    @FXML private TableColumn<Customer, String> balanceUsdColumn;
    @FXML private TableColumn<Customer, Void> actionsColumn;
    @FXML private Label totalCustomersLabel;
    @FXML private Label totalDebtLabel;
    @FXML private Label totalCreditLabel;
    @FXML private Label totalDebtIqdLabel;
    @FXML private Label totalDebtUsdLabel;
    
    private final CustomerService customerService = new CustomerService();
    private ObservableList<Customer> allCustomers;
    
    @FXML
    private void initialize() {
        setupTableColumns();
        loadCustomers();
        
        // Enable search on Enter key
        searchField.setOnAction(e -> handleSearch());
    }
    
    private void setupTableColumns() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerCodeColumn.setCellValueFactory(new PropertyValueFactory<>("customerCode"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        projectLocationColumn.setCellValueFactory(new PropertyValueFactory<>("projectLocation"));
        
        balanceColumn.setCellValueFactory(cellData -> {
            Customer customer = cellData.getValue();
            Double balance = customer.getCurrentBalance();
            return new SimpleStringProperty(currencyFormat.format(balance != null ? balance : 0));
        });
        
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item + " د.ع");
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

        if (balanceIqdColumn != null) {
            balanceIqdColumn.setCellValueFactory(cellData -> {
                Customer customer = cellData.getValue();
                return new SimpleStringProperty(currencyFormat.format(customer.getBalanceIqd()));
            });
            balanceIqdColumn.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item + " د.ع");
                        Customer customer = getTableView().getItems().get(getIndex());
                        double balance = customer.getBalanceIqd();
                        if (balance < 0) {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        } else if (balance > 0) {
                            setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #6b7280;");
                        }
                    }
                }
            });
        }

        if (balanceUsdColumn != null) {
            balanceUsdColumn.setCellValueFactory(cellData -> {
                Customer customer = cellData.getValue();
                return new SimpleStringProperty(currencyFormat.format(customer.getBalanceUsd()));
            });
            balanceUsdColumn.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item + " $");
                        Customer customer = getTableView().getItems().get(getIndex());
                        double balance = customer.getBalanceUsd();
                        if (balance < 0) {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        } else if (balance > 0) {
                            setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #6b7280;");
                        }
                    }
                }
            });
        }
        
        setupActionsColumn();
    }
    
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("عرض");
            private final Button editBtn = new Button("تعديل");
            private final Button deleteBtn = new Button("حذف");
            
            {
                viewBtn.setStyle("-fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4;");
                editBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4;");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4;");
                
                viewBtn.setOnAction(e -> handleViewCustomer(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e -> handleEditCustomer(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteCustomer(getTableView().getItems().get(getIndex())));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(3, viewBtn, editBtn, deleteBtn);
                    setGraphic(box);
                }
            }
        });
    }
    
    private void loadCustomers() {
        try {
            List<Customer> customers = customerService.getAllCustomers();
            allCustomers = FXCollections.observableArrayList(customers);
            customersTable.setItems(allCustomers);
            updateStatistics(customers);
        } catch (Exception e) {
            logger.error("Failed to load customers", e);
            showError("خطأ", "فشل في تحميل قائمة العملاء");
        }
    }
    
    private void updateStatistics(List<Customer> customers) {
        totalCustomersLabel.setText("إجمالي العملاء: " + customers.size());
        
        double totalDebtIqd = customers.stream()
                .filter(c -> c.getBalanceIqd() < 0)
                .mapToDouble(c -> Math.abs(c.getBalanceIqd()))
                .sum();
        
        double totalDebtUsd = customers.stream()
                .filter(c -> c.getBalanceUsd() < 0)
                .mapToDouble(c -> Math.abs(c.getBalanceUsd()))
                .sum();
        
        double totalCreditIqd = customers.stream()
                .filter(c -> c.getBalanceIqd() > 0)
                .mapToDouble(Customer::getBalanceIqd)
                .sum();
        
        double totalCreditUsd = customers.stream()
                .filter(c -> c.getBalanceUsd() > 0)
                .mapToDouble(Customer::getBalanceUsd)
                .sum();
        
        totalDebtLabel.setText(currencyFormat.format(totalDebtIqd) + " د.ع");
        totalCreditLabel.setText(currencyFormat.format(totalCreditIqd) + " د.ع");
        
        if (totalDebtIqdLabel != null) {
            totalDebtIqdLabel.setText(currencyFormat.format(totalDebtIqd) + " د.ع");
        }
        if (totalDebtUsdLabel != null) {
            totalDebtUsdLabel.setText(currencyFormat.format(totalDebtUsd) + " $");
        }
    }
    
    @FXML
    private void handleSearch() {
        String searchText = searchField.getText().trim().toLowerCase();
        
        if (searchText.isEmpty()) {
            customersTable.setItems(allCustomers);
            updateStatistics(allCustomers);
            return;
        }
        
        List<Customer> filtered = allCustomers.stream()
                .filter(c -> 
                    (c.getName() != null && c.getName().toLowerCase().contains(searchText)) ||
                    (c.getCustomerCode() != null && c.getCustomerCode().toLowerCase().contains(searchText)) ||
                    (c.getPhoneNumber() != null && c.getPhoneNumber().contains(searchText))
                )
                .collect(Collectors.toList());
        
        customersTable.setItems(FXCollections.observableArrayList(filtered));
        updateStatistics(filtered);
    }
    
    @FXML
    private void handleReset() {
        searchField.clear();
        customersTable.setItems(allCustomers);
        updateStatistics(allCustomers);
    }
    
    @FXML
    private void handleAddCustomer() {
        openCustomerForm(null);
    }
    
    private void handleViewCustomer(Customer customer) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("تفاصيل العميل");
        alert.setHeaderText(customer.getName());
        
        StringBuilder details = new StringBuilder();
        details.append("كود العميل: ").append(customer.getCustomerCode()).append("\n\n");
        details.append("معلومات الاتصال:\n");
        details.append("  الهاتف: ").append(customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "-").append("\n");
        details.append("\n");
        details.append("العنوان: ").append(customer.getAddress() != null ? customer.getAddress() : "-").append("\n");
        details.append("مواقع المشاريع:\n").append(customer.getProjectLocation() != null ? customer.getProjectLocation() : "-").append("\n\n");
        details.append("رصيد الدينار: ").append(currencyFormat.format(customer.getBalanceIqd())).append(" د.ع\n");
        details.append("رصيد الدولار: ").append(currencyFormat.format(customer.getBalanceUsd())).append(" $");
        
        alert.setContentText(details.toString());
        alert.showAndWait();
    }
    
    private void handleEditCustomer(Customer customer) {
        openCustomerForm(customer);
    }
    
    private void handleDeleteCustomer(Customer customer) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText("هل أنت متأكد من حذف العميل: " + customer.getName() + "؟\nلا يمكن التراجع عن هذا الإجراء.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    customerService.deleteCustomer(customer);
                    loadCustomers();
                    showInfo("تم", "تم حذف العميل بنجاح");
                } catch (Exception e) {
                    logger.error("Failed to delete customer", e);
                    showError("خطأ", "فشل في حذف العميل: " + e.getMessage());
                }
            }
        });
    }
    
    private void openCustomerForm(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CustomerForm.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle(customer == null ? "إضافة عميل جديد" : "تعديل بيانات العميل");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(false);
            
            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);
            if (customer != null) {
                controller.setCustomer(customer);
            }
            
            stage.showAndWait();
            
            if (controller.isSaved()) {
                loadCustomers();
            }
        } catch (IOException e) {
            logger.error("Failed to open customer form", e);
            showError("خطأ", "فشل في فتح نموذج العميل");
        }
    }
    
    @FXML
    private void handleExport() {
        showInfo("قريباً", "ميزة التصدير إلى Excel قيد التطوير");
    }
    
    @FXML
    private void handlePrint() {
        try {
            PrintService printService = new PrintService();
            java.io.File pdfFile = printService.generateCustomerListPdf(allCustomers);
            
            if (pdfFile.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdfFile);
                }
                showInfo("تم", "تم إنشاء تقرير العملاء:\n" + pdfFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Failed to print customer list", e);
            showError("خطأ", "فشل في طباعة قائمة العملاء: " + e.getMessage());
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
