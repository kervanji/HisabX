package com.hisabx.service;

import com.hisabx.database.Repository.CustomerRepository;
import com.hisabx.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CustomerService {
    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository customerRepository;
    
    public CustomerService() {
        this.customerRepository = new CustomerRepository();
    }
    
    public Customer createCustomer(Customer customer) {
        logger.info("Creating new customer: {}", customer.getName());
        
        // Always generate sequential customer code on create (defensive)
        customer.setCustomerCode(generateCustomerCode());
        
        // Validate customer data
        validateCustomer(customer);
        
        return customerRepository.save(customer);
    }
    
    public Customer updateCustomer(Customer customer) {
        logger.info("Updating customer: {}", customer.getId());
        
        // Validate customer data
        validateCustomer(customer);
        
        return customerRepository.save(customer);
    }
    
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    
    public Optional<Customer> getCustomerByCode(String customerCode) {
        return customerRepository.findByCustomerCode(customerCode);
    }
    
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    
    public List<Customer> searchCustomersByName(String name) {
        return customerRepository.findByNameContaining(name);
    }
    
    public void deleteCustomer(Long id) {
        logger.info("Deleting customer: {}", id);
        customerRepository.deleteById(id);
    }
    
    public void deleteCustomer(Customer customer) {
        logger.info("Deleting customer: {}", customer.getId());
        customerRepository.delete(customer);
    }
    
    private String generateCustomerCode() {
        return customerRepository.getNextCustomerCode();
    }

    public String previewNextCustomerCode() {
        return customerRepository.getNextCustomerCode();
    }
    
    private void validateCustomer(Customer customer) {
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم العميل مطلوب");
        }

        if (customer.getPhoneNumber() == null || customer.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("رقم الهاتف مطلوب");
        }

        if (!isValidPhoneNumber(customer.getPhoneNumber().trim())) {
            throw new IllegalArgumentException("رقم الهاتف غير صالح (يجب أن يبدأ بـ 07)");
        }

        if (customer.getCustomerCode() == null || customer.getCustomerCode().trim().isEmpty()) {
            throw new IllegalArgumentException("كود العميل مطلوب");
        }

        Optional<Customer> byCode = customerRepository.findByCustomerCode(customer.getCustomerCode());
        if (byCode.isPresent() && (customer.getId() == null || !byCode.get().getId().equals(customer.getId()))) {
            throw new IllegalArgumentException("كود العميل مستخدم بالفعل");
        }
    }
    
    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches("^07\\d{9}$");
    }
    
    public Customer updateCustomerBalance(Long customerId, Double amount) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setCurrentBalance(customer.getCurrentBalance() + amount);
            return customerRepository.save(customer);
        }
        throw new IllegalArgumentException("العميل غير موجود");
    }
    
    public List<Customer> getCustomersWithDebt() {
        return customerRepository.findAll().stream()
                .filter(customer -> customer.getCurrentBalance() < 0)
                .toList();
    }
    
    public List<Customer> getCustomersWithCredit() {
        return customerRepository.findAll().stream()
                .filter(customer -> customer.getCurrentBalance() > 0)
                .toList();
    }
}
