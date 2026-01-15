package com.hisabx.service;

import com.hisabx.model.Customer;
import com.hisabx.model.Product;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

public class PrintService {
    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    
    private static final String PREF_BANNER_PATH = "receipt.banner.path";
    private static final String APP_NAME = "HisabX";
    private static final String COMPANY_WEBSITE = "Kervanjiholding.com";
    private static final float BANNER_HEIGHT = 80f;
    
    public File generateCustomerListPdf(List<Customer> customers) {
        try {
            File dir = new File("reports");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "customers_report_" + timestamp + ".pdf";
            File outputFile = new File(dir, fileName);
            
            Document document = new Document(PageSize.A4, 30, 30, 30 + BANNER_HEIGHT, 30);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            document.open();
            
            BaseFont baseFont = loadArabicBaseFont();
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Font headerFont = new Font(baseFont, 11, Font.BOLD);
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
            Font smallFont = new Font(baseFont, 9, Font.NORMAL);
            
            // Banner
            addBanner(writer, document);
            
            // Title
            addCenteredRtlText(document, "تقرير العملاء", titleFont);
            
            // Date
            addCenteredRtlText(document,
                    "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    smallFont, 15f);
            
            // Table
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.setWidths(new float[]{1.25f, 0.75f, 1.45f, 1.2f, 0.30f, 0.25f});
            
            // Headers
            addTableHeader(table, "ت", headerFont);
            addTableHeader(table, "الكود", headerFont);
            addTableHeader(table, "الاسم", headerFont);
            addTableHeader(table, "الهاتف", headerFont);
            addTableHeader(table, "العنوان", headerFont);
            addTableHeader(table, "الرصيد", headerFont);
            
            // Data rows
            int rowNum = 1;
            double totalDebt = 0;
            double totalCredit = 0;
            
            for (Customer customer : customers) {
                table.addCell(createBodyCell(String.valueOf(rowNum), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(customer.getCustomerCode(), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(customer.getName(), bodyFont, Element.ALIGN_RIGHT));
                table.addCell(createBodyCell(customer.getPhoneNumber(), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(customer.getAddress(), bodyFont, Element.ALIGN_RIGHT));
                
                Double balance = customer.getCurrentBalance();
                String balanceStr = DECIMAL_FORMAT.format(balance != null ? balance : 0);
                PdfPCell balanceCell = createBodyCell(balanceStr, bodyFont, Element.ALIGN_CENTER);
                if (balance != null && balance < 0) {
                    balanceCell.setBackgroundColor(new BaseColor(254, 226, 226));
                    totalDebt += Math.abs(balance);
                } else if (balance != null && balance > 0) {
                    balanceCell.setBackgroundColor(new BaseColor(220, 252, 231));
                    totalCredit += balance;
                }
                table.addCell(balanceCell);
                
                rowNum++;
            }
            
            document.add(table);
            
            // Summary
            document.add(new Paragraph(" ", bodyFont));
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(50);
            summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summaryTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            
            addSummaryRow(summaryTable, "إجمالي العملاء:", String.valueOf(customers.size()), headerFont, bodyFont);
            addSummaryRow(summaryTable, "إجمالي الديون:", DECIMAL_FORMAT.format(totalDebt) + " د.ع", headerFont, bodyFont);
            addSummaryRow(summaryTable, "إجمالي الأرصدة:", DECIMAL_FORMAT.format(totalCredit) + " د.ع", headerFont, bodyFont);
            
            document.add(summaryTable);
            
            // Footer
            addUnifiedFooter(document, headerFont, smallFont);
            
            document.close();
            logger.info("Customer list PDF generated: {}", outputFile.getAbsolutePath());
            return outputFile;
            
        } catch (Exception e) {
            logger.error("Failed to generate customer list PDF", e);
            throw new RuntimeException("فشل في إنشاء تقرير العملاء", e);
        }
    }
    
    public File generateInventoryListPdf(List<Product> products) {
        try {
            File dir = new File("reports");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "inventory_report_" + timestamp + ".pdf";
            File outputFile = new File(dir, fileName);
            
            Document document = new Document(PageSize.A4.rotate(), 30, 30, 30 + BANNER_HEIGHT, 30);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            document.open();
            
            BaseFont baseFont = loadArabicBaseFont();
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Font headerFont = new Font(baseFont, 11, Font.BOLD);
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
            Font smallFont = new Font(baseFont, 9, Font.NORMAL);
            
            // Banner
            addBanner(writer, document);
            
            // Title
            addCenteredRtlText(document, "تقرير المخزون", titleFont);
            
            // Date
            addCenteredRtlText(document,
                    "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    smallFont, 15f);
            
            // Table
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.setWidths(new float[]{0.4f, 0.8f, 1.5f, 0.8f, 0.8f, 0.8f, 0.6f, 0.6f});
            
            // Headers
            addTableHeader(table, "ت", headerFont);
            addTableHeader(table, "الكود", headerFont);
            addTableHeader(table, "الاسم", headerFont);
            addTableHeader(table, "الفئة", headerFont);
            addTableHeader(table, "السعر", headerFont);
            addTableHeader(table, "التكلفة", headerFont);
            addTableHeader(table, "الكمية", headerFont);
            addTableHeader(table, "الحد الأدنى", headerFont);
            
            // Data rows
            int rowNum = 1;
            double totalValue = 0;
            double totalStock = 0;
            int lowStockCount = 0;
            
            for (Product product : products) {
                table.addCell(createBodyCell(String.valueOf(rowNum), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(product.getProductCode(), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(product.getName(), bodyFont, Element.ALIGN_RIGHT));
                table.addCell(createBodyCell(product.getCategory(), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(DECIMAL_FORMAT.format(product.getUnitPrice() != null ? product.getUnitPrice() : 0), bodyFont, Element.ALIGN_CENTER));
                table.addCell(createBodyCell(DECIMAL_FORMAT.format(product.getCostPrice() != null ? product.getCostPrice() : 0), bodyFont, Element.ALIGN_CENTER));
                
                Double qty = product.getQuantityInStock();
                Double minStock = product.getMinimumStock();
                boolean isLowStock = qty != null && minStock != null && qty <= minStock;
                
                PdfPCell qtyCell = createBodyCell(String.valueOf(qty != null ? qty : 0.0), bodyFont, Element.ALIGN_CENTER);
                if (isLowStock) {
                    qtyCell.setBackgroundColor(new BaseColor(254, 226, 226));
                    lowStockCount++;
                }
                table.addCell(qtyCell);
                table.addCell(createBodyCell(String.valueOf(minStock != null ? minStock : 0.0), bodyFont, Element.ALIGN_CENTER));
                
                if (qty != null && product.getCostPrice() != null) {
                    totalValue += qty * product.getCostPrice();
                }
                totalStock += qty != null ? qty : 0;
                
                rowNum++;
            }
            
            document.add(table);
            
            // Summary
            document.add(new Paragraph(" ", bodyFont));
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(40);
            summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            summaryTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            
            addSummaryRow(summaryTable, "إجمالي المنتجات:", String.valueOf(products.size()), headerFont, bodyFont);
            addSummaryRow(summaryTable, "إجمالي الكمية:", String.valueOf(totalStock), headerFont, bodyFont);
            addSummaryRow(summaryTable, "قيمة المخزون:", DECIMAL_FORMAT.format(totalValue) + " د.ع", headerFont, bodyFont);
            addSummaryRow(summaryTable, "منتجات منخفضة:", String.valueOf(lowStockCount), headerFont, bodyFont);
            
            document.add(summaryTable);
            
            // Footer
            addUnifiedFooter(document, headerFont, smallFont);
            
            document.close();
            logger.info("Inventory list PDF generated: {}", outputFile.getAbsolutePath());
            return outputFile;
            
        } catch (Exception e) {
            logger.error("Failed to generate inventory list PDF", e);
            throw new RuntimeException("فشل في إنشاء تقرير المخزون", e);
        }
    }
    
    private BaseFont loadArabicBaseFont() throws DocumentException, java.io.IOException {
        String[] fontCandidates = new String[]{
            "C:\\Windows\\Fonts\\arial.ttf",
            "C:\\Windows\\Fonts\\tahoma.ttf",
            "C:\\Windows\\Fonts\\arialuni.ttf"
        };

        for (String fontPath : fontCandidates) {
            try {
                return BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }

        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
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
    
    private PdfPCell createBodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(4f);
        return cell;
    }
    
    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setPadding(5f);
        labelCell.setBorder(PdfPCell.NO_BORDER);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setPadding(5f);
        valueCell.setBorder(PdfPCell.NO_BORDER);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addCenteredRtlText(Document document, String text, Font font) throws DocumentException {
        addCenteredRtlText(document, text, font, 8f, 8f);
    }

    private void addCenteredRtlText(Document document, String text, Font font, float spacingAfter) throws DocumentException {
        addCenteredRtlText(document, text, font, 8f, spacingAfter);
    }

    private void addCenteredRtlText(Document document, String text, Font font, float spacingBefore, float spacingAfter) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(spacingBefore);
        paragraph.setSpacingAfter(spacingAfter);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.addElement(paragraph);
        wrapper.addCell(cell);

        document.add(wrapper);
    }
    
    private void addBanner(PdfWriter writer, Document document) {
        try {
            Image banner = loadBannerImage();
            if (banner != null) {
                float pageWidth = document.getPageSize().getWidth();
                float pageHeight = document.getPageSize().getHeight();
                banner.scaleAbsolute(pageWidth, BANNER_HEIGHT);
                banner.setAbsolutePosition(0f, pageHeight - BANNER_HEIGHT);
                writer.getDirectContent().addImage(banner);
            }
        } catch (Exception e) {
            logger.warn("Failed to add banner to report", e);
        }
    }
    
    private Image loadBannerImage() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(PrintService.class);
            String bannerPath = prefs.get(PREF_BANNER_PATH, null);
            if (bannerPath != null && !bannerPath.trim().isEmpty()) {
                File f = new File(bannerPath);
                if (f.exists() && f.isFile()) {
                    return Image.getInstance(f.getAbsolutePath());
                }
            }
            
            URL logoUrl = PrintService.class.getResource("/templates/HisabX.png");
            if (logoUrl != null) {
                return Image.getInstance(logoUrl);
            }
        } catch (Exception e) {
            logger.warn("Failed to load banner image", e);
        }
        return null;
    }
    
    private void addUnifiedFooter(Document document, Font boldFont, Font smallFont) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);
        footerTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        footerTable.setSpacingBefore(15f);

        PdfPCell appInfoCell = new PdfPCell(new Phrase(APP_NAME + " | " + COMPANY_WEBSITE, smallFont));
        appInfoCell.setBorder(PdfPCell.NO_BORDER);
        appInfoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        appInfoCell.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        appInfoCell.setPaddingTop(8f);
        appInfoCell.setPaddingBottom(8f);
        appInfoCell.setBackgroundColor(new BaseColor(245, 245, 245));
        footerTable.addCell(appInfoCell);

        document.add(footerTable);
    }
}
