package com.hisabx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.hisabx.MainApp;
import com.hisabx.model.Product;
import com.hisabx.model.Sale;
import com.hisabx.service.CustomerService;
import com.hisabx.service.InventoryService;
import com.hisabx.service.SalesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.prefs.Preferences;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    
    @FXML private BorderPane mainLayout;
    @FXML private Label todaySalesCountLabel;
    @FXML private Label todaySalesAmountLabel;
    @FXML private Label lowStockDescLabel;
    @FXML private Label lowStockCountLabel;
    @FXML private Label pendingPaymentsDescLabel;
    @FXML private Label pendingPaymentsLabel;
    @FXML private Label totalCustomersLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label totalSalesLabel;
    @FXML private Label inventoryValueLabel;
    @FXML private Label companyNameLabel;
    
    private static final String PREF_COMPANY_NAME = "company.name";
    
    private MainApp mainApp;
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final SalesService salesService = new SalesService();
    
    @FXML
    private void initialize() {
        loadCompanyName();
        refreshDashboard();
    }
    
    private void loadCompanyName() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
            String companyName = prefs.get(PREF_COMPANY_NAME, "");
            if (companyNameLabel != null && !companyName.isEmpty()) {
                companyNameLabel.setText(companyName);
            }
        } catch (Exception e) {
            logger.warn("Failed to load company name", e);
        }
    }
    
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
        refreshDashboard();
    }
    
    private void registerDashboardRefresh(Stage stage) {
        if (stage == null) {
            return;
        }
        stage.setOnHidden(event -> refreshDashboard());
        stage.setOnCloseRequest(event -> refreshDashboard());
    }
    
    private void refreshDashboard() {
        try {
            // Total customers
            int customersCount = customerService.getAllCustomers().size();
            if (totalCustomersLabel != null) {
                totalCustomersLabel.setText(String.valueOf(customersCount));
            }
            
            // Total products
            int productsCount = inventoryService.getAllProducts().size();
            if (totalProductsLabel != null) {
                totalProductsLabel.setText(String.valueOf(productsCount));
            }
            
            // Total sales count
            List<Sale> allSales = salesService.getAllSales();
            if (totalSalesLabel != null) {
                totalSalesLabel.setText(String.valueOf(allSales.size()));
            }
            
            // Today's sales
            LocalDate today = LocalDate.now();
            List<Sale> todaySales = allSales.stream()
                    .filter(s -> s.getSaleDate() != null && s.getSaleDate().toLocalDate().equals(today))
                    .toList();
            double todayAmount = todaySales.stream().mapToDouble(s -> s.getFinalAmount() != null ? s.getFinalAmount() : 0).sum();
            
            if (todaySalesCountLabel != null) {
                todaySalesCountLabel.setText("عدد المبيعات: " + todaySales.size());
            }
            if (todaySalesAmountLabel != null) {
                todaySalesAmountLabel.setText(currencyFormat.format(todayAmount) + " د.ع");
            }
            
            // Low stock products
            List<Product> lowStockProducts = inventoryService.getLowStockProducts();
            if (lowStockCountLabel != null) {
                if (lowStockProducts.isEmpty()) {
                    lowStockCountLabel.setText("لا توجد تنبيهات");
                    lowStockCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #35b585; -fx-background-color: #e6fff4; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    lowStockCountLabel.setText(lowStockProducts.size() + " منتج منخفض");
                    lowStockCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444; -fx-background-color: #fee2e2; -fx-padding: 6 10; -fx-background-radius: 8;");
                }
            }
            
            // Pending payments
            List<Sale> pendingPayments = salesService.getPendingPayments();
            double pendingAmount = pendingPayments.stream().mapToDouble(s -> {
                double finalAmt = s.getFinalAmount() != null ? s.getFinalAmount() : 0;
                double paidAmt = s.getPaidAmount() != null ? s.getPaidAmount() : 0;
                return finalAmt - paidAmt;
            }).sum();
            
            if (pendingPaymentsLabel != null) {
                if (pendingPayments.isEmpty()) {
                    pendingPaymentsLabel.setText("لا توجد معلقات");
                    pendingPaymentsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #35b585; -fx-background-color: #e6fff4; -fx-padding: 6 10; -fx-background-radius: 8;");
                } else {
                    pendingPaymentsLabel.setText(pendingPayments.size() + " فاتورة (" + currencyFormat.format(pendingAmount) + ")");
                    pendingPaymentsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ff8c42; -fx-background-color: #fff2e5; -fx-padding: 6 10; -fx-background-radius: 8;");
                }
            }
            
            // Inventory value
            double inventoryValue = inventoryService.getTotalInventoryValue();
            if (inventoryValueLabel != null) {
                inventoryValueLabel.setText(currencyFormat.format(inventoryValue) + " د.ع");
            }
            
        } catch (Exception e) {
            logger.error("Failed to refresh dashboard", e);
        }
    }
    
    @FXML
    private void handleNewCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CustomerForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("عميل جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open new customer window", e);
            showError("خطأ", "فشل في فتح نافذة العميل الجديد");
        }
    }
    
    @FXML
    private void handleNewProduct() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/ProductForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("منتج جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            ProductController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open new product window", e);
            showError("خطأ", "فشل في فتح نافذة المنتج الجديد");
        }
    }
    
    @FXML
    private void handleNewSale() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/SaleForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("بيع جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root, 1000, 700);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            SaleFormController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMainApp(mainApp);
            registerDashboardRefresh(stage);
            stage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open new sale window", e);
            showError("خطأ", "فشل في فتح نافذة البيع الجديد");
        }
    }
    
    @FXML
    private void handleViewCustomers() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CustomerList.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("عرض العملاء");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open customer list window", e);
            showError("خطأ", "فشل في فتح نافذة عرض العملاء");
        }
    }
    
    @FXML
    private void handleSearchCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CustomerSearch.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("البحث عن عميل");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            CustomerSearchController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMainApp(mainApp);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open customer search window", e);
            showError("خطأ", "فشل في فتح نافذة البحث عن العملاء");
        }
    }
    
    @FXML
    private void handleViewInventory() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/InventoryList.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("عرض المخزون");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open inventory window", e);
            showError("خطأ", "فشل في فتح نافذة عرض المخزون");
        }
    }
    
    @FXML
    private void handleLowStock() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/LowStockList.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("المنتجات منخفضة المخزون");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة المنتجات منخفضة المخزون");
        }
    }
    
    @FXML
    private void handleAddStock() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/AddStockDialog.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إضافة مخزون");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            com.hisabx.controller.AddStockController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.showAndWait();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة إضافة المخزون");
        }
    }
    
    @FXML
    private void handleManageCategories() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CategoryManager.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إدارة الفئات");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            CategoryController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.showAndWait();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة إدارة الفئات");
        }
    }
    
    @FXML
    private void handleViewSales() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/SaleList.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("عرض المبيعات");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            SaleListController controller = loader.getController();
            controller.setMainApp(mainApp);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة عرض المبيعات");
        }
    }
    
    @FXML
    private void handleSalesReport() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/SalesReport.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("تقارير المبيعات");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root, 950, 650);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open sales report window", e);
            showError("خطأ", "فشل في فتح نافذة تقارير المبيعات");
        }
    }
    
    @FXML
    private void handlePendingPayments() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/PendingPayments.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("المدفوعات المعلقة");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            stage.show();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة المدفوعات المعلقة");
        }
    }
    
    @FXML
    private void handleProductReturn() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/ReturnForm.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إرجاع مواد مباعة");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            ReturnController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open product return window", e);
            showError("خطأ", "فشل في فتح نافذة إرجاع المواد");
        }
    }
    
    @FXML
    private void handleCreateReceipt() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/CreateReceipt.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إنشاء إيصال جديد");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            CreateReceiptController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMainApp(mainApp);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open create receipt window", e);
            showError("خطأ", "فشل في فتح نافذة إنشاء الإيصال");
        }
    }
    
    @FXML
    private void handleViewReceipts() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/ReceiptList.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("عرض الإيصالات");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            ReceiptListController controller = loader.getController();
            controller.setMainApp(mainApp);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open receipts list window", e);
            showError("خطأ", "فشل في فتح نافذة عرض الإيصالات");
        }
    }
    
    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/Settings.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("إعدادات النظام");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(mainApp.getPrimaryStage());
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMaximized(true);
            
            SettingsController controller = loader.getController();
            controller.setDialogStage(stage);
            registerDashboardRefresh(stage);
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open settings window", e);
            showError("خطأ", "فشل في فتح نافذة الإعدادات");
        }
    }
    
    @FXML
    private void handleFirebaseSync() {
        // TODO: Implement Firebase sync
        showInfo("قريباً", "ميزة المزامنة مع فايربيس قيد التطوير");
    }
    
    @FXML
    private void handleAbout() {
        showInfo("عن البرنامج", 
                "HisabX v1.0.0\n\n" +
                "من تطوير: KervanjiHolding\n" +
                "الموقع: Kervanjiholding.com\n\n" +
                "نظام متكامل لإدارة المخازن والمبيعات\n\n" +
                "المميزات:\n" +
                "• إدارة العملاء والمخزون\n" +
                "• نظام المبيعات والفواتير\n" +
                "• إصدار الإيصالات الفورية\n" +
                "• تخزين البيانات محلياً\n" +
                "• دعم المزامنة السحابية (قريباً)\n\n" +
                "للدعم الفني: 07730199732\n\n" +
                "© 2025 KervanjiHolding. جميع الحقوق محفوظة.");
    }
    
    @FXML
    private void handleExit() {
        System.exit(0);
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
