package com.hisabx.controller;

import com.hisabx.MainApp;
import com.hisabx.model.Receipt;
import com.hisabx.model.Sale;
import com.hisabx.service.ReceiptService;
import com.hisabx.service.SalesService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class CreateReceiptController {
    private static final Logger logger = LoggerFactory.getLogger(CreateReceiptController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    @FXML private TextField saleSearchField;
    @FXML private TableView<Sale> salesTable;
    @FXML private TableColumn<Sale, String> saleCodeColumn;
    @FXML private TableColumn<Sale, String> customerNameColumn;
    @FXML private TableColumn<Sale, String> saleDateColumn;
    @FXML private TableColumn<Sale, String> totalAmountColumn;
    @FXML private TableColumn<Sale, String> paymentStatusColumn;
    @FXML private TableColumn<Sale, String> hasReceiptColumn;
    @FXML private VBox receiptDetailsBox;
    @FXML private ComboBox<String> templateComboBox;
    @FXML private TextField receiptNotesField;
    @FXML private CheckBox printAfterCreateCheckBox;
    @FXML private Button createButton;
    @FXML private Label statusLabel;
    
    private final SalesService salesService = new SalesService();
    private final ReceiptService receiptService = new ReceiptService();
    private Stage dialogStage;
    private MainApp mainApp;
    private Sale selectedSale;
    private boolean tabMode = false;
    
    @FXML
    private void initialize() {
        setupTableColumns();
        setupTemplateComboBox();
        
        // Enable search on Enter
        saleSearchField.setOnAction(e -> handleSearchSale());
        
        // Handle table selection
        salesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedSale = newVal;
            updateSelectionState();
        });
        
        // Double-click to create receipt
        salesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && selectedSale != null) {
                handleCreateReceipt();
            }
        });
        
        // Load all sales initially
        loadAllSales();
    }
    
    private void setupTableColumns() {
        saleCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaleCode()));
        
        customerNameColumn.setCellValueFactory(data -> {
            Sale sale = data.getValue();
            String name = sale.getCustomer() != null ? sale.getCustomer().getName() : "-";
            return new SimpleStringProperty(name);
        });
        
        saleDateColumn.setCellValueFactory(data -> {
            Sale sale = data.getValue();
            String date = sale.getSaleDate() != null ? sale.getSaleDate().format(dateFormatter) : "-";
            return new SimpleStringProperty(date);
        });
        
        totalAmountColumn.setCellValueFactory(data -> {
            Sale sale = data.getValue();
            return new SimpleStringProperty(currencyFormat.format(sale.getFinalAmount()) + " د.ع");
        });
        
        paymentStatusColumn.setCellValueFactory(data -> {
            Sale sale = data.getValue();
            String status = "PAID".equals(sale.getPaymentStatus()) ? "مدفوع" : "معلق";
            return new SimpleStringProperty(status);
        });
        
        paymentStatusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("مدفوع".equals(item)) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        hasReceiptColumn.setCellValueFactory(data -> {
            Sale sale = data.getValue();
            boolean hasReceipt = receiptService.hasReceiptForSale(sale.getId());
            return new SimpleStringProperty(hasReceipt ? "✓" : "-");
        });
        
        hasReceiptColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("✓".equals(item)) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #6b7280;");
                    }
                }
            }
        });
    }
    
    private void setupTemplateComboBox() {
        templateComboBox.setItems(FXCollections.observableArrayList(
            "DEFAULT",
            "DETAILED",
            "SIMPLE"
        ));
        templateComboBox.setValue("DEFAULT");
    }
    
    private void loadAllSales() {
        try {
            receiptService.ensureSingleReceiptPerSale();
            List<Sale> sales = salesService.getAllSales();
            salesTable.setItems(FXCollections.observableArrayList(sales));
            statusLabel.setText("إجمالي الفواتير: " + sales.size());
        } catch (Exception e) {
            logger.error("Failed to load sales", e);
            showError("خطأ", "فشل في تحميل الفواتير");
        }
    }
    
    private void updateSelectionState() {
        boolean hasSelection = selectedSale != null;
        receiptDetailsBox.setDisable(!hasSelection);
        createButton.setDisable(!hasSelection);
        
        if (hasSelection) {
            boolean hasReceipt = receiptService.hasReceiptForSale(selectedSale.getId());
            if (hasReceipt) {
                statusLabel.setText("⚠️ هذه الفاتورة لديها إيصال بالفعل - سيتم إنشاء إيصال جديد");
                statusLabel.setStyle("-fx-text-fill: #f59e0b;");
            } else {
                statusLabel.setText("✓ الفاتورة جاهزة لإنشاء إيصال");
                statusLabel.setStyle("-fx-text-fill: #10b981;");
            }
        } else {
            statusLabel.setText("اختر فاتورة لإنشاء إيصال لها");
            statusLabel.setStyle("-fx-text-fill: #6b7280;");
        }
    }
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
    
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }
    
    public void setTabMode(boolean tabMode) {
        this.tabMode = tabMode;
    }
    
    @FXML
    private void handleSearchSale() {
        String searchText = saleSearchField.getText().trim().toLowerCase();
        
        try {
            List<Sale> allSales = salesService.getAllSales();
            
            if (searchText.isEmpty()) {
                salesTable.setItems(FXCollections.observableArrayList(allSales));
                statusLabel.setText("إجمالي الفواتير: " + allSales.size());
                return;
            }
            
            List<Sale> filtered = allSales.stream()
                    .filter(s -> 
                        (s.getSaleCode() != null && s.getSaleCode().toLowerCase().contains(searchText)) ||
                        (s.getCustomer() != null && s.getCustomer().getName() != null && 
                         s.getCustomer().getName().toLowerCase().contains(searchText))
                    )
                    .collect(Collectors.toList());
            
            salesTable.setItems(FXCollections.observableArrayList(filtered));
            statusLabel.setText("تم العثور على " + filtered.size() + " فاتورة");
            
        } catch (Exception e) {
            logger.error("Failed to search sales", e);
            showError("خطأ", "فشل في البحث عن الفواتير");
        }
    }
    
    @FXML
    private void handleCreateReceipt() {
        if (selectedSale == null) {
            showWarning("تنبيه", "الرجاء اختيار فاتورة أولاً");
            return;
        }
        
        try {
            String template = templateComboBox.getValue();
            String notes = receiptNotesField.getText();
            
            Receipt receipt = receiptService.generateReceipt(selectedSale.getId(), template, "System");
            
            if (notes != null && !notes.trim().isEmpty()) {
                receipt.setNotes(notes);
            }
            
            showSuccess("تم بنجاح", "تم إنشاء الإيصال رقم: " + receipt.getReceiptNumber());
            
            // Print if checkbox is selected
            if (printAfterCreateCheckBox.isSelected() && receipt.getFilePath() != null) {
                File pdfFile = new File(receipt.getFilePath());
                if (pdfFile.exists()) {
                    if (mainApp != null) {
                        mainApp.showPdfPreview(pdfFile);
                    } else if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                    }
                }
            }
            
            // Refresh table
            salesTable.refresh();
            updateSelectionState();
            
        } catch (Exception e) {
            logger.error("Failed to create receipt", e);
            showError("خطأ", "فشل في إنشاء الإيصال: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleClose() {
        if (tabMode) {
            com.hisabx.util.TabManager.getInstance().closeTab("create-receipt");
        } else if (dialogStage != null) {
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
    
    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
