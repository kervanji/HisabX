package com.hisabx.service;

import com.hisabx.database.Repository.VoucherRepository;
import com.hisabx.database.Repository.CustomerRepository;
import com.hisabx.model.Customer;
import com.hisabx.model.Voucher;
import com.hisabx.model.Voucher.VoucherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class VoucherService {
    private static final Logger logger = LoggerFactory.getLogger(VoucherService.class);
    private final VoucherRepository voucherRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    
    public VoucherService() {
        this.voucherRepository = new VoucherRepository();
        this.customerRepository = new CustomerRepository();
        this.customerService = new CustomerService();
    }
    
    public Voucher createReceiptVoucher(Voucher voucher) {
        logger.info("Creating receipt voucher for customer: {}", 
            voucher.getCustomer() != null ? voucher.getCustomer().getName() : "N/A");
        
        voucher.setVoucherType(VoucherType.RECEIPT);
        voucher.setVoucherNumber(voucherRepository.generateVoucherNumber(VoucherType.RECEIPT));
        
        validateVoucher(voucher);
        
        Voucher savedVoucher = voucherRepository.save(voucher);
        
        if (voucher.getCustomer() != null) {
            // Update balance based on currency (receipt = money coming in, reduce customer debt)
            customerService.updateCustomerBalanceByCurrency(
                voucher.getCustomer().getId(), 
                -voucher.getFinalAmount(),
                voucher.getCurrency()
            );
        }
        
        logger.info("Receipt voucher created: {}", savedVoucher.getVoucherNumber());
        return savedVoucher;
    }
    
    public Voucher createPaymentVoucher(Voucher voucher) {
        logger.info("Creating payment voucher for customer: {}", 
            voucher.getCustomer() != null ? voucher.getCustomer().getName() : "N/A");
        
        voucher.setVoucherType(VoucherType.PAYMENT);
        voucher.setVoucherNumber(voucherRepository.generateVoucherNumber(VoucherType.PAYMENT));
        
        validateVoucher(voucher);
        
        Voucher savedVoucher = voucherRepository.save(voucher);
        
        if (voucher.getCustomer() != null) {
            // Update balance based on currency (payment = money going out, increase customer credit)
            customerService.updateCustomerBalanceByCurrency(
                voucher.getCustomer().getId(), 
                voucher.getFinalAmount(),
                voucher.getCurrency()
            );
        }
        
        logger.info("Payment voucher created: {}", savedVoucher.getVoucherNumber());
        return savedVoucher;
    }
    
    public Voucher updateVoucher(Voucher voucher) {
        logger.info("Updating voucher: {}", voucher.getVoucherNumber());
        validateVoucher(voucher);
        return voucherRepository.save(voucher);
    }
    
    public Optional<Voucher> getVoucherById(Long id) {
        return voucherRepository.findById(id);
    }
    
    public Optional<Voucher> getVoucherByNumber(String voucherNumber) {
        return voucherRepository.findByVoucherNumber(voucherNumber);
    }
    
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAllWithDetails();
    }
    
    public List<Voucher> getReceiptVouchers() {
        return voucherRepository.findByType(VoucherType.RECEIPT);
    }
    
    public List<Voucher> getPaymentVouchers() {
        return voucherRepository.findByType(VoucherType.PAYMENT);
    }
    
    public List<Voucher> getVouchersByCustomer(Long customerId) {
        return voucherRepository.findByCustomerId(customerId);
    }
    
    public List<Voucher> getVouchersByDateRange(LocalDateTime from, LocalDateTime to) {
        return voucherRepository.findByDateRange(from, to);
    }
    
    public void deleteVoucher(Long id) {
        logger.info("Deleting voucher: {}", id);
        voucherRepository.deleteById(id);
    }
    
    public long getNextReceiptVoucherNumber() {
        return voucherRepository.getNextVoucherNumber(VoucherType.RECEIPT);
    }
    
    public long getNextPaymentVoucherNumber() {
        return voucherRepository.getNextVoucherNumber(VoucherType.PAYMENT);
    }
    
    private void validateVoucher(Voucher voucher) {
        if (voucher.getAmount() == null || voucher.getAmount() <= 0) {
            throw new IllegalArgumentException("المبلغ يجب أن يكون أكبر من صفر");
        }
        
        if (voucher.getVoucherType() == null) {
            throw new IllegalArgumentException("نوع السند مطلوب");
        }
    }
    
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    
    public List<Customer> searchCustomers(String name) {
        return customerRepository.findByNameContaining(name);
    }
}
