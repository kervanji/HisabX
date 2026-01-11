package com.hisabx;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import com.hisabx.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private Stage primaryStage;
    private BorderPane mainLayout;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("HisabX - نظام إدارة المخازن والمبيعات");
        
        // Set application icon
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/images/logo.svg")
            );
            this.primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Failed to load application icon", e);
        }
        
        // Enable full screen mode
        this.primaryStage.setMaximized(true);
        this.primaryStage.setFullScreen(false); // Maximized instead of true fullscreen for better UX
        
        try {
            // Initialize database
            DatabaseManager.initialize();
            
            // Load main layout
            showMainLayout();
            
            logger.info("Application started successfully");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showError("خطأ في بدء التطبيق", "فشل في بدء التطبيق: " + e.getMessage());
        }
    }

    private void showMainLayout() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainApp.class.getResource("/views/MainLayout.fxml"));
        mainLayout = loader.load();
        
        // Get the controller and set the main app reference
        com.hisabx.controller.MainController controller = loader.getController();
        controller.setMainApp(this);
        
        Scene scene = new Scene(mainLayout);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void showPdfPreview(java.io.File pdfFile) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/PdfPreview.fxml"));
            javafx.scene.Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("معاينة الطباعة");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            com.hisabx.controller.PdfPreviewController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPdfFile(pdfFile);

            dialogStage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to show PDF preview", e);
            showError("خطأ", "فشل في عرض معاينة الطباعة: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
