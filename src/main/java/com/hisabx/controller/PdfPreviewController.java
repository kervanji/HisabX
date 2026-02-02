package com.hisabx.controller;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.hisabx.util.FxUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PdfPreviewController {
    private static final Logger logger = LoggerFactory.getLogger(PdfPreviewController.class);

    @FXML private VBox pagesContainer;
    @FXML private Button printButton;

    private Stage dialogStage;
    private File pdfFile;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setPdfFile(File pdfFile) {
        this.pdfFile = pdfFile;
        loadPdfPreview();
    }

    private void loadPdfPreview() {
        if (pdfFile == null || !pdfFile.exists()) return;

        Task<List<WritableImage>> loadTask = new Task<>() {
            @Override
            protected List<WritableImage> call() throws Exception {
                List<WritableImage> images = new ArrayList<>();
                try (PDDocument document = PDDocument.load(pdfFile)) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    for (int page = 0; page < document.getNumberOfPages(); page++) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 150, ImageType.RGB);
                        images.add(SwingFXUtils.toFXImage(bim, null));
                    }
                }
                return images;
            }
        };

        loadTask.setOnSucceeded(e -> {
            pagesContainer.getChildren().clear();
            for (WritableImage image : loadTask.getValue()) {
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(595); // A4 width at roughly 72 DPI (adjust as needed)
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 10, 0, 0, 0);");
                pagesContainer.getChildren().add(imageView);
            }
        });

        loadTask.setOnFailed(e -> {
            logger.error("Failed to load PDF preview", loadTask.getException());
            // Show error placeholder or alert
        });

        new Thread(loadTask).start();
    }

    @FXML
    private void handlePrint() {
        if (pdfFile == null || !pdfFile.exists()) return;

        Task<Void> printTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (PDDocument document = PDDocument.load(pdfFile)) {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPageable(new PDFPageable(document));
                    if (job.printDialog()) {
                        job.print();
                    }
                }
                return null;
            }
        };

        printTask.setOnFailed(e -> logger.error("Failed to print PDF", printTask.getException()));

        new Thread(printTask).start();
    }

    @FXML
    private void handleClose() {
        FxUtil.closeWindow(printButton);
    }
}
