package com.hisabx.controller;

import com.hisabx.model.*;
import com.hisabx.service.CustomerService;
import com.hisabx.service.ReturnService;
import com.hisabx.service.SalesService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReturnController {
    private static final Logger logger = LoggerFactory.getLogger(ReturnController.class);
    private static final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Sale> saleComboBox;
    @FXML private Label projectLabel;
    @FXML private TableView<ReturnableItem> saleItemsTable;
    @FXML private TableColumn<ReturnableItem, Boolean> selectColumn;
    @FXML private TableColumn<ReturnableItem, String> productColumn;
    @FXML private TableColumn<ReturnableItem, Integer> soldQtyColumn;
    @FXML private TableColumn<ReturnableItem, Integer> returnedQtyColumn;
    @FXML private TableColumn<ReturnableItem, Integer> availableQtyColumn;
    @FXML private TableColumn<ReturnableItem, Integer> returnQtyColumn;
    @FXML private TableColumn<ReturnableItem, Double> unitPriceColumn;
    @FXML private TableColumn<ReturnableItem, Double> totalColumn;
    @FXML private ComboBox<String> reasonComboBox;
    @FXML private TextArea notesArea;
    @FXML private Label totalReturnLabel;
    @FXML private ComboBox<String> conditionComboBox;

    private final CustomerService customerService = new CustomerService();
    private final SalesService salesService = new SalesService();
    private final ReturnService returnService = new ReturnService();
    
    private Stage dialogStage;
    private ObservableList<ReturnableItem> returnableItems = FXCollections.observableArrayList();
    private Map<Long, Integer> previousReturns = new HashMap<>();

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void initialize() {
        setupCustomerComboBox();
        setupSaleComboBox();
        setupTable();
        setupDefaults();
    }

    private void setupCustomerComboBox() {
        customerComboBox.setItems(FXCollections.observableArrayList(customerService.getAllCustomers()));
        customerComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        customerComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
    }

    private void setupSaleComboBox() {
        saleComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Sale item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getSaleCode());
            }
        });
        saleComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Sale item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getSaleCode());
            }
        });
    }

    private void setupTable() {
        selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectColumn.setCellFactory(col -> new CheckBoxTableCell<>());
        
        productColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        soldQtyColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getSoldQuantity()).asObject());
        returnedQtyColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getReturnedQuantity()).asObject());
        availableQtyColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getAvailableQuantity()).asObject());
        unitPriceColumn.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getUnitPrice()).asObject());
        
        // Return quantity column with editable spinner
        returnQtyColumn.setCellValueFactory(data -> data.getValue().returnQuantityProperty().asObject());
        returnQtyColumn.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Integer> spinner = new Spinner<>(0, 9999, 0);
            {
                spinner.setEditable(true);
                spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                    ReturnableItem item = getTableRow().getItem();
                    if (item != null && newVal != null) {
                        int max = item.getAvailableQuantity();
                        if (newVal > max) {
                            spinner.getValueFactory().setValue(max);
                        } else {
                            item.setReturnQuantity(newVal);
                            updateTotalReturn();
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReturnableItem returnableItem = getTableRow().getItem();
                    if (returnableItem != null) {
                        spinner.getValueFactory().setValue(returnableItem.getReturnQuantity());
                        ((SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory())
                                .setMax(returnableItem.getAvailableQuantity());
                    }
                    setGraphic(spinner);
                }
            }
        });

        totalColumn.setCellValueFactory(data -> {
            double total = data.getValue().getReturnQuantity() * data.getValue().getUnitPrice();
            return new SimpleDoubleProperty(total).asObject();
        });

        saleItemsTable.setItems(returnableItems);
        saleItemsTable.setEditable(true);
    }

    private void setupDefaults() {
        reasonComboBox.setValue("خطأ في الطلب");
        conditionComboBox.setValue("بحالة جيدة");
    }

    @FXML
    private void handleCustomerChange() {
        Customer selected = customerComboBox.getValue();
        if (selected != null) {
            List<Sale> customerSales = salesService.getSalesByCustomerId(selected.getId());
            saleComboBox.setItems(FXCollections.observableArrayList(customerSales));
            saleComboBox.setValue(null);
            returnableItems.clear();
            projectLabel.setText("-");
        }
    }

    @FXML
    private void handleSaleChange() {
        Sale selected = saleComboBox.getValue();
        if (selected != null) {
            projectLabel.setText(selected.getProjectLocation() != null ? selected.getProjectLocation() : "-");
            loadSaleItems(selected);
        }
    }

    private void loadSaleItems(Sale sale) {
        returnableItems.clear();
        previousReturns.clear();

        // Get previous returns for this sale
        List<SaleReturn> existingReturns = returnService.getReturnsBySale(sale.getId());
        for (SaleReturn ret : existingReturns) {
            if (ret.getReturnItems() != null) {
                for (ReturnItem ri : ret.getReturnItems()) {
                    if (ri.getProduct() == null) {
                        continue;
                    }
                    Long productId = ri.getProduct().getId();
                    int quantity = ri.getQuantity() != null ? ri.getQuantity() : 0;
                    previousReturns.merge(productId, quantity, (existing, addition) -> existing + addition);
                }
            }
        }

        // Create returnable items from sale items
        if (sale.getSaleItems() != null) {
            for (SaleItem item : sale.getSaleItems()) {
                int returned = previousReturns.getOrDefault(item.getProduct().getId(), 0);
                int available = item.getQuantity() - returned;
                if (available > 0) {
                    ReturnableItem ri = new ReturnableItem(
                            item,
                            item.getProduct().getName(),
                            item.getQuantity(),
                            returned,
                            available,
                            item.getUnitPrice()
                    );
                    returnableItems.add(ri);
                }
            }
        }

        updateTotalReturn();
    }

    private void updateTotalReturn() {
        double total = returnableItems.stream()
                .mapToDouble(item -> item.getReturnQuantity() * item.getUnitPrice())
                .sum();
        totalReturnLabel.setText(currencyFormat.format(total) + " د.ع");
    }

    @FXML
    private void handleConfirmReturn() {
        Sale sale = saleComboBox.getValue();
        if (sale == null) {
            showError("خطأ", "الرجاء اختيار فاتورة");
            return;
        }

        List<ReturnItem> itemsToReturn = new ArrayList<>();
        for (ReturnableItem ri : returnableItems) {
            if (ri.getReturnQuantity() > 0) {
                ReturnItem returnItem = new ReturnItem();
                returnItem.setProduct(ri.getSaleItem().getProduct());
                returnItem.setOriginalSaleItem(ri.getSaleItem());
                returnItem.setQuantity(ri.getReturnQuantity());
                returnItem.setUnitPrice(ri.getUnitPrice());
                returnItem.setConditionStatus(getConditionCode(conditionComboBox.getValue()));
                returnItem.setReturnReason(reasonComboBox.getValue());
                itemsToReturn.add(returnItem);
            }
        }

        if (itemsToReturn.isEmpty()) {
            showError("خطأ", "الرجاء تحديد كمية للإرجاع");
            return;
        }

        try {
            String reason = reasonComboBox.getValue();
            if (notesArea.getText() != null && !notesArea.getText().trim().isEmpty()) {
                reason += " - " + notesArea.getText().trim();
            }

            SaleReturn savedReturn = returnService.createReturn(sale, itemsToReturn, reason, "System");
            
            showSuccess("تم بنجاح", 
                    "تم إنشاء مرتجع بنجاح\n" +
                    "رقم المرتجع: " + savedReturn.getReturnCode() + "\n" +
                    "المبلغ المرتجع: " + currencyFormat.format(savedReturn.getTotalReturnAmount()) + " د.ع");

            // Reload sale items to show updated available quantities
            loadSaleItems(sale);

        } catch (Exception e) {
            logger.error("Failed to create return", e);
            showError("خطأ", "فشل في إنشاء المرتجع: " + e.getMessage());
        }
    }

    private String getConditionCode(String condition) {
        if (condition == null) return "GOOD";
        return switch (condition) {
            case "بحالة جيدة" -> "GOOD";
            case "تالف" -> "DAMAGED";
            case "معيب" -> "DEFECTIVE";
            default -> "GOOD";
        };
    }

    @FXML
    private void handlePrintReturnReceipt() {
        Sale sale = saleComboBox.getValue();
        if (sale == null) {
            showError("خطأ", "الرجاء اختيار فاتورة أولاً");
            return;
        }
        
        List<SaleReturn> returns = returnService.getReturnsBySale(sale.getId());
        if (returns.isEmpty()) {
            showInfo("معلومة", "لا توجد مرتجعات لهذه الفاتورة");
            return;
        }
        
        SaleReturn lastReturn = returns.get(0);
        StringBuilder receipt = new StringBuilder();
        receipt.append("=== إيصال إرجاع ===\n\n");
        receipt.append("رقم المرتجع: ").append(lastReturn.getReturnCode()).append("\n");
        receipt.append("رقم الفاتورة: ").append(sale.getSaleCode()).append("\n");
        receipt.append("العميل: ").append(lastReturn.getCustomer().getName()).append("\n");
        receipt.append("التاريخ: ").append(lastReturn.getReturnDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        
        receipt.append("--- المواد المرتجعة ---\n");
        if (lastReturn.getReturnItems() != null) {
            for (ReturnItem item : lastReturn.getReturnItems()) {
                receipt.append("• ").append(item.getProduct().getName())
                        .append(" × ").append(item.getQuantity())
                        .append(" = ").append(currencyFormat.format(item.getTotalPrice())).append(" د.ع\n");
            }
        }
        
        receipt.append("\nالمبلغ المرتجع: ").append(currencyFormat.format(lastReturn.getTotalReturnAmount())).append(" د.ع\n");
        receipt.append("السبب: ").append(lastReturn.getReturnReason()).append("\n");
        
        showInfo("إيصال الإرجاع", receipt.toString());
    }

    @FXML
    private void handleViewReturns() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setLocation(getClass().getResource("/views/ReturnList.fxml"));
            javafx.scene.Parent root = loader.load();
            
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("سجل المرتجعات");
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            if (dialogStage != null) {
                stage.initOwner(dialogStage);
            }
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to open returns list", e);
            showError("خطأ", "فشل في فتح سجل المرتجعات");
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
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

    // Inner class to represent a returnable item
    public static class ReturnableItem {
        private final SaleItem saleItem;
        private final String productName;
        private final int soldQuantity;
        private final int returnedQuantity;
        private final int availableQuantity;
        private final double unitPrice;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleIntegerProperty returnQuantity = new SimpleIntegerProperty(0);

        public ReturnableItem(SaleItem saleItem, String productName, int soldQuantity, 
                              int returnedQuantity, int availableQuantity, double unitPrice) {
            this.saleItem = saleItem;
            this.productName = productName;
            this.soldQuantity = soldQuantity;
            this.returnedQuantity = returnedQuantity;
            this.availableQuantity = availableQuantity;
            this.unitPrice = unitPrice;
        }

        public SaleItem getSaleItem() { return saleItem; }
        public String getProductName() { return productName; }
        public int getSoldQuantity() { return soldQuantity; }
        public int getReturnedQuantity() { return returnedQuantity; }
        public int getAvailableQuantity() { return availableQuantity; }
        public double getUnitPrice() { return unitPrice; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        
        public int getReturnQuantity() { return returnQuantity.get(); }
        public void setReturnQuantity(int value) { returnQuantity.set(value); }
        public SimpleIntegerProperty returnQuantityProperty() { return returnQuantity; }
    }
}
