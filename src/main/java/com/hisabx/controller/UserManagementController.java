package com.hisabx.controller;

import com.hisabx.model.User;
import com.hisabx.model.UserRole;
import com.hisabx.service.AuthService;
import com.hisabx.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class UserManagementController {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> displayNameColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private TableColumn<User, String> lastLoginColumn;
    @FXML private TableColumn<User, Void> actionsColumn;
    
    @FXML private Label totalUsersLabel;
    @FXML private Label adminCountLabel;
    @FXML private Label sellerCountLabel;
    
    private final AuthService authService = new AuthService();
    private final ObservableList<User> usersList = FXCollections.observableArrayList();
    private boolean tabMode = false;
    
    @FXML
    private void initialize() {
        setupTable();
        loadUsers();
    }
    
    private void setupTable() {
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        displayNameColumn.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        
        roleColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getRole().getDisplayName()));
        
        statusColumn.setCellValueFactory(cellData -> {
            User user = cellData.getValue();
            if (user.isLocked()) {
                return new SimpleStringProperty("🔒 مقفل");
            } else if (user.isActive()) {
                return new SimpleStringProperty("✅ نشط");
            } else {
                return new SimpleStringProperty("⛔ معطل");
            }
        });
        
        lastLoginColumn.setCellValueFactory(cellData -> {
            User user = cellData.getValue();
            if (user.getLastLoginAt() != null) {
                return new SimpleStringProperty(user.getLastLoginAt().format(DATE_FORMAT));
            }
            return new SimpleStringProperty("لم يسجل دخول");
        });
        
        setupActionsColumn();
        usersTable.setItems(usersList);
    }
    
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("✏️");
            private final Button resetBtn = new Button("🔓");
            private final Button deleteBtn = new Button("🗑️");
            
            {
                editBtn.setStyle("-fx-background-color: #1a2744; -fx-text-fill: #e8edf4; -fx-border-color: rgba(100,181,246,0.3); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;");
                resetBtn.setStyle("-fx-background-color: #0d3a1a; -fx-text-fill: #e8edf4; -fx-border-color: rgba(76,175,80,0.35); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;");
                deleteBtn.setStyle("-fx-background-color: #501a1a; -fx-text-fill: #fdeaea; -fx-border-color: rgba(239,83,80,0.4); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 10; -fx-cursor: hand;");
                
                editBtn.setTooltip(new Tooltip("تعديل"));
                resetBtn.setTooltip(new Tooltip("فك القفل"));
                deleteBtn.setTooltip(new Tooltip("حذف"));
                
                editBtn.setOnAction(e -> handleEditUser(getTableRow().getItem()));
                resetBtn.setOnAction(e -> handleUnlockUser(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> handleDeleteUser(getTableRow().getItem()));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableRow().getItem();
                    javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(4);
                    buttons.getChildren().add(editBtn);
                    if (user != null && user.isLocked()) {
                        buttons.getChildren().add(resetBtn);
                    }
                    // Don't allow deleting the current user or the last admin
                    if (user != null && !user.getUsername().equals(SessionManager.getInstance().getCurrentUsername())) {
                        if (user.getRole() != UserRole.ADMIN || authService.getActiveAdminCount() > 1) {
                            buttons.getChildren().add(deleteBtn);
                        }
                    }
                    setGraphic(buttons);
                }
            }
        });
    }
    
    private void loadUsers() {
        usersList.clear();
        usersList.addAll(authService.getAllUsers());
        updateStats();
    }
    
    private void updateStats() {
        int total = usersList.size();
        long admins = usersList.stream().filter(u -> u.getRole() == UserRole.ADMIN).count();
        long sellers = usersList.stream().filter(u -> u.getRole() == UserRole.SELLER).count();
        
        if (totalUsersLabel != null) totalUsersLabel.setText(String.valueOf(total));
        if (adminCountLabel != null) adminCountLabel.setText(String.valueOf(admins));
        if (sellerCountLabel != null) sellerCountLabel.setText(String.valueOf(sellers));
    }
    
    @FXML
    private void handleAddUser() {
        showUserDialog(null);
    }
    
    private void handleEditUser(User user) {
        if (user != null) {
            showUserDialog(user);
        }
    }
    
    
    private void handleUnlockUser(User user) {
        if (user != null && user.isLocked()) {
            authService.unlockUser(user.getId());
            loadUsers();
            showInfo("تم", "تم فك قفل المستخدم");
        }
    }
    
    private void handleDeleteUser(User user) {
        if (user == null) return;
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText("هل أنت متأكد من حذف المستخدم: " + user.getDisplayName() + "؟");
        confirm.setContentText("هذا الإجراء لا يمكن التراجع عنه.");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            authService.deleteUser(user.getId());
            loadUsers();
            showInfo("تم", "تم حذف المستخدم بنجاح");
        }
    }
    
    private void showUserDialog(User existingUser) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(existingUser == null ? "إضافة مستخدم جديد" : "تعديل المستخدم");
        
        ButtonType saveButtonType = new ButtonType("حفظ", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20;");
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("اسم المستخدم (للدخول)");
        if (existingUser != null) {
            usernameField.setText(existingUser.getUsername());
            usernameField.setDisable(true);
        }
        
        TextField displayNameField = new TextField();
        displayNameField.setPromptText("الاسم المعروض");
        if (existingUser != null) {
            displayNameField.setText(existingUser.getDisplayName());
        }
        
        PasswordField pinField = new PasswordField();
        pinField.setPromptText(existingUser == null ? "الرمز (PIN)" : "رمز جديد (اتركه فارغاً للإبقاء)");
        
        ComboBox<UserRole> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll(UserRole.values());
        roleCombo.setValue(existingUser != null ? existingUser.getRole() : UserRole.SELLER);
        
        grid.add(new Label("اسم المستخدم:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("الاسم المعروض:"), 0, 1);
        grid.add(displayNameField, 1, 1);
        grid.add(new Label("الرمز (PIN):"), 0, 2);
        grid.add(pinField, 1, 2);
        grid.add(new Label("الدور:"), 0, 3);
        grid.add(roleCombo, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String username = usernameField.getText().trim();
                String displayName = displayNameField.getText().trim();
                String pin = pinField.getText();
                UserRole role = roleCombo.getValue();
                
                if (username.isEmpty() || displayName.isEmpty()) {
                    showError("خطأ", "الرجاء ملء جميع الحقول المطلوبة");
                    return null;
                }
                
                if (existingUser == null) {
                    // New user
                    if (pin.isEmpty() || pin.length() < 4) {
                        showError("خطأ", "الرمز يجب أن يكون 4 أرقام على الأقل");
                        return null;
                    }
                    if (authService.isUsernameExists(username)) {
                        showError("خطأ", "اسم المستخدم موجود مسبقاً");
                        return null;
                    }
                    User newUser = new User(username, displayName, authService.hashPin(pin), role);
                    return authService.saveUser(newUser);
                } else {
                    // Update existing
                    existingUser.setDisplayName(displayName);
                    existingUser.setRole(role);
                    if (!pin.isEmpty()) {
                        if (pin.length() < 4) {
                            showError("خطأ", "الرمز يجب أن يكون 4 أرقام على الأقل");
                            return null;
                        }
                        existingUser.setPinHash(authService.hashPin(pin));
                    }
                    authService.updateUser(existingUser);
                    return existingUser;
                }
            }
            return null;
        });
        
        Optional<User> result = dialog.showAndWait();
        if (result.isPresent()) {
            loadUsers();
            showInfo("تم", existingUser == null ? "تم إضافة المستخدم بنجاح" : "تم تحديث المستخدم بنجاح");
        }
    }
    
    @FXML
    private void handleRefresh() {
        loadUsers();
    }
    
    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
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
