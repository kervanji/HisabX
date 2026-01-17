package com.hisabx.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
public class Voucher {
    
    public enum VoucherType {
        RECEIPT,  // سند قبض - دخول المال
        PAYMENT   // سند دفع - خروج المال
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "voucher_number", nullable = false, unique = true)
    private String voucherNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;
    
    @Column(name = "voucher_date")
    private LocalDateTime voucherDate;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "exchange_rate")
    private Double exchangeRate;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    @Column(name = "account_name")
    private String accountName;
    
    @Column(name = "cash_account")
    private String cashAccount;
    
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    @Column(name = "discount_percentage")
    private Double discountPercentage;
    
    @Column(name = "discount_amount")
    private Double discountAmount;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "processed_by")
    private String processedBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Voucher() {
        this.voucherDate = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currency = "دينار";
        this.exchangeRate = 1.0;
        this.discountPercentage = 0.0;
        this.discountAmount = 0.0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVoucherNumber() {
        return voucherNumber;
    }

    public void setVoucherNumber(String voucherNumber) {
        this.voucherNumber = voucherNumber;
    }

    public VoucherType getVoucherType() {
        return voucherType;
    }

    public void setVoucherType(VoucherType voucherType) {
        this.voucherType = voucherType;
    }

    public LocalDateTime getVoucherDate() {
        return voucherDate;
    }

    public void setVoucherDate(LocalDateTime voucherDate) {
        this.voucherDate = voucherDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(Double exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getCashAccount() {
        return cashAccount;
    }

    public void setCashAccount(String cashAccount) {
        this.cashAccount = cashAccount;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(Double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public Double getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Double discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Helper method to get final amount after discount
    public Double getFinalAmount() {
        if (amount == null) return 0.0;
        double discount = discountAmount != null ? discountAmount : 0.0;
        return amount - discount;
    }
    
    // Helper method to get voucher type in Arabic
    public String getVoucherTypeArabic() {
        return voucherType == VoucherType.RECEIPT ? "سند قبض" : "سند دفع";
    }
}
