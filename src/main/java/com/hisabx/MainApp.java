package com.hisabx;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import com.hisabx.database.DatabaseManager;
import com.hisabx.controller.LoginController;
import com.hisabx.model.User;
import com.hisabx.model.UserRole;
import com.hisabx.util.SessionManager;
import com.hisabx.util.TabManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private Stage primaryStage;
    private BorderPane mainLayout;
    private com.hisabx.controller.MainController mainController;

    private final List<Stage> managedStages = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("HisabX - نظام إدارة المخازن والمبيعات");

        registerStage(primaryStage);
        
        // Set application icon
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/templates/HisabX.ico")
            );
            this.primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Failed to load application icon", e);
        }
        
        // Window size (main window maximized only)
        this.primaryStage.setWidth(900);
        this.primaryStage.setHeight(700);
        this.primaryStage.setResizable(true);
        this.primaryStage.setMaximized(true);
        this.primaryStage.setFullScreen(false);
        
        try {
            // Initialize database
            DatabaseManager.initialize();
            
            // Set up logout callback
            SessionManager.getInstance().setOnLogoutCallback(this::showLoginScreen);
            
            // Show login screen first
            showLoginScreen();
            
            logger.info("Application started successfully");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showError("خطأ في بدء التطبيق", "فشل في بدء التطبيق: " + e.getMessage());
        }
    }
    
    private void showLoginScreen() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/Login.fxml"));
            Parent loginRoot = loader.load();
            
            LoginController controller = loader.getController();
            controller.setOnLoginSuccess(() -> {
                try {
                    showMainLayout();
                } catch (IOException e) {
                    logger.error("Failed to show main layout after login", e);
                    showError("خطأ", "فشل في تحميل الواجهة الرئيسية");
                }
            });
            
            Scene scene = new Scene(loginRoot);
            applyUiFontSizeToScene(scene, SessionManager.getInstance().getUiFontSize());
            primaryStage.setScene(scene);
            primaryStage.setMaximized(false);
            primaryStage.setResizable(false);
            primaryStage.setWidth(520);
            primaryStage.setHeight(840);
            primaryStage.centerOnScreen();
            primaryStage.show();
            
        } catch (IOException e) {
            logger.error("Failed to show login screen", e);
            showError("خطأ", "فشل في تحميل شاشة الدخول");
        }
    }

    private void showMainLayout() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainApp.class.getResource("/views/MainLayout.fxml"));
        mainLayout = loader.load();
        
        // Get the controller and set the main app reference
        mainController = loader.getController();
        mainController.setMainApp(this);
        
        Scene scene = new Scene(mainLayout);
        applyUiFontSizeToScene(scene, SessionManager.getInstance().getUiFontSize());
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }
    
    public void logout() {
        // Clear UI state that is kept in singletons between sessions
        TabManager.getInstance().reset();
        SessionManager.getInstance().endSession();
    }

    public void lock() {
        if (mainLayout == null || primaryStage.getScene() == null) {
            logout();
            return;
        }

        User previousUser = SessionManager.getInstance().getCurrentUser();
        String previousUsername = previousUser != null ? previousUser.getUsername() : null;
        UserRole previousRole = previousUser != null ? previousUser.getRole() : null;

        SessionManager.getInstance().lockSession();

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class.getResource("/views/Login.fxml"));
            Parent loginRoot = loader.load();

            LoginController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("قفل التطبيق");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.setResizable(false);
            dialogStage.setOnCloseRequest(event -> event.consume());

            registerStage(dialogStage);

            controller.setOnLoginSuccess(() -> {
                dialogStage.close();

                User currentUser = SessionManager.getInstance().getCurrentUser();
                String currentUsername = currentUser != null ? currentUser.getUsername() : null;
                UserRole currentRole = currentUser != null ? currentUser.getRole() : null;

                boolean userChanged = previousUsername != null && !previousUsername.equals(currentUsername);
                boolean roleChanged = previousRole != null && currentRole != null && previousRole != currentRole;

                if (userChanged || roleChanged) {
                    TabManager.getInstance().closeAllTabs();
                }

                if (mainController != null) {
                    mainController.refreshAfterLogin();
                }
            });

            StackPane overlayRoot = new StackPane();
            overlayRoot.setStyle("-fx-background-color: rgba(15, 23, 42, 0.60);");
            overlayRoot.getChildren().add(loginRoot);

            Scene scene = new Scene(overlayRoot);
            scene.setFill(Color.TRANSPARENT);
            applyUiFontSizeToScene(scene, SessionManager.getInstance().getUiFontSize());
            dialogStage.setScene(scene);

            // Match overlay window to the main window position/size
            dialogStage.setX(primaryStage.getX());
            dialogStage.setY(primaryStage.getY());
            dialogStage.setWidth(primaryStage.getWidth());
            dialogStage.setHeight(primaryStage.getHeight());

            primaryStage.xProperty().addListener((obs, o, n) -> dialogStage.setX(n.doubleValue()));
            primaryStage.yProperty().addListener((obs, o, n) -> dialogStage.setY(n.doubleValue()));
            primaryStage.widthProperty().addListener((obs, o, n) -> dialogStage.setWidth(n.doubleValue()));
            primaryStage.heightProperty().addListener((obs, o, n) -> dialogStage.setHeight(n.doubleValue()));

            dialogStage.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to show lock screen", e);
            logout();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void applyUiFontSizeToAllWindows(int fontSizePx) {
        int clamped = Math.max(10, Math.min(24, fontSizePx));
        for (Stage stage : new ArrayList<>(managedStages)) {
            if (stage != null && stage.getScene() != null && stage.getScene().getRoot() != null) {
                stage.getScene().getRoot().setStyle(
                        upsertFontSizeStyle(stage.getScene().getRoot().getStyle(), clamped)
                );
            }
        }
    }

    public static String upsertFontSizeStyle(String existingStyle, int fontSizePx) {
        String style = existingStyle == null ? "" : existingStyle;
        String cleaned = style.replaceAll("(?i)-fx-font-size\\s*:\\s*[^;]+;?", "").trim();
        if (!cleaned.isEmpty() && !cleaned.endsWith(";")) {
            cleaned = cleaned + ";";
        }
        return cleaned + "-fx-font-size: " + fontSizePx + "px;";
    }

    private void applyUiFontSizeToScene(Scene scene, int fontSizePx) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        scene.getRoot().setStyle(upsertFontSizeStyle(scene.getRoot().getStyle(), fontSizePx));
    }

    private void registerStage(Stage stage) {
        if (stage == null) {
            return;
        }
        if (!managedStages.contains(stage)) {
            managedStages.add(stage);
        }
        stage.setOnHidden(e -> managedStages.remove(stage));
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
            applyUiFontSizeToScene(scene, SessionManager.getInstance().getUiFontSize());
            dialogStage.setScene(scene);

            registerStage(dialogStage);

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
