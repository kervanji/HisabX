package com.hisabx.model;

/**
 * نوع السند
 * RECEIPT - سند قبض (دخول أموال)
 * PAYMENT - سند دفع (خروج أموال)
 */
public enum VoucherType {
    RECEIPT("سند قبض"),    // قبض - دخول أموال
    PAYMENT("سند دفع");    // دفع - خروج أموال
    
    private final String arabicName;
    
    VoucherType(String arabicName) {
        this.arabicName = arabicName;
    }
    
    public String getArabicName() {
        return arabicName;
    }
}
