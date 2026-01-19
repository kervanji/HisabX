package com.hisabx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.hisabx.util.TabManager;
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
    @FXML private TabPane mainTabPane;
    @FXML private Tab dashboardTab;
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
        if (mainTabPane != null && dashboardTab != null) {
            TabManager.getInstance().initialize(mainTabPane, dashboardTab, mainApp);
            TabManager.getInstance().setDashboardRefreshCallback(this::refreshDashboard);
        }
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
        TabManager.getInstance().openTab(
                "new-customer",
                "👤 عميل جديد",
                "/views/CustomerForm.fxml",
                (CustomerController controller) -> controller.setTabMode(true)
        );
    }
    
    @FXML
    private void handleNewProduct() {
        TabManager.getInstance().openTab(
                "new-product",
                "📦 منتج جديد",
                "/views/ProductForm.fxml",
                (ProductController controller) -> controller.setTabMode(true)
        );
    }
    
    @FXML
    private void handleNewSale() {
        TabManager.getInstance().openTab(
                "new-sale",
                "🛒 بيع جديد",
                "/views/SaleForm.fxml",
                (SaleFormController controller) -> {
                    controller.setTabMode(true);
                    controller.setMainApp(mainApp);
                }
        );
    }
    
    @FXML
    private void handleViewCustomers() {
        TabManager.getInstance().openTab(
                "customers",
                "👥 العملاء",
                "/views/CustomerList.fxml"
        );
    }
    
    @FXML
    private void handleSearchCustomer() {
        TabManager.getInstance().openTab(
                "customer-search",
                "🔎 بحث العملاء",
                "/views/CustomerSearch.fxml",
                (CustomerSearchController controller) -> {
                    controller.setTabMode(true);
                    controller.setMainApp(mainApp);
                }
        );
    }
    
    @FXML
    private void handleViewInventory() {
        TabManager.getInstance().openTab(
                "inventory",
                "📦 المخزون",
                "/views/InventoryList.fxml"
        );
    }
    
    @FXML
    private void handleLowStock() {
        TabManager.getInstance().openTab(
                "low-stock",
                "⚠️ منخفض المخزون",
                "/views/LowStockList.fxml"
        );
    }
    
    @FXML
    private void handleAddStock() {
        TabManager.getInstance().openTab(
                "add-stock",
                "➕ إضافة مخزون",
                "/views/AddStockDialog.fxml",
                (AddStockController controller) -> controller.setTabMode(true)
        );
    }
    
    @FXML
    private void handleManageCategories() {
        TabManager.getInstance().openTab(
                "categories",
                "🧩 الفئات",
                "/views/CategoryManager.fxml",
                (CategoryController controller) -> controller.setTabMode(true)
        );
    }
    
    @FXML
    private void handleViewSales() {
        TabManager.getInstance().openTab(
                "sales",
                "🧾 المبيعات",
                "/views/SaleList.fxml",
                (SaleListController controller) -> controller.setMainApp(mainApp)
        );
    }
    
    @FXML
    private void handleSalesReport() {
        TabManager.getInstance().openTab(
                "sales-report",
                "📊 تقارير المبيعات",
                "/views/SalesReport.fxml"
        );
    }
    
    @FXML
    private void handlePendingPayments() {
        TabManager.getInstance().openTab(
                "pending-payments",
                "💰 المدفوعات المعلقة",
                "/views/PendingPayments.fxml"
        );
    }
    
    @FXML
    private void handleProductReturn() {
        TabManager.getInstance().openTab(
                "product-return",
                "↩️ إرجاع مواد",
                "/views/ReturnForm.fxml",
                (ReturnController controller) -> controller.setTabMode(true)
        );
    }
    
    @FXML
    private void handleCreateReceipt() {
        TabManager.getInstance().openTab(
                "create-receipt",
                "🧾 إنشاء إيصال",
                "/views/CreateReceipt.fxml",
                (CreateReceiptController controller) -> {
                    controller.setTabMode(true);
                    controller.setMainApp(mainApp);
                }
        );
    }
    
    @FXML
    private void handleViewReceipts() {
        TabManager.getInstance().openTab(
                "receipt-list",
                "📄 الإيصالات",
                "/views/ReceiptList.fxml",
                (ReceiptListController controller) -> controller.setMainApp(mainApp)
        );
    }
    
    @FXML
    private void handleSettings() {
        TabManager.getInstance().openTab(
                "settings",
                "⚙️ الإعدادات",
                "/views/Settings.fxml",
                (SettingsController controller) -> controller.setTabMode(true)
        );
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
