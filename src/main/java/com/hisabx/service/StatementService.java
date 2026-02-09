package com.hisabx.service;

import com.hisabx.model.*;
import com.hisabx.model.dto.StatementItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StatementService {
    private static final Logger logger = LoggerFactory.getLogger(StatementService.class);

    // Dependencies
    private final SalesService salesService;
    private final VoucherService voucherService;
    private final ReturnService returnService;
    private final CustomerService customerService;

    public StatementService() {
        this.salesService = new SalesService();
        this.voucherService = new VoucherService();
        this.returnService = new ReturnService();
        this.customerService = new CustomerService();
    }

    /**
     * Generate statement of account for a specific customer.
     * 
     * @param customerId      Customer ID
     * @param projectLocation Optional filter by project location
     * @param from            Start date (inclusive)
     * @param to              End date (inclusive)
     * @return List of sorted statement items with calculated balance
     */
    /**
     * Generate statement of account for a specific customer.
     * 
     * @param customerId      Customer ID
     * @param projectLocation Optional filter by project location
     * @param currency        Filter by currency (required)
     * @param from            Start date (inclusive)
     * @param to              End date (inclusive)
     * @return List of sorted statement items with calculated balance
     */
    public List<StatementItem> getStatement(Long customerId, String projectLocation, String currency,
            LocalDateTime from, LocalDateTime to) {
        List<StatementItem> items = new ArrayList<>();

        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("العملة مطلوبة");
        }

        // Fetch Sales
        List<Sale> sales = salesService.getSalesByCustomerId(customerId);
        for (Sale sale : sales) {
            if (projectLocation != null && !projectLocation.isEmpty()
                    && !projectLocation.equals(sale.getProjectLocation())) {
                continue;
            }
            if (!currency.equals(sale.getCurrency())) {
                continue;
            }

            if (sale.getFinalAmount() > 0) {
                items.add(new StatementItem(
                        sale.getSaleDate(),
                        "فاتورة مبيع",
                        sale.getSaleCode(),
                        sale.getNotes(),
                        sale.getFinalAmount(), // Debit (We sold -> they owe us)
                        0.0,
                        sale.getCurrency(),
                        sale));
            }
        }

        // Fetch Vouchers (Receipts & Payments)
        List<Voucher> vouchers = voucherService.getVouchersByCustomer(customerId);
        for (Voucher voucher : vouchers) {
            if (projectLocation != null && !projectLocation.isEmpty()
                    && !projectLocation.equals(voucher.getProjectName())) {
                continue;
            }
            if (!currency.equals(voucher.getCurrency())) {
                continue;
            }

            if (!Boolean.TRUE.equals(voucher.getIsCancelled())) {
                if (voucher.getVoucherType() == VoucherType.RECEIPT) {
                    items.add(new StatementItem(
                            voucher.getVoucherDate(),
                            "سند قبض",
                            voucher.getVoucherNumber(),
                            voucher.getDescription(),
                            0.0,
                            voucher.getAmount(), // Credit
                            voucher.getCurrency(),
                            voucher));
                } else if (voucher.getVoucherType() == VoucherType.PAYMENT) {
                    items.add(new StatementItem(
                            voucher.getVoucherDate(),
                            "سند الدفع",
                            voucher.getVoucherNumber(),
                            voucher.getDescription(),
                            voucher.getAmount(), // Debit
                            0.0,
                            voucher.getCurrency(),
                            voucher));
                }
            }
        }

        // Fetch Returns
        List<SaleReturn> returns = returnService.getReturnsByCustomer(customerId);
        for (SaleReturn ret : returns) {
            if (projectLocation != null && !projectLocation.isEmpty()) {
                if (ret.getSale() == null || !projectLocation.equals(ret.getSale().getProjectLocation())) {
                    continue;
                }
            }

            String retCurrency = ret.getSale() != null ? ret.getSale().getCurrency() : "دينار";
            if (!currency.equals(retCurrency)) {
                continue;
            }

            items.add(new StatementItem(
                    ret.getReturnDate(),
                    "مرتجع مبيعات",
                    ret.getReturnCode(),
                    ret.getReturnReason(),
                    0.0,
                    ret.getTotalReturnAmount(), // Credit
                    retCurrency,
                    ret));
        }

        // Sort by Date
        items.sort(Comparator.comparing(StatementItem::getDate));

        // Calculate Running Balance
        double balance = 0.0;
        List<StatementItem> allItemsWithBalance = new ArrayList<>();

        for (StatementItem item : items) {
            double debit = item.getDebit() != null ? item.getDebit() : 0.0;
            double credit = item.getCredit() != null ? item.getCredit() : 0.0;

            // Debit (They owe us) -> Increases Debt (positive balance)
            // Credit (They paid) -> Decreases Debt
            balance = balance + debit - credit;
            item.setBalance(balance);
            allItemsWithBalance.add(item);
        }

        // Filter by Date Range
        List<StatementItem> result = new ArrayList<>();

        // If start date is provided, add Opening Balance row
        if (from != null) {
            double openingBal = 0.0;
            // Find the balance right before 'from' date
            for (StatementItem item : allItemsWithBalance) {
                if (item.getDate().isBefore(from)) {
                    openingBal = item.getBalance();
                } else {
                    break;
                }
            }

            StatementItem opening = new StatementItem(
                    from.minusSeconds(1),
                    "رصيد سابق",
                    "-",
                    "رصيد افتتاحي",
                    0.0,
                    0.0,
                    currency,
                    null);
            opening.setBalance(openingBal);
            result.add(opening);
        }

        for (StatementItem item : allItemsWithBalance) {
            boolean isBefore = from != null && item.getDate().isBefore(from);
            boolean isAfter = to != null && item.getDate().isAfter(to);

            if (!isBefore && !isAfter) {
                result.add(item);
            }
        }

        return result;
    }
}
