package com.hisabx.controller;

import com.hisabx.model.User;
import com.hisabx.service.AuthService;
import com.hisabx.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML private VBox loginContainer;
    @FXML private Label titleLabel;
    @FXML private Label lastUserLabel;
    @FXML private HBox lastUserBox;
    @FXML private TextField usernameField;
    @FXML private VBox usernameBox;
    @FXML private PasswordField pinField;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Button loginButton;
    @FXML private Button switchUserButton;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    
    private final AuthService authService = new AuthService();
    private Runnable onLoginSuccess;
    private boolean quickLoginMode = false;
    private String lastUsername;
    
    @FXML
    private void initialize() {
        // Check for remembered username
        lastUsername = SessionManager.getInstance().getLastUsername();
        
        if (lastUsername != null && !lastUsername.isEmpty()) {
            // Quick login mode
            showQuickLoginMode();
        } else {
            // Full login mode
            showFullLoginMode();
        }
        
        // Enter key triggers login
        pinField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> {
            if (!usernameField.getText().isEmpty()) {
                pinField.requestFocus();
            }
        });
        
        // Focus on appropriate field
        Platform.runLater(() -> {
            if (quickLoginMode) {
                pinField.requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }
    
    private void showQuickLoginMode() {
        quickLoginMode = true;
        
        // Get user display info
        Optional<User> userOpt = authService.getUserByUsername(lastUsername);
        String displayText = lastUsername;
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            displayText = user.getDisplayName() + " (" + user.getRole().getDisplayName() + ")";
        }
        
        lastUserLabel.setText(displayText);
        lastUserBox.setVisible(true);
        lastUserBox.setManaged(true);
        usernameBox.setVisible(false);
        usernameBox.setManaged(false);
        switchUserButton.setVisible(true);
        switchUserButton.setManaged(true);
        
        titleLabel.setText("مرحباً بعودتك");
    }
    
    private void showFullLoginMode() {
        quickLoginMode = false;
        
        lastUserBox.setVisible(false);
        lastUserBox.setManaged(false);
        usernameBox.setVisible(true);
        usernameBox.setManaged(true);
        switchUserButton.setVisible(false);
        switchUserButton.setManaged(false);
        
        titleLabel.setText("تسجيل الدخول");
        usernameField.clear();
        pinField.clear();
        
        Platform.runLater(() -> usernameField.requestFocus());
    }
    
    @FXML
    private void handleLogin() {
        clearError();
        
        String username = quickLoginMode ? lastUsername : usernameField.getText().trim();
        String pin = pinField.getText();
        
        if (username.isEmpty()) {
            showError("الرجاء إدخال اسم المستخدم");
            return;
        }
        
        if (pin.isEmpty()) {
            showError("الرجاء إدخال الرمز");
            return;
        }
        
        // Attempt authentication
        loginButton.setDisable(true);
        statusLabel.setText("جاري التحقق...");
        
        Optional<User> result = authService.authenticate(username, pin);
        
        if (result.isPresent()) {
            User user = result.get();
            boolean remember = rememberCheckBox.isSelected();
            
            SessionManager.getInstance().startSession(user, remember);
            statusLabel.setText("تم تسجيل الدخول بنجاح!");
            
            logger.info("Login successful for user: {}", username);
            
            if (onLoginSuccess != null) {
                Platform.runLater(onLoginSuccess);
            }
        } else {
            loginButton.setDisable(false);
            statusLabel.setText("");
            
            // Check why login failed
            Optional<User> userCheck = authService.getUserByUsername(username);
            if (userCheck.isEmpty()) {
                showError("اسم المستخدم غير موجود");
            } else if (!userCheck.get().isActive()) {
                showError("الحساب غير مفعّل");
            } else {
                showError("الرمز غير صحيح");
            }
            
            pinField.clear();
            pinField.requestFocus();
        }
    }
    
    @FXML
    private void handleSwitchUser() {
        showFullLoginMode();
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
    
    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
    
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }
}
