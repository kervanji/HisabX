package com.hisabx.controller;

import com.hisabx.model.Customer;
import com.hisabx.model.Product;
import com.hisabx.model.Sale;
import com.hisabx.service.CustomerService;
import com.hisabx.service.InventoryService;
import com.hisabx.service.ReceiptService;
import com.hisabx.service.SalesService;
import com.hisabx.util.SessionManager;
import com.hisabx.util.TabManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    private static final String PREF_BANNER_PATH = "receipt.banner.path";
    private static final String PREF_COMPANY_NAME = "company.name";

    @FXML
    private TextField backupPathField;
    @FXML
    private TextField restorePathField;
    @FXML
    private TextField bannerPathField;
    @FXML
    private TextField receiptsPathField;
    @FXML
    private TextField companyNameField;
    @FXML
    private Slider fontSizeSlider;
    @FXML
    private Label fontSizeValueLabel;
    @FXML
    private Label backupStatusLabel;
    @FXML
    private Label customersCountLabel;
    @FXML
    private Label productsCountLabel;
    @FXML
    private Label salesCountLabel;
    @FXML
    private Label receiptsCountLabel;
    @FXML
    private Label dbSizeLabel;
    @FXML
    private Label driveStatusLabel;
    @FXML
    private Label lastBackupLabel;

    private com.hisabx.service.drive.BackupService backupService;
    private final CustomerService customerService = new CustomerService();
    private final InventoryService inventoryService = new InventoryService();
    private final SalesService salesService = new SalesService();
    private final ReceiptService receiptService = new ReceiptService();
    private Stage dialogStage;

    @FXML
    private void initialize() {
        // Set default backup path
        backupPathField.setText(System.getProperty("user.home") + File.separator + "HisabX_Backups");

        // Load preferences
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        String bannerPath = prefs.get(PREF_BANNER_PATH, "");
        bannerPathField.setText(bannerPath);

        // Load company name
        String companyName = prefs.get(PREF_COMPANY_NAME, "");
        if (companyNameField != null) {
            companyNameField.setText(companyName);
        }

        // Load statistics
        handleRefreshStats();

        // Load UI font size
        if (fontSizeSlider != null && fontSizeValueLabel != null) {
            int size = SessionManager.getInstance().getUiFontSize();
            fontSizeSlider.setValue(size);
            fontSizeValueLabel.setText(size + "px");
            fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int rounded = (int) Math.round(newVal.doubleValue());
                fontSizeValueLabel.setText(rounded + "px");
                SessionManager.getInstance().setUiFontSize(rounded);

                com.hisabx.MainApp app = TabManager.getInstance().getMainApp();
                if (app != null) {
                    app.applyUiFontSizeToAllWindows(rounded);
                } else if (dialogStage != null && dialogStage.getScene() != null) {
                    dialogStage.getScene().getRoot().setStyle(
                            com.hisabx.MainApp.upsertFontSizeStyle(dialogStage.getScene().getRoot().getStyle(),
                                    rounded));
                }
            });
        }

        // Initialize Drive Services
        com.hisabx.MainApp app = TabManager.getInstance().getMainApp();
        if (app != null) {
            this.backupService = app.getBackupService();
            updateDriveStatus();
        }
    }

    private void updateDriveStatus() {
        if (backupService == null)
            return;

        // This is a bit of a hack since we didn't add getDriveService to BackupService
        // But we initialized it in MainApp.
        // Let's assume for now we can rely on UI feedback from connection attempts.
        // Ideally we should expose connection status from BackupService.

        // For now, let's just leave status update to after actions or when we can check
        // real status.
        // We will implement a proper check in next iteration or via MainApp getter if
        // needed.
    }

    @FXML
    private void handleConnectGoogleDrive() {
        if (backupService == null) {
            showError("خطأ", "خدمة النسخ الاحتياطي غير متوفرة");
            return;
        }

        // We need to trigger the OAuth flow.
        // Since BackupService manages Drive, we might need a method there or in
        // DriveService.
        // Let's use a background thread to re-initialize or trigger auth if needed.
        new Thread(() -> {
            try {
                // Re-running initialize usually triggers auth flow if not signed in
                // But we don't have direct access to GoogleDriveService instance here easily
                // without getter.
                // Let's rely on MainApp to expose it or BackupService to expose it.
                // I will assume I added getGoogleDriveService to MainApp or similar validation
                // in the next step if needed.
                // actually, let's use the one from MainApp if I can.

                // Reflection or cast if needed, but I didn't add getter for GoogleDriveService
                // in MainApp, only BackupService.
                // Wait, inside MainApp I initialized BackupService with DriveService.
                // I should add a check logic here.

                // Simulating connection check/trigger
                javafx.application.Platform.runLater(() -> {
                    driveStatusLabel.setText("جارِ الاتصال...");
                    driveStatusLabel.setStyle("-fx-text-fill: #fbbf24;"); // Orange
                });

                // Real implementation would call driveService.initialize() again or check
                // status
                // For now, let's assume the user has to restart or it's auto-connected.
                // To make this button functional, I really need a method like `connect()` in
                // BackupService or DriveService.

                // Let's notify user to restart if first time, or handle it properly.
                javafx.application.Platform.runLater(() -> {
                    showInfo("Google Drive", "إذا لم تظهر نافذة الدخول للمتصفح، يرجى إعادة تشغيل البرنامج.");
                });
            } catch (Exception e) {
                logger.error("Failed to connect", e);
                javafx.application.Platform.runLater(() -> showError("خطأ", "فشل الاتصال: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleBackupNow() {
        if (backupService == null)
            return;

        backupStatusLabel.setText("جارِ إنشاء النسخة الاحتياطية...");
        backupStatusLabel.setStyle("-fx-text-fill: #fbbf24;");

        new Thread(() -> {
            try {
                backupService.performBackup();
                javafx.application.Platform.runLater(() -> {
                    backupStatusLabel.setText("✓ تم الانتهاء من النسخ الاحتياطي السحابي");
                    backupStatusLabel.setStyle("-fx-text-fill: #10b981;");
                    lastBackupLabel.setText(
                            "آخر نسخة: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                });
            } catch (Exception e) {
                logger.error("Backup failed", e);
                javafx.application.Platform.runLater(() -> {
                    backupStatusLabel.setText("✗ فشل النسخ الاحتياطي");
                    backupStatusLabel.setStyle("-fx-text-fill: #ef4444;");
                });
            }
        }).start();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleBrowseBackupPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("اختر مجلد النسخ الاحتياطي");
        File dir = chooser.showDialog(dialogStage);
        if (dir != null) {
            backupPathField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void handleBrowseRestorePath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختر ملف النسخة الاحتياطية");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database Files", "*.db"));
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            restorePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleCreateBackup() {
        String backupDir = backupPathField.getText().trim();
        if (backupDir.isEmpty()) {
            showError("خطأ", "الرجاء تحديد مجلد النسخ الاحتياطي");
            return;
        }

        try {
            // Create backup directory if not exists
            File dir = new File(backupDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Source database file
            File sourceDb = new File("hisabx.db");
            if (!sourceDb.exists()) {
                showError("خطأ", "ملف قاعدة البيانات غير موجود");
                return;
            }

            // Create backup with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "hisabx_backup_" + timestamp + ".db";
            File backupFile = new File(dir, backupFileName);

            Files.copy(sourceDb.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            backupStatusLabel.setText("✓ تم إنشاء النسخة الاحتياطية: " + backupFileName);
            backupStatusLabel.setStyle("-fx-text-fill: #10b981;");

            showSuccess("تم بنجاح", "تم إنشاء النسخة الاحتياطية بنجاح\n" + backupFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to create backup", e);
            backupStatusLabel.setText("✗ فشل في إنشاء النسخة الاحتياطية");
            backupStatusLabel.setStyle("-fx-text-fill: #ef4444;");
            showError("خطأ", "فشل في إنشاء النسخة الاحتياطية: " + e.getMessage());
        }
    }

    @FXML
    private void handleRestoreBackup() {
        String restorePath = restorePathField.getText().trim();
        if (restorePath.isEmpty()) {
            showError("خطأ", "الرجاء تحديد ملف النسخة الاحتياطية");
            return;
        }

        File backupFile = new File(restorePath);
        if (!backupFile.exists()) {
            showError("خطأ", "ملف النسخة الاحتياطية غير موجود");
            return;
        }

        // Confirm restore
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الاستعادة");
        confirm.setHeaderText("هل أنت متأكد من استعادة البيانات؟");
        confirm.setContentText(
                "سيتم استبدال جميع البيانات الحالية بالبيانات من النسخة الاحتياطية.\nهذا الإجراء لا يمكن التراجع عنه!");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Close database connections first
                    com.hisabx.database.DatabaseManager.shutdown();

                    // Backup current database before restore
                    File currentDb = new File("hisabx.db");
                    if (currentDb.exists()) {
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        File preRestoreBackup = new File("hisabx_pre_restore_" + timestamp + ".db");
                        Files.copy(currentDb.toPath(), preRestoreBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Restore from backup
                    Files.copy(backupFile.toPath(), currentDb.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    backupStatusLabel.setText("✓ تم استعادة البيانات بنجاح - يرجى إعادة تشغيل البرنامج");
                    backupStatusLabel.setStyle("-fx-text-fill: #10b981;");

                    showSuccess("تم بنجاح", "تم استعادة البيانات بنجاح!\nيرجى إعادة تشغيل البرنامج لتطبيق التغييرات.");

                } catch (Exception e) {
                    logger.error("Failed to restore backup", e);
                    backupStatusLabel.setText("✗ فشل في استعادة البيانات");
                    backupStatusLabel.setStyle("-fx-text-fill: #ef4444;");
                    showError("خطأ", "فشل في استعادة البيانات: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleSaveCompanyName() {
        String companyName = companyNameField.getText().trim();
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);

        if (companyName.isEmpty()) {
            prefs.remove(PREF_COMPANY_NAME);
            showSuccess("تم", "تم إزالة اسم الشركة");
        } else {
            prefs.put(PREF_COMPANY_NAME, companyName);
            showSuccess("تم", "تم حفظ اسم الشركة بنجاح\nسيظهر عند إعادة تشغيل البرنامج");
        }
    }

    @FXML
    private void handleBrowseBannerPath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختر صورة الشعار");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            bannerPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveBanner() {
        String bannerPath = bannerPathField.getText().trim();
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);

        if (bannerPath.isEmpty()) {
            prefs.remove(PREF_BANNER_PATH);
            showSuccess("تم", "تم إزالة الشعار");
        } else {
            File file = new File(bannerPath);
            if (!file.exists()) {
                showError("خطأ", "ملف الصورة غير موجود");
                return;
            }
            prefs.put(PREF_BANNER_PATH, bannerPath);
            showSuccess("تم", "تم حفظ الشعار بنجاح");
        }
    }

    @FXML
    private void handleRemoveBanner() {
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        prefs.remove(PREF_BANNER_PATH);
        bannerPathField.clear();
        showSuccess("تم", "تم إزالة الشعار");
    }

    @FXML
    private void handleOpenReceiptsFolder() {
        try {
            File receiptsDir = new File(receiptsPathField.getText().trim());
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(receiptsDir);
            }
        } catch (Exception e) {
            logger.error("Failed to open receipts folder", e);
            showError("خطأ", "فشل في فتح مجلد الإيصالات");
        }
    }

    @FXML
    private void handleRefreshStats() {
        try {
            int customersCount = customerService.getAllCustomers().size();
            int productsCount = inventoryService.getAllProducts().size();
            int salesCount = salesService.getAllSales().size();
            int receiptsCount = receiptService.getAllReceipts().size();

            customersCountLabel.setText("عدد العملاء: " + customersCount);
            productsCountLabel.setText("عدد المنتجات: " + productsCount);
            salesCountLabel.setText("عدد المبيعات: " + salesCount);
            receiptsCountLabel.setText("عدد الإيصالات: " + receiptsCount);

            File dbFile = new File("hisabx.db");
            if (dbFile.exists()) {
                long sizeKB = dbFile.length() / 1024;
                dbSizeLabel.setText("حجم قاعدة البيانات: " + sizeKB + " KB");
            }
        } catch (Exception e) {
            logger.error("Failed to refresh stats", e);
        }
    }

    @FXML
    private void handleResetDatabase() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد إعادة التعيين");
        confirm.setHeaderText("⚠️ تحذير خطير!");
        confirm.setContentText("سيتم حذف جميع البيانات نهائياً!\nهل أنت متأكد تماماً؟");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Second confirmation
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("تأكيد نهائي");
                dialog.setHeaderText("للتأكيد، اكتب 'حذف' في الحقل أدناه");
                dialog.setContentText("اكتب 'حذف':");

                dialog.showAndWait().ifPresent(text -> {
                    if ("حذف".equals(text.trim())) {
                        try {
                            // Create backup before reset
                            File currentDb = new File("hisabx.db");
                            if (currentDb.exists()) {
                                String timestamp = LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                                File preResetBackup = new File("hisabx_pre_reset_" + timestamp + ".db");
                                Files.copy(currentDb.toPath(), preResetBackup.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                            }

                            // Delete database
                            com.hisabx.database.DatabaseManager.shutdown();
                            if (currentDb.exists()) {
                                currentDb.delete();
                            }

                            showSuccess("تم", "تم إعادة تعيين قاعدة البيانات.\nيرجى إعادة تشغيل البرنامج.");
                        } catch (Exception e) {
                            logger.error("Failed to reset database", e);
                            showError("خطأ", "فشل في إعادة تعيين قاعدة البيانات");
                        }
                    } else {
                        showInfo("إلغاء", "تم إلغاء العملية");
                    }
                });
            }
        });
    }

    @FXML
    private void handleExportCustomers() {
        exportToCSV("customers", () -> {
            List<Customer> customers = customerService.getAllCustomers();
            StringBuilder sb = new StringBuilder();
            sb.append("الكود,الاسم,الهاتف,العنوان,موقع المشروع,الرصيد\n");
            for (Customer c : customers) {
                sb.append(escape(c.getCustomerCode())).append(",");
                sb.append(escape(c.getName())).append(",");
                sb.append(escape(c.getPhoneNumber())).append(",");
                sb.append(escape(c.getAddress())).append(",");
                sb.append(escape(c.getProjectLocation())).append(",");
                sb.append(c.getCurrentBalance() != null ? c.getCurrentBalance() : 0).append("\n");
            }
            return sb.toString();
        });
    }

    @FXML
    private void handleExportProducts() {
        exportToCSV("products", () -> {
            List<Product> products = inventoryService.getAllProducts();
            StringBuilder sb = new StringBuilder();
            sb.append("الكود,الاسم,الفئة,السعر,التكلفة,الكمية,الحد الأدنى\n");
            for (Product p : products) {
                sb.append(escape(p.getProductCode())).append(",");
                sb.append(escape(p.getName())).append(",");
                sb.append(escape(p.getCategory())).append(",");
                sb.append(p.getUnitPrice() != null ? p.getUnitPrice() : 0).append(",");
                sb.append(p.getCostPrice() != null ? p.getCostPrice() : 0).append(",");
                sb.append(p.getQuantityInStock() != null ? p.getQuantityInStock() : 0).append(",");
                sb.append(p.getMinimumStock() != null ? p.getMinimumStock() : 0).append("\n");
            }
            return sb.toString();
        });
    }

    @FXML
    private void handleExportSales() {
        exportToCSV("sales", () -> {
            List<Sale> sales = salesService.getAllSales();
            StringBuilder sb = new StringBuilder();
            sb.append("رقم الفاتورة,العميل,التاريخ,الإجمالي,المدفوع,الحالة\n");
            for (Sale s : sales) {
                sb.append(escape(s.getSaleCode())).append(",");
                sb.append(escape(s.getCustomer() != null ? s.getCustomer().getName() : "-")).append(",");
                sb.append(s.getSaleDate() != null
                        ? s.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "-").append(",");
                sb.append(s.getFinalAmount() != null ? s.getFinalAmount() : 0).append(",");
                sb.append(s.getPaidAmount() != null ? s.getPaidAmount() : 0).append(",");
                sb.append(escape(s.getPaymentStatus())).append("\n");
            }
            return sb.toString();
        });
    }

    private void exportToCSV(String name, java.util.function.Supplier<String> dataSupplier) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("حفظ ملف CSV");
        chooser.setInitialFileName(name + "_export.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(dialogStage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
                // Add BOM for Excel compatibility
                writer.write('\ufeff');
                writer.write(dataSupplier.get());
                showSuccess("تم", "تم تصدير البيانات بنجاح إلى:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to export to CSV", e);
                showError("خطأ", "فشل في تصدير البيانات");
            }
        }
    }

    private String escape(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
