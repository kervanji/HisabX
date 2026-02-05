package com.hisabx.service;

import com.hisabx.database.Repository.ReceiptRepository;
import com.hisabx.database.Repository.SaleRepository;
import com.hisabx.database.Repository.SaleReturnRepository;
import com.hisabx.model.Customer;
import com.hisabx.model.Receipt;
import com.hisabx.model.ReturnItem;
import com.hisabx.model.Sale;
import com.hisabx.model.SaleItem;
import com.hisabx.model.SaleReturn;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class ReceiptService {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptService.class);
    private final ReceiptRepository receiptRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;

    private static final String PREF_BANNER_PATH = "receipt.banner.path";
    private static final String PREF_LAST_RECEIPT_NUMBER = "receipt.last.number";
    
    // Company information
    private static final String APP_NAME = "HisabX";
    private static final String COMPANY_WEBSITE = "Kervanjiholding.com";
    
    public ReceiptService() {
        this.receiptRepository = new ReceiptRepository();
        this.saleRepository = new SaleRepository();
        this.returnRepository = new SaleReturnRepository();
    }

    public File generateAccountStatementPdf(Customer customer,
                                            String projectLocation,
                                            LocalDate from,
                                            LocalDate to,
                                            boolean includeItems) {
        return generateAccountStatementPdf(customer, projectLocation, from, to, includeItems, null);
    }

    public File generateAccountStatementPdf(Customer customer,
                                            String projectLocation,
                                            LocalDate from,
                                            LocalDate to,
                                            boolean includeItems,
                                            String currency) {
        return generateAccountStatementPdf(customer, projectLocation, from, to, includeItems, currency, null);
    }

    public File generateAccountStatementPdf(Customer customer,
                                            String projectLocation,
                                            LocalDate from,
                                            LocalDate to,
                                            boolean includeItems,
                                            String currency,
                                            File outputFile) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("العميل غير موجود");
        }

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
        List<Sale> sales;
        List<SaleReturn> returns;
        try {
            sales = saleRepository.findForAccountStatement(customer.getId(), projectLocation, fromDt, toDt, includeItems);
            returns = returnRepository.findForAccountStatement(customer.getId(), projectLocation, fromDt, toDt);
            if (currency != null && !currency.isEmpty()) {
                String selectedCurrency = currency;
                sales = sales.stream()
                        .filter(sale -> selectedCurrency.equals(sale.getCurrency()))
                        .toList();
                returns = returns.stream()
                        .filter(ret -> ret.getSale() != null && selectedCurrency.equals(ret.getSale().getCurrency()))
                        .toList();
            }
        } catch (Exception e) {
            logger.error("Failed to load sales/returns for statement", e);
            throw e;
        }

        try {
            byte[] pdfData = generateAccountStatementPDF(customer, projectLocation, from, to, sales, returns, includeItems, currency);

            File out = outputFile;
            if (out == null) {
                File dir = new File("statements");
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String safeCustomer = customer.getName() != null ? customer.getName().replaceAll("[^\\p{L}\\p{N}]+", "_") : "customer";
                String fileName = "statement_" + safeCustomer + "_" + datePart + ".pdf";
                out = new File(dir, fileName);
            }

            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(pdfData);
            }

            return out;
        } catch (Exception e) {
            logger.error("Failed to generate account statement PDF", e);
            throw new RuntimeException("فشل في إنشاء كشف الحساب", e);
        }
    }

    private Receipt prepareReceiptForSale(Sale sale, String template, String printedBy) {
        List<Receipt> existingReceipts = receiptRepository.findBySaleId(sale.getId());
        Receipt receipt;

        if (existingReceipts.isEmpty()) {
            receipt = new Receipt();
            receipt.setReceiptNumber(generateReceiptNumber());
            receipt.setSale(sale);
        } else {
            // keep the latest receipt and delete older duplicates
            receipt = existingReceipts.get(existingReceipts.size() - 1);
            for (int i = 0; i < existingReceipts.size() - 1; i++) {
                Receipt duplicate = existingReceipts.get(i);
                deleteReceiptSafely(duplicate);
            }
        }

        receipt.setTemplate(template != null ? template : "DEFAULT");
        receipt.setPrintedBy(printedBy);
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setUpdatedAt(LocalDateTime.now());
        return receipt;
    }

    public void ensureSingleReceiptPerSale() {
        List<Long> saleIds = receiptRepository.findSaleIdsWithMultipleReceipts();
        for (Long saleId : saleIds) {
            List<Receipt> receipts = receiptRepository.findBySaleId(saleId);
            if (receipts.isEmpty()) {
                continue;
            }
            Receipt keep = receipts.get(receipts.size() - 1);
            for (int i = 0; i < receipts.size() - 1; i++) {
                Receipt duplicate = receipts.get(i);
                if (!duplicate.getId().equals(keep.getId())) {
                    deleteReceiptSafely(duplicate);
                }
            }
        }
    }

    private void deleteReceiptSafely(Receipt receipt) {
        try {
            if (receipt.getFilePath() != null) {
                java.io.File pdf = new java.io.File(receipt.getFilePath());
                if (pdf.exists()) {
                    pdf.delete();
                }
            }
            receiptRepository.delete(receipt);
        } catch (Exception e) {
            logger.warn("Failed to delete duplicate receipt {}", receipt.getId(), e);
        }
    }
    
    public Receipt generateReceipt(Long saleId, String template, String printedBy) {
        logger.info("Generating receipt for sale: {}", saleId);
        
        Optional<Sale> saleOpt = saleRepository.findByIdWithDetails(saleId);
        if (saleOpt.isEmpty()) {
            throw new IllegalArgumentException("البيع غير موجود");
        }
        
        Sale sale = saleOpt.get();
        Receipt receipt = prepareReceiptForSale(sale, template, printedBy);
        
        // Generate PDF
        try {
            byte[] pdfData = generateReceiptPDF(sale, receipt.getTemplate());
            
            // Ensure receipts directory exists
            java.io.File receiptsDir = new java.io.File("receipts");
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }
            
            // Save PDF file
            String fileName = "receipt_" + receipt.getReceiptNumber() + ".pdf";
            String filePath = "receipts/" + fileName;
            
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfData);
            }
            
            receipt.setFilePath(filePath);
            receipt.setIsPrinted(true);
            receipt.setPrintedAt(LocalDateTime.now());
            
            Receipt savedReceipt = receiptRepository.save(receipt);
            logger.info("Receipt generated successfully: {}", savedReceipt.getReceiptNumber());
            
            return savedReceipt;
            
        } catch (Exception e) {
            logger.error("Failed to generate receipt PDF", e);
            throw new RuntimeException("فشل في إنشاء الإيصال", e);
        }
    }

    public Receipt regenerateReceiptPdf(Long receiptId, String printedBy) {
        Optional<Receipt> receiptOpt = receiptRepository.findByIdWithDetails(receiptId);
        if (receiptOpt.isEmpty()) {
            throw new IllegalArgumentException("الوصل غير موجود");
        }

        Receipt receipt = receiptOpt.get();
        if (receipt.getSale() == null) {
            throw new IllegalStateException("لا توجد فاتورة مرتبطة بهذا الوصل");
        }

        try {
            byte[] pdfData = generateReceiptPDF(receipt.getSale(), receipt.getTemplate());

            java.io.File receiptsDir = new java.io.File("receipts");
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs();
            }

            String fileName = "receipt_" + receipt.getReceiptNumber() + ".pdf";
            String filePath = "receipts/" + fileName;

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfData);
            }

            receipt.setFilePath(filePath);
            receipt.setIsPrinted(true);
            receipt.setPrintedAt(LocalDateTime.now());
            receipt.setPrintedBy(printedBy);

            Receipt savedReceipt = receiptRepository.save(receipt);
            logger.info("Receipt PDF regenerated successfully: {}", savedReceipt.getReceiptNumber());
            return savedReceipt;
        } catch (Exception e) {
            logger.error("Failed to regenerate receipt PDF", e);
            throw new RuntimeException("فشل في إعادة إنشاء الوصل", e);
        }
    }
    
    private byte[] generateReceiptPDF(Sale sale, String template) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();
        
        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
        Font smallFont = new Font(baseFont, 9, Font.NORMAL);

        try {
            Image banner = loadBannerImage();
            if (banner != null) {
                float pageWidth = document.getPageSize().getWidth();
                float pageHeight = document.getPageSize().getHeight();

                banner.scaleAbsolute(pageWidth, bannerTargetHeight);
                banner.setAbsolutePosition(0f, pageHeight - bannerTargetHeight);
                writer.getDirectContent().addImage(banner);
            }
        } catch (Exception e) {
            logger.warn("Failed to add banner", e);
        }

        document.add(new Paragraph(" ", arabicFont));

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoTable.setWidths(new float[]{1.5f, 1});
        infoTable.setSpacingBefore(5f);
        infoTable.setSpacingAfter(10f);

        String customerName = sale.getCustomer() != null && sale.getCustomer().getName() != null ? sale.getCustomer().getName() : "-";
        String customerPhone = sale.getCustomer() != null ? sale.getCustomer().getPhoneNumber() : null;
        String customerAddress = sale.getCustomer() != null ? sale.getCustomer().getAddress() : null;

        PdfPCell customerCell = new PdfPCell();
        customerCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        customerCell.setPadding(8f);
        customerCell.addElement(new Phrase("اسم العميل: " + customerName, arabicFont));
        if (customerPhone != null && !customerPhone.trim().isEmpty()) {
            customerCell.addElement(new Phrase("الهاتف: " + customerPhone, arabicFont));
        }
        if (customerAddress != null && !customerAddress.trim().isEmpty()) {
            customerCell.addElement(new Phrase("العنوان: " + customerAddress, arabicFont));
        }
        if (sale.getProjectLocation() != null && !sale.getProjectLocation().trim().isEmpty()) {
            customerCell.addElement(new Phrase("موقع المشروع: " + sale.getProjectLocation(), arabicFont));
        }
        infoTable.addCell(customerCell);

        PdfPCell invoiceCell = new PdfPCell();
        invoiceCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        invoiceCell.setPadding(8f);
        invoiceCell.setBackgroundColor(new BaseColor(255, 182, 193));
        invoiceCell.addElement(new Phrase("فاتورة بيع/أجل", arabicBoldFont));
        invoiceCell.addElement(new Phrase(" ", smallFont));
        invoiceCell.addElement(new Phrase("رقم الفاتورة: " + (sale.getSaleCode() != null ? sale.getSaleCode() : "-"), arabicFont));
        invoiceCell.addElement(new Phrase(
            "التاريخ والوقت: " + (sale.getSaleDate() != null ? sale.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-"),
            arabicFont
        ));
        infoTable.addCell(invoiceCell);

        document.add(infoTable);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidths(new float[]{1f, 1f, 1f, 0.6f, 1f, 0.8f, 0.2f});
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);

        addTableHeader(table, "ت", arabicBoldFont);
        addTableHeader(table, "المادة", arabicBoldFont);
        addTableHeader(table, "العدد", arabicBoldFont);
        addTableHeader(table, "التعبئة", arabicBoldFont);
        addTableHeader(table, "السعر", arabicBoldFont);
        addTableHeader(table, "السعر بعد الخصم", arabicBoldFont);
        addTableHeader(table, "المجموع ع", arabicBoldFont);

        List<SaleItem> items = sale.getSaleItems() != null ? sale.getSaleItems() : Collections.emptyList();
        int rowNo = 1;
        for (SaleItem item : items) {
            String productName = item.getProduct() != null && item.getProduct().getName() != null ? item.getProduct().getName() : "-";
            String unitOfMeasure = item.getProduct() != null && item.getProduct().getUnitOfMeasure() != null ? item.getProduct().getUnitOfMeasure() : "-";

            table.addCell(createBodyCell(String.valueOf(rowNo), arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(productName, arabicFont, Element.ALIGN_RIGHT));
            table.addCell(createBodyCell(item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "0", arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(unitOfMeasure, arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(formatAmount(item.getUnitPrice()), arabicFont, Element.ALIGN_CENTER));
            Double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
            Double discountAmount = item.getDiscountAmount() != null ? item.getDiscountAmount() : 0.0;
            double discountPerUnit = quantity > 0 ? discountAmount / quantity : 0.0;
            double unitPriceAfterDiscount = (item.getUnitPrice() != null ? item.getUnitPrice() : 0.0) - discountPerUnit;
            table.addCell(createBodyCell(formatAmount(unitPriceAfterDiscount), arabicFont, Element.ALIGN_CENTER));
            table.addCell(createBodyCell(formatAmount(item.getTotalPrice()), arabicFont, Element.ALIGN_CENTER));
            rowNo++;
        }

        document.add(table);

        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(100);
        totalsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        totalsTable.setWidths(new float[]{1, 1});
        totalsTable.setSpacingBefore(5f);
        totalsTable.setSpacingAfter(10f);

        PdfPCell notesCell = new PdfPCell();
        notesCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        notesCell.setPadding(8f);
        notesCell.setMinimumHeight(60f);
        notesCell.addElement(new Phrase("عدد المواد: " + (sale.getSaleItems() != null ? sale.getSaleItems().size() : 0), arabicFont));
        if (sale.getNotes() != null && !sale.getNotes().trim().isEmpty()) {
            notesCell.addElement(new Phrase("ملاحظات: " + sale.getNotes(), arabicFont));
        }
        totalsTable.addCell(notesCell);

        PdfPCell summaryCell = new PdfPCell();
        summaryCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summaryCell.setPadding(8f);
        
        PdfPTable innerTotals = new PdfPTable(2);
        innerTotals.setWidthPercentage(100);
        innerTotals.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        
        String saleCurrency = sale.getCurrency() != null ? sale.getCurrency() : "دينار";
        addTotalRowBordered(innerTotals, "المجموع", formatCurrency(sale.getTotalAmount(), saleCurrency), arabicFont, arabicBoldFont);
        double itemsDiscount = items.stream()
                .mapToDouble(item -> item.getDiscountAmount() != null ? item.getDiscountAmount() : 0.0)
                .sum();
        if (itemsDiscount > 0) {
            addTotalRowBordered(innerTotals, "خصم المواد", formatCurrency(itemsDiscount, saleCurrency), arabicFont, arabicBoldFont);
        }
        if (sale.getDiscountAmount() != null && sale.getDiscountAmount() > 0) {
            addTotalRowBordered(innerTotals, "خصم إضافي", formatCurrency(sale.getDiscountAmount(), saleCurrency), arabicFont, arabicBoldFont);
        }
        addTotalRowBordered(innerTotals, "الإجمالي", formatCurrency(sale.getFinalAmount(), saleCurrency), arabicBoldFont, arabicBoldFont);
        
        Double paid = sale.getPaidAmount() != null ? sale.getPaidAmount() : 0.0;
        if (paid > 0) {
            addTotalRowBordered(innerTotals, "المدفوع", formatCurrency(paid, saleCurrency), arabicFont, arabicBoldFont);
            Double finalAmount = sale.getFinalAmount() != null ? sale.getFinalAmount() : 0.0;
            addTotalRowBordered(innerTotals, "المتبقي", formatCurrency(finalAmount - paid, saleCurrency), arabicFont, arabicBoldFont);
        }
        
        summaryCell.addElement(innerTotals);
        totalsTable.addCell(summaryCell);
        
        document.add(totalsTable);

        PdfPTable signatureTable = new PdfPTable(2);
        signatureTable.setWidthPercentage(100);
        signatureTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signatureTable.setSpacingBefore(10f);
        signatureTable.setSpacingAfter(10f);

        PdfPCell paymentCell = new PdfPCell();
        paymentCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        paymentCell.setPadding(8f);
        paymentCell.setMinimumHeight(50f);
        paymentCell.addElement(new Phrase("طريقة الدفع: " + getPaymentMethodArabic(sale.getPaymentMethod()), arabicFont));
        paymentCell.addElement(new Phrase("الحالة: " + getPaymentStatusArabic(sale.getPaymentStatus()), arabicFont));
        signatureTable.addCell(paymentCell);

        PdfPCell signCell = new PdfPCell();
        signCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signCell.setPadding(8f);
        signCell.setMinimumHeight(50f);
        signCell.addElement(new Phrase("التوقيع: ", arabicFont));
        signatureTable.addCell(signCell);

        document.add(signatureTable);

        addUnifiedFooter(document, arabicBoldFont, smallFont);
        
        document.close();
        
        return baos.toByteArray();
    }
    
    private BaseFont loadArabicBaseFont() throws DocumentException, IOException {
        String[] fontCandidates = new String[]{
            "C:\\Windows\\Fonts\\arial.ttf",
            "C:\\Windows\\Fonts\\tahoma.ttf",
            "C:\\Windows\\Fonts\\arialuni.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/Diwan Kufi.ttc,0",
            "/System/Library/Fonts/Supplemental/Damascus.ttc,0",
            "/System/Library/Fonts/Supplemental/DecoTypeNaskh.ttc,0",
            "/System/Library/Fonts/Supplemental/KufiStandardGK.ttc,0",
            "/Library/Fonts/Arial Unicode.ttf",
            "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
            "/usr/share/fonts/opentype/noto/NotoNaskhArabic-Regular.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };

        for (String fontPath : fontCandidates) {
            try {
                return BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }

        logger.warn("Arabic font not found on system. Falling back to Helvetica.");
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private Image loadBannerImage() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
            String bannerPath = prefs.get(PREF_BANNER_PATH, null);
            if (bannerPath != null && !bannerPath.trim().isEmpty()) {
                java.io.File f = new java.io.File(bannerPath);
                if (f.exists() && f.isFile()) {
                    return Image.getInstance(f.getAbsolutePath());
                }
            }

            URL logoUrl = ReceiptService.class.getResource("/templates/HisabX.png");
            if (logoUrl != null) {
                return Image.getInstance(logoUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to load banner image", e);
        }
        return null;
    }

    private byte[] generateAccountStatementPDF(Customer customer,
                                               String projectLocation,
                                               LocalDate from,
                                               LocalDate to,
                                               List<Sale> sales,
                                               List<SaleReturn> returns,
                                               boolean includeItems,
                                               String currency) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final float bannerTargetHeight = 120f;
        Document document = new Document(PageSize.A4, 30, 30, 30 + bannerTargetHeight, 30);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadArabicBaseFont();
        Font arabicFont = new Font(baseFont, 10, Font.NORMAL);
        Font arabicBoldFont = new Font(baseFont, 11, Font.BOLD);
        Font sectionTitleFont = new Font(baseFont, 12, Font.BOLD);
        Font smallFont = new Font(baseFont, 9, Font.NORMAL);

        try {
            Image banner = loadBannerImage();
            if (banner != null) {
                float pageWidth = document.getPageSize().getWidth();
                float pageHeight = document.getPageSize().getHeight();
                banner.scaleAbsolute(pageWidth, bannerTargetHeight);
                banner.setAbsolutePosition(0f, pageHeight - bannerTargetHeight);
                writer.getDirectContent().addImage(banner);
            }
        } catch (Exception e) {
            logger.warn("Failed to add banner", e);
        }

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell titleCell = new PdfPCell(new Phrase("كشف حساب", sectionTitleFont));
        titleCell.setBorder(PdfPCell.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        titleCell.setPaddingBottom(4f);
        header.addCell(titleCell);

        document.add(header);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        info.setWidths(new float[]{1.4f, 1f});
        info.setSpacingBefore(6f);
        info.setSpacingAfter(8f);

        PdfPCell left = new PdfPCell();
        left.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        left.setPadding(8f);
        left.addElement(new Phrase("اسم العميل: " + (customer.getName() != null ? customer.getName() : "-"), arabicFont));
        if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()) {
            left.addElement(new Phrase("الهاتف: " + customer.getPhoneNumber(), arabicFont));
        }
        if (projectLocation != null && !projectLocation.trim().isEmpty()) {
            left.addElement(new Phrase("المشروع: " + projectLocation, arabicFont));
        }
        info.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        right.setPadding(8f);
        String period;
        if (from != null && to != null) {
            period = from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " إلى " + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (from != null) {
            period = "من " + from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (to != null) {
            period = "إلى " + to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else {
            period = "كل الفترات";
        }
        right.addElement(new Phrase("الفترة: " + period, arabicFont));
        right.addElement(new Phrase("تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), arabicFont));
        if (currency != null && !currency.isEmpty()) {
            right.addElement(new Phrase("العملة: " + currency, arabicFont));
        }
        info.addCell(right);

        document.add(info);

        double totalFinal = 0.0;
        double totalPaid = 0.0;
        double totalReturns = 0.0;
        double totalFinalIqd = 0.0;
        double totalPaidIqd = 0.0;
        double totalReturnsIqd = 0.0;
        double totalFinalUsd = 0.0;
        double totalPaidUsd = 0.0;
        double totalReturnsUsd = 0.0;
        for (Sale s : sales) {
            double finalAmount = s.getFinalAmount() != null ? s.getFinalAmount() : 0.0;
            double paidAmount = s.getPaidAmount() != null ? s.getPaidAmount() : 0.0;
            totalFinal += finalAmount;
            totalPaid += paidAmount;
            if (currency == null || currency.isEmpty()) {
                if ("دولار".equals(s.getCurrency())) {
                    totalFinalUsd += finalAmount;
                    totalPaidUsd += paidAmount;
                } else {
                    totalFinalIqd += finalAmount;
                    totalPaidIqd += paidAmount;
                }
            }
        }
        for (SaleReturn r : returns) {
            double returnAmount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;
            totalReturns += returnAmount;
            if (currency == null || currency.isEmpty()) {
                String retCurrency = r.getSale() != null ? r.getSale().getCurrency() : null;
                if ("دولار".equals(retCurrency)) {
                    totalReturnsUsd += returnAmount;
                } else {
                    totalReturnsIqd += returnAmount;
                }
            }
        }
        double totalRemaining = totalFinal - totalPaid - totalReturns;
        double totalRemainingIqd = totalFinalIqd - totalPaidIqd - totalReturnsIqd;
        double totalRemainingUsd = totalFinalUsd - totalPaidUsd - totalReturnsUsd;
        
        double netSales = totalFinal - totalReturns;
        double netSalesIqd = totalFinalIqd - totalReturnsIqd;
        double netSalesUsd = totalFinalUsd - totalReturnsUsd;

        Map<Long, Double> returnsBySaleId = new HashMap<>();
        for (SaleReturn r : returns) {
            if (r.getSale() == null || r.getSale().getId() == null) {
                continue;
            }
            double amount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;
            returnsBySaleId.merge(r.getSale().getId(), amount, (a, b) -> (a != null ? a : 0.0) + (b != null ? b : 0.0));
        }

        PdfPTable summary = new PdfPTable(3);
        summary.setWidthPercentage(100);
        summary.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        summary.setSpacingBefore(6f);
        summary.setSpacingAfter(10f);
        summary.setWidths(new float[]{1f, 1f, 1f});

        PdfPCell s1 = new PdfPCell(new Phrase("إجمالي البيع\n" +
            (currency == null || currency.isEmpty()
                ? "دينار: " + formatCurrency(netSalesIqd, "دينار") + "\nدولار: " + formatCurrency(netSalesUsd, "دولار")
                : formatCurrency(netSales, currency)), arabicBoldFont));
        s1.setHorizontalAlignment(Element.ALIGN_CENTER);
        s1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s1.setPadding(8f);
        summary.addCell(s1);

        PdfPCell s2 = new PdfPCell(new Phrase("إجمالي المدفوع\n" +
            (currency == null || currency.isEmpty()
                ? "دينار: " + formatCurrency(totalPaidIqd, "دينار") + "\nدولار: " + formatCurrency(totalPaidUsd, "دولار")
                : formatCurrency(totalPaid, currency)), arabicBoldFont));
        s2.setHorizontalAlignment(Element.ALIGN_CENTER);
        s2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s2.setPadding(8f);
        summary.addCell(s2);

        PdfPCell s3 = new PdfPCell(new Phrase("المطلوب للدفع لهذا المشروع\n" +
            (currency == null || currency.isEmpty()
                ? "دينار: " + formatCurrency(totalRemainingIqd, "دينار") + "\nدولار: " + formatCurrency(totalRemainingUsd, "دولار")
                : formatCurrency(totalRemaining, currency)), arabicBoldFont));
        s3.setHorizontalAlignment(Element.ALIGN_CENTER);
        s3.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        s3.setPadding(8f);
        summary.addCell(s3);

        document.add(summary);

        if (includeItems) {
            Map<String, ItemAggregate> aggregated = new HashMap<>();

            for (Sale s : sales) {
                String rowCurrency = currency == null || currency.isEmpty()
                        ? ("دولار".equals(s.getCurrency()) ? "دولار" : "دينار")
                        : currency;
                List<SaleItem> items = s.getSaleItems() != null ? s.getSaleItems() : Collections.emptyList();
                for (SaleItem it : items) {
                    Long productId = it.getProduct() != null ? it.getProduct().getId() : null;
                    String productName = it.getProduct() != null && it.getProduct().getName() != null ? it.getProduct().getName() : "-";
                    String key = (productId != null ? productId : 0L) + "|" + rowCurrency;

                    double qty = it.getQuantity() != null ? it.getQuantity() : 0.0;
                    double total = it.getTotalPrice() != null ? it.getTotalPrice() : 0.0;

                    ItemAggregate agg = aggregated.computeIfAbsent(key, k -> new ItemAggregate(productName, rowCurrency));
                    agg.quantity += qty;
                    agg.total += total;
                }
            }

            for (SaleReturn r : returns) {
                String returnCurrency = currency == null || currency.isEmpty()
                        ? ("دولار".equals(r.getSale() != null ? r.getSale().getCurrency() : null) ? "دولار" : "دينار")
                        : currency;
                List<ReturnItem> returnItems = r.getReturnItems() != null ? r.getReturnItems() : Collections.emptyList();
                for (ReturnItem item : returnItems) {
                    Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
                    String productName = item.getProduct() != null && item.getProduct().getName() != null ? item.getProduct().getName() : "-";
                    String key = (productId != null ? productId : 0L) + "|" + returnCurrency;

                    double qty = item.getQuantity() != null ? item.getQuantity() : 0.0;
                    double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

                    ItemAggregate agg = aggregated.computeIfAbsent(key, k -> new ItemAggregate(productName, returnCurrency));
                    agg.quantity -= qty;
                    agg.total -= total;
                }
            }

            List<ItemAggregate> rows = new ArrayList<>(aggregated.values());
            rows.removeIf(r -> Math.abs(r.quantity) < 0.000001 && Math.abs(r.total) < 0.000001);
            rows.sort(Comparator
                    .comparing((ItemAggregate a) -> a.productName != null ? a.productName : "")
                    .thenComparing(a -> a.currency != null ? a.currency : ""));

            Map<String, Double> grandTotals = new HashMap<>();
            for (ItemAggregate r : rows) {
                String cur = r.currency != null ? r.currency : "دينار";
                grandTotals.merge(cur, r.total, Double::sum);
            }

            PdfPTable itemsTable = new PdfPTable(5);
            itemsTable.setWidthPercentage(100);
            itemsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            itemsTable.setSpacingBefore(5f);
            itemsTable.setSpacingAfter(10f);
            itemsTable.setWidths(new float[]{1f, 1.6f, 0.9f, 1f, 0.2f});

            addTableHeader(itemsTable, "ت", arabicBoldFont);
            addTableHeader(itemsTable, "المادة", arabicBoldFont);
            addTableHeader(itemsTable, "الكمية", arabicBoldFont);
            addTableHeader(itemsTable, "السعر", arabicBoldFont);
            addTableHeader(itemsTable, "المجموع", arabicBoldFont);

            int i = 1;
            for (ItemAggregate agg : rows) {
                double unitPrice = agg.quantity != 0.0 ? (agg.total / agg.quantity) : 0.0;
                itemsTable.addCell(createBodyCell(String.valueOf(i), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(agg.productName != null ? agg.productName : "-", arabicFont, Element.ALIGN_RIGHT));
                itemsTable.addCell(createBodyCell(formatAmount(agg.quantity), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(formatCurrency(unitPrice, agg.currency), arabicFont, Element.ALIGN_CENTER));
                itemsTable.addCell(createBodyCell(formatCurrency(agg.total, agg.currency), arabicFont, Element.ALIGN_CENTER));
                i++;
            }

            List<String> currencies = new ArrayList<>(grandTotals.keySet());
            currencies.sort(String::compareTo);
            for (String cur : currencies) {
                PdfPCell labelCell = new PdfPCell(new Phrase("المجموع النهائي - " + cur, arabicBoldFont));
                labelCell.setColspan(4);
                labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                labelCell.setPadding(4f);
                itemsTable.addCell(labelCell);

                PdfPCell valueCell = new PdfPCell(new Phrase(formatCurrency(grandTotals.getOrDefault(cur, 0.0), cur), arabicBoldFont));
                valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                valueCell.setPadding(4f);
                itemsTable.addCell(valueCell);
            }

            document.add(itemsTable);
        } else {
            PdfPTable salesTable = new PdfPTable(6);
            salesTable.setWidthPercentage(100);
            salesTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            salesTable.setSpacingBefore(5f);
            salesTable.setSpacingAfter(10f);
            salesTable.setWidths(new float[]{1f, 1f, 1.2f, 1f, 1f, 0.2f});

            addTableHeader(salesTable, "ت", arabicBoldFont);
            addTableHeader(salesTable, "رقم الفاتورة", arabicBoldFont);
            addTableHeader(salesTable, "التاريخ", arabicBoldFont);
            addTableHeader(salesTable, "المشروع", arabicBoldFont);
            addTableHeader(salesTable, "الإجمالي", arabicBoldFont);
            addTableHeader(salesTable, "المدفوع/الدين", arabicBoldFont);

            int row = 1;
            for (Sale s : sales) {
                String code = s.getSaleCode() != null ? s.getSaleCode() : "-";
                String date = s.getSaleDate() != null ? s.getSaleDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                String proj = s.getProjectLocation() != null ? s.getProjectLocation() : "-";
                double fin = s.getFinalAmount() != null ? s.getFinalAmount() : 0.0;
                double paid = s.getPaidAmount() != null ? s.getPaidAmount() : 0.0;
                double returnsForSale = returnsBySaleId.getOrDefault(s.getId(), 0.0);
                double rem = fin - paid - returnsForSale;
                String rowCurrency = currency == null || currency.isEmpty()
                        ? ("دولار".equals(s.getCurrency()) ? "دولار" : "دينار")
                        : currency;
                String paidDebtDisplay = rem != 0
                        ? formatCurrency(paid, rowCurrency) + " / " + formatCurrency(rem, rowCurrency)
                        : formatCurrency(paid, rowCurrency);

                salesTable.addCell(createBodyCell(String.valueOf(row), arabicFont, Element.ALIGN_CENTER));
                salesTable.addCell(createBodyCell(code, arabicFont, Element.ALIGN_CENTER));
                salesTable.addCell(createBodyCell(date, arabicFont, Element.ALIGN_CENTER));
                salesTable.addCell(createBodyCell(proj, arabicFont, Element.ALIGN_CENTER));
                salesTable.addCell(createBodyCell(formatCurrency(fin, rowCurrency), arabicFont, Element.ALIGN_CENTER));
                salesTable.addCell(createBodyCell(paidDebtDisplay, arabicFont, Element.ALIGN_CENTER));
                row++;
            }

            document.add(salesTable);

            if (!returns.isEmpty()) {
                PdfPTable returnsHeader = new PdfPTable(1);
                returnsHeader.setWidthPercentage(100);
                returnsHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                returnsHeader.setSpacingBefore(10f);

                PdfPCell returnsTitleCell = new PdfPCell(new Phrase("المرتجعات", sectionTitleFont));
                returnsTitleCell.setBorder(PdfPCell.NO_BORDER);
                returnsTitleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                returnsTitleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                returnsTitleCell.setPaddingBottom(4f);
                returnsTitleCell.setBackgroundColor(new BaseColor(255, 230, 230));
                returnsHeader.addCell(returnsTitleCell);
                document.add(returnsHeader);

                PdfPTable returnsTable = new PdfPTable(5);
                returnsTable.setWidthPercentage(100);
                returnsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                returnsTable.setSpacingBefore(5f);
                returnsTable.setSpacingAfter(10f);
                returnsTable.setWidths(new float[]{1f, 1f, 1f, 1.2f, 0.2f});

                addTableHeader(returnsTable, "ت", arabicBoldFont);
                addTableHeader(returnsTable, "رقم المرتجع", arabicBoldFont);
                addTableHeader(returnsTable, "رقم الفاتورة", arabicBoldFont);
                addTableHeader(returnsTable, "التاريخ", arabicBoldFont);
                addTableHeader(returnsTable, "المبلغ المرتجع", arabicBoldFont);

                int retRow = 1;
                for (SaleReturn r : returns) {
                    String retCode = r.getReturnCode() != null ? r.getReturnCode() : "-";
                    String saleCode = r.getSale() != null && r.getSale().getSaleCode() != null ? r.getSale().getSaleCode() : "-";
                    String retDate = r.getReturnDate() != null ? r.getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                    double retAmount = r.getTotalReturnAmount() != null ? r.getTotalReturnAmount() : 0.0;

                    returnsTable.addCell(createBodyCell(String.valueOf(retRow), arabicFont, Element.ALIGN_CENTER));
                    returnsTable.addCell(createBodyCell(retCode, arabicFont, Element.ALIGN_CENTER));
                    returnsTable.addCell(createBodyCell(saleCode, arabicFont, Element.ALIGN_CENTER));
                    returnsTable.addCell(createBodyCell(retDate, arabicFont, Element.ALIGN_CENTER));
                    String returnCurrency = currency == null || currency.isEmpty()
                            ? ("دولار".equals(r.getSale() != null ? r.getSale().getCurrency() : null) ? "دولار" : "دينار")
                            : currency;
                    returnsTable.addCell(createBodyCell(formatCurrency(retAmount, returnCurrency), arabicFont, Element.ALIGN_CENTER));
                    retRow++;
                }

                document.add(returnsTable);
            }
        }

        addUnifiedFooter(document, arabicBoldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private static class ItemAggregate {
        private final String productName;
        private final String currency;
        private double quantity;
        private double total;

        private ItemAggregate(String productName, String currency) {
            this.productName = productName;
            this.currency = currency;
        }
    }

    private PdfPCell createBodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        return cell;
    }

    private void addTotalRowBordered(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(5f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(5f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    private void addTableHeader(PdfPTable table, String header, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(header, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(5f);
        table.addCell(cell);
    }
    
    private String formatAmount(Double value) {
        double v = value != null ? value : 0.0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return df.format(v);
    }

    private String formatCurrency(Double value) {
        return formatCurrency(value, "دينار");
    }

    private String formatCurrency(Double value, String currency) {
        String label = currency != null && !currency.isBlank() ? currency : "دينار";
        return formatAmount(value) + " " + label;
    }
    
    private String generateReceiptNumber() {
        Preferences prefs = Preferences.userNodeForPackage(ReceiptService.class);
        long lastUsed = prefs.getLong(PREF_LAST_RECEIPT_NUMBER, 0L);

        long dbNext = receiptRepository.getNextReceiptNumberNumeric();
        long next = Math.max(lastUsed + 1L, dbNext);

        prefs.putLong(PREF_LAST_RECEIPT_NUMBER, next);
        return String.valueOf(next);
    }
    
    private String getPaymentMethodArabic(String method) {
        if (method == null) return "-";
        switch (method) {
            case "CASH": return "نقدي";
            case "DEBT": return "دين";
            case "CARD": return "بطاقة";
            default: return method;
        }
    }
    
    private void addUnifiedFooter(Document document, Font boldFont, Font smallFont) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        footerTable.setSpacingBefore(15f);

        PdfPCell thankYouCell = new PdfPCell(new Phrase("شكراً لتعاملكم معنا", boldFont));
        thankYouCell.setBorder(PdfPCell.NO_BORDER);
        thankYouCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        thankYouCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        thankYouCell.setPaddingBottom(8f);
        footerTable.addCell(thankYouCell);

        PdfPCell appInfoCell = new PdfPCell(new Phrase(APP_NAME + " | " + COMPANY_WEBSITE, smallFont));
        appInfoCell.setBorder(PdfPCell.NO_BORDER);
        appInfoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        appInfoCell.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        appInfoCell.setPaddingTop(5f);
        appInfoCell.setBackgroundColor(new BaseColor(245, 245, 245));
        footerTable.addCell(appInfoCell);

        document.add(footerTable);
    }

    private String getPaymentStatusArabic(String status) {
        if (status == null) return "-";
        switch (status) {
            case "PAID": return "مدفوع";
            case "PENDING": return "معلق";
            case "OVERDUE": return "متأخر";
            default: return status;
        }
    }
    
    public Optional<Receipt> getReceiptById(Long id) {
        return receiptRepository.findById(id);
    }
    
    public Optional<Receipt> getReceiptByNumber(String receiptNumber) {
        return receiptRepository.findByReceiptNumber(receiptNumber);
    }
    
    public List<Receipt> getAllReceipts() {
        return receiptRepository.findAllWithDetails();
    }
    
    public void deleteReceipt(Long id) {
        if (id == null) {
            return;
        }

        try {
            Optional<Receipt> receiptOpt = receiptRepository.findById(id);
            if (receiptOpt.isPresent()) {
                Receipt receipt = receiptOpt.get();
                if (receipt.getFilePath() != null) {
                    File pdf = new File(receipt.getFilePath());
                    if (pdf.exists()) {
                        pdf.delete();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to delete receipt PDF before DB delete: {}", id, e);
        }

        receiptRepository.deleteByIdDirect(id);
    }
    
    public boolean hasReceiptForSale(Long saleId) {
        if (saleId == null) return false;
        return receiptRepository.findAll().stream()
                .anyMatch(r -> r.getSale() != null && saleId.equals(r.getSale().getId()));
    }
}
