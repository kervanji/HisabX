package com.hisabx.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.hisabx.model.Customer;
import com.hisabx.model.Product;
import com.hisabx.model.Sale;
import com.hisabx.model.Receipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class FirebaseSyncService {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseSyncService.class);
    private final FirebaseService firebaseService;
    private final Firestore firestore;
    
    public FirebaseSyncService() {
        this.firebaseService = FirebaseService.getInstance();
        this.firestore = firebaseService.getFirestore();
    }
    
    // Customer sync methods
    public void syncCustomer(Customer customer) {
        try {
            DocumentReference docRef = firestore
                    .collection("customers")
                    .document(Objects.requireNonNull(customer.getCustomerCode(), "customerCode"));
            docRef.set(Objects.requireNonNull(convertCustomerToMap(customer), "customer data")).get();
            logger.info("Customer synced to Firebase: {}", customer.getCustomerCode());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to sync customer to Firebase", e);
            throw new RuntimeException("Customer sync failed", e);
        }
    }
    
    public List<Customer> getCustomersFromFirebase() {
        try {
            CollectionReference customers = firestore.collection("customers");
            ApiFuture<QuerySnapshot> future = customers.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            
            List<Customer> customerList = new ArrayList<>();
            for (QueryDocumentSnapshot doc : documents) {
                customerList.add(convertMapToCustomer(doc.getData()));
            }
            
            logger.info("Retrieved {} customers from Firebase", customerList.size());
            return customerList;
            
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve customers from Firebase", e);
            throw new RuntimeException("Failed to retrieve customers", e);
        }
    }
    
    // Product sync methods
    public void syncProduct(Product product) {
        try {
            DocumentReference docRef = firestore
                    .collection("products")
                    .document(Objects.requireNonNull(product.getProductCode(), "productCode"));
            docRef.set(Objects.requireNonNull(convertProductToMap(product), "product data")).get();
            logger.info("Product synced to Firebase: {}", product.getProductCode());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to sync product to Firebase", e);
            throw new RuntimeException("Product sync failed", e);
        }
    }
    
    public List<Product> getProductsFromFirebase() {
        try {
            CollectionReference products = firestore.collection("products");
            ApiFuture<QuerySnapshot> future = products.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            
            List<Product> productList = new ArrayList<>();
            for (QueryDocumentSnapshot doc : documents) {
                productList.add(convertMapToProduct(doc.getData()));
            }
            
            logger.info("Retrieved {} products from Firebase", productList.size());
            return productList;
            
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve products from Firebase", e);
            throw new RuntimeException("Failed to retrieve products", e);
        }
    }
    
    // Sale sync methods
    public void syncSale(Sale sale) {
        try {
            DocumentReference docRef = firestore
                    .collection("sales")
                    .document(Objects.requireNonNull(sale.getSaleCode(), "saleCode"));
            docRef.set(Objects.requireNonNull(convertSaleToMap(sale), "sale data")).get();
            logger.info("Sale synced to Firebase: {}", sale.getSaleCode());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to sync sale to Firebase", e);
            throw new RuntimeException("Sale sync failed", e);
        }
    }
    
    // Receipt sync methods
    public void syncReceipt(Receipt receipt) {
        try {
            DocumentReference docRef = firestore
                    .collection("receipts")
                    .document(Objects.requireNonNull(receipt.getReceiptNumber(), "receiptNumber"));
            docRef.set(Objects.requireNonNull(convertReceiptToMap(receipt), "receipt data")).get();
            logger.info("Receipt synced to Firebase: {}", receipt.getReceiptNumber());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to sync receipt to Firebase", e);
            throw new RuntimeException("Receipt sync failed", e);
        }
    }
    
    // Full sync methods
    public void syncAllData(List<Customer> customers, List<Product> products, List<Sale> sales, List<Receipt> receipts) {
        logger.info("Starting full data sync to Firebase");
        
        // Sync customers
        for (Customer customer : customers) {
            syncCustomer(customer);
        }
        
        // Sync products
        for (Product product : products) {
            syncProduct(product);
        }
        
        // Sync sales
        for (Sale sale : sales) {
            syncSale(sale);
        }
        
        // Sync receipts
        for (Receipt receipt : receipts) {
            syncReceipt(receipt);
        }
        
        logger.info("Full data sync completed successfully");
    }
    
    // Data conversion methods
    private Map<String, Object> convertCustomerToMap(Customer customer) {
        Map<String, Object> map = new HashMap<>();
        map.put("customerCode", customer.getCustomerCode());
        map.put("name", customer.getName());
        map.put("phoneNumber", customer.getPhoneNumber());
        map.put("address", customer.getAddress());
        map.put("projectLocation", customer.getProjectLocation());
        map.put("email", customer.getEmail());
        map.put("taxId", customer.getTaxId());
        map.put("creditLimit", customer.getCreditLimit());
        map.put("currentBalance", customer.getCurrentBalance());
        map.put("createdAt", customer.getCreatedAt().toString());
        map.put("updatedAt", customer.getUpdatedAt().toString());
        return map;
    }
    
    private Customer convertMapToCustomer(Map<String, Object> map) {
        Customer customer = new Customer();
        customer.setCustomerCode((String) map.get("customerCode"));
        customer.setName((String) map.get("name"));
        customer.setPhoneNumber((String) map.get("phoneNumber"));
        customer.setAddress((String) map.get("address"));
        customer.setProjectLocation((String) map.get("projectLocation"));
        customer.setEmail((String) map.get("email"));
        customer.setTaxId((String) map.get("taxId"));
        customer.setCreditLimit((Double) map.get("creditLimit"));
        customer.setCurrentBalance((Double) map.get("currentBalance"));
        // Note: Date conversion would need proper handling
        return customer;
    }
    
    private Map<String, Object> convertProductToMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("productCode", product.getProductCode());
        map.put("name", product.getName());
        map.put("description", product.getDescription());
        map.put("category", product.getCategory());
        map.put("unitPrice", product.getUnitPrice());
        map.put("costPrice", product.getCostPrice());
        map.put("quantityInStock", product.getQuantityInStock());
        map.put("minimumStock", product.getMinimumStock());
        map.put("maximumStock", product.getMaximumStock());
        map.put("unitOfMeasure", product.getUnitOfMeasure());
        map.put("barcode", product.getBarcode());
        map.put("isActive", product.getIsActive());
        map.put("createdAt", product.getCreatedAt().toString());
        map.put("updatedAt", product.getUpdatedAt().toString());
        return map;
    }
    
    private Product convertMapToProduct(Map<String, Object> map) {
        Product product = new Product();
        product.setProductCode((String) map.get("productCode"));
        product.setName((String) map.get("name"));
        product.setDescription((String) map.get("description"));
        product.setCategory((String) map.get("category"));
        product.setUnitPrice((Double) map.get("unitPrice"));
        product.setCostPrice((Double) map.get("costPrice"));
        product.setQuantityInStock(((Number) map.get("quantityInStock")).doubleValue());
        product.setMinimumStock(((Number) map.get("minimumStock")).doubleValue());
        product.setMaximumStock(((Number) map.get("maximumStock")).doubleValue());
        product.setUnitOfMeasure((String) map.get("unitOfMeasure"));
        product.setBarcode((String) map.get("barcode"));
        product.setIsActive((Boolean) map.get("isActive"));
        return product;
    }
    
    private Map<String, Object> convertSaleToMap(Sale sale) {
        Map<String, Object> map = new HashMap<>();
        map.put("saleCode", sale.getSaleCode());
        map.put("customerId", sale.getCustomer().getId());
        map.put("saleDate", sale.getSaleDate().toString());
        map.put("totalAmount", sale.getTotalAmount());
        map.put("discountAmount", sale.getDiscountAmount());
        map.put("taxAmount", sale.getTaxAmount());
        map.put("finalAmount", sale.getFinalAmount());
        map.put("paymentMethod", sale.getPaymentMethod());
        map.put("paymentStatus", sale.getPaymentStatus());
        map.put("notes", sale.getNotes());
        map.put("createdBy", sale.getCreatedBy());
        map.put("createdAt", sale.getCreatedAt().toString());
        map.put("updatedAt", sale.getUpdatedAt().toString());
        return map;
    }
    
    private Map<String, Object> convertReceiptToMap(Receipt receipt) {
        Map<String, Object> map = new HashMap<>();
        map.put("receiptNumber", receipt.getReceiptNumber());
        map.put("saleId", receipt.getSale().getId());
        map.put("receiptDate", receipt.getReceiptDate().toString());
        map.put("template", receipt.getTemplate());
        map.put("filePath", receipt.getFilePath());
        map.put("isPrinted", receipt.getIsPrinted());
        map.put("printedAt", receipt.getPrintedAt() != null ? receipt.getPrintedAt().toString() : null);
        map.put("printedBy", receipt.getPrintedBy());
        map.put("notes", receipt.getNotes());
        map.put("createdAt", receipt.getCreatedAt().toString());
        map.put("updatedAt", receipt.getUpdatedAt().toString());
        return map;
    }
}
