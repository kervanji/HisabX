package com.hisabx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.hisabx.MainApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private BorderPane mainLayout;
    
    private MainApp mainApp;
    
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
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
            
            CustomerController controller = loader.getController();
            controller.setDialogStage(stage);
            
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
            
            ProductController controller = loader.getController();
            controller.setDialogStage(stage);
            
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
            
            SaleFormController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMainApp(mainApp);
            
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
            
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open customer list window", e);
            showError("خطأ", "فشل في فتح نافذة عرض العملاء");
        }
    }
    
    @FXML
    private void handleSearchCustomer() {
        // TODO: Implement customer search
        showInfo("قريباً", "ميزة البحث عن العملاء قيد التطوير");
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
            
            com.hisabx.controller.AddStockController controller = loader.getController();
            controller.setDialogStage(stage);
            
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
            
            CategoryController controller = loader.getController();
            controller.setDialogStage(stage);
            
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
            
            SaleListController controller = loader.getController();
            controller.setMainApp(mainApp);
            
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
            
            stage.show();
        } catch (IOException e) {
            showError("خطأ", "فشل في فتح نافذة المدفوعات المعلقة");
        }
    }
    
    @FXML
    private void handleCreateReceipt() {
        // TODO: Implement create receipt
        showInfo("قريباً", "ميزة إنشاء الإيصالات قيد التطوير");
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
            
            ReceiptListController controller = loader.getController();
            controller.setMainApp(mainApp);
            
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open receipts list window", e);
            showError("خطأ", "فشل في فتح نافذة عرض الإيصالات");
        }
    }
    
    @FXML
    private void handleSettings() {
        // TODO: Implement settings
        showInfo("قريباً", "ميزة الإعدادات قيد التطوير");
    }
    
    @FXML
    private void handleFirebaseSync() {
        // TODO: Implement Firebase sync
        showInfo("قريباً", "ميزة المزامنة مع فايربيس قيد التطوير");
    }
    
    @FXML
    private void handleHelp() {
        showInfo("دليل الاستخدام", 
                "مرحباً بك في نظام HisabX\n\n" +
                "1. إدارة العملاء: إضافة وتعديل وحذف العملاء\n" +
                "2. إدارة المخزون: إدارة المنتجات والمخزون\n" +
                "3. المبيعات: إنشاء وتتبع المبيعات\n" +
                "4. الإيصالات: إنشاء وطباعة الإيصالات\n\n" +
                "للمساعدة الإضافية، يرجى التواصل مع الدعم الفني.");
    }
    
    @FXML
    private void handleAbout() {
        showInfo("عن البرنامج", 
                "HisabX v1.0.0\n\n" +
                "نظام متكامل لإدارة المخازن والمبيعات\n" +
                "المصمم خصيصاً للسوق السعودي\n\n" +
                "المميزات:\n" +
                "• إدارة العملاء والمخزون\n" +
                "• نظام المبيعات والفواتير\n" +
                "• إصدار الإيصالات الفورية\n" +
                "• تخزين البيانات محلياً\n" +
                "• دعم المزامنة السحابية (قريباً)\n\n" +
                "© 2025 HisabX. جميع الحقوق محفوظة.");
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
