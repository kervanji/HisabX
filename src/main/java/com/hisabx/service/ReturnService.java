package com.hisabx.service;

import com.hisabx.database.Repository.SaleReturnRepository;
import com.hisabx.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReturnService {
    private static final Logger logger = LoggerFactory.getLogger(ReturnService.class);

    private final SaleReturnRepository returnRepository;
    private final InventoryService inventoryService;

    public ReturnService() {
        this.returnRepository = new SaleReturnRepository();
        this.inventoryService = new InventoryService();
    }

    public SaleReturn createReturn(Sale sale, List<ReturnItem> items, String reason, String processedBy) {
        try {
            SaleReturn saleReturn = new SaleReturn();
            saleReturn.setReturnCode(returnRepository.generateReturnCode());
            saleReturn.setSale(sale);
            saleReturn.setCustomer(sale.getCustomer());
            saleReturn.setReturnDate(LocalDateTime.now());
            saleReturn.setReturnReason(reason);
            saleReturn.setProcessedBy(processedBy);
            saleReturn.setReturnStatus("COMPLETED");

            double totalReturnAmount = 0.0;
            List<ReturnItem> returnItems = new ArrayList<>();

            for (ReturnItem item : items) {
                item.setSaleReturn(saleReturn);
                item.setTotalPrice(item.getQuantity() * item.getUnitPrice());
                totalReturnAmount += item.getTotalPrice();
                returnItems.add(item);

                // Update inventory - add returned items back to stock
                if ("GOOD".equals(item.getConditionStatus())) {
                    inventoryService.addStock(item.getProduct().getId(), item.getQuantity());
                }
            }

            saleReturn.setTotalReturnAmount(totalReturnAmount);
            saleReturn.setReturnItems(returnItems);

            SaleReturn savedReturn = returnRepository.save(saleReturn);
            logger.info("Created return: {} with amount: {}", savedReturn.getReturnCode(), totalReturnAmount);
            return savedReturn;
        } catch (Exception e) {
            logger.error("Failed to create return", e);
            throw new RuntimeException("Failed to create return: " + e.getMessage(), e);
        }
    }

    public List<SaleReturn> getAllReturns() {
        return returnRepository.findAllWithDetails();
    }

    public List<SaleReturn> getReturnsBySale(Long saleId) {
        return returnRepository.findBySaleId(saleId);
    }

    public List<SaleReturn> getReturnsByCustomer(Long customerId) {
        return returnRepository.findByCustomerId(customerId);
    }

    public Double getTotalReturnsByCustomerAndProject(Long customerId, String projectLocation) {
        return returnRepository.getTotalReturnsByCustomerAndProject(customerId, projectLocation);
    }

    public SaleReturn getReturnById(Long id) {
        return returnRepository.findById(id).orElse(null);
    }

    public void updateReturnStatus(Long returnId, String status) {
        SaleReturn saleReturn = returnRepository.findById(returnId).orElse(null);
        if (saleReturn != null) {
            saleReturn.setReturnStatus(status);
            saleReturn.setUpdatedAt(LocalDateTime.now());
            returnRepository.save(saleReturn);
        }
    }

    public void deleteReturn(Long returnId) {
        SaleReturn saleReturn = returnRepository.findById(returnId).orElse(null);
        if (saleReturn != null) {
            returnRepository.delete(saleReturn);
        }
    }
}
