package tokyoera.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import tokyoera.model.CartItem;
import tokyoera.model.OrderReceipt;

import java.awt.Color;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;

// This service generates a PDF receipt for a completed order.
// It uses the iText/OpenPDF library to lay out a retro-styled thermal receipt.
public class PdfService {
    public Path exportReceipt(OrderReceipt receipt) {
        try {
            Path output = Path.of("receipt-order-" + receipt.getOrderId() + ".pdf").toAbsolutePath();

            // Narrow receipt-like page (A5 width, taller) with cream background
            com.lowagie.text.Rectangle pageSize = new com.lowagie.text.Rectangle(340f, 700f);
            pageSize.setBackgroundColor(new Color(254, 249, 231)); // cream #fef9e7
            Document document = new Document(pageSize, 28, 28, 28, 28);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(output.toFile()));
            document.open();

            // Fonts (Courier = monospace retro feel, different sizes for different sections)
            Font fontTitle  = new Font(Font.COURIER, 20f, Font.BOLD,   new Color(26, 26, 26));
            Font fontSub    = new Font(Font.COURIER,  8f, Font.NORMAL, new Color(102,102,102));
            Font fontMeta   = new Font(Font.COURIER,  9f, Font.NORMAL, new Color(80, 80, 80));
            Font fontItem   = new Font(Font.COURIER,  9f, Font.NORMAL, new Color(60, 60, 60));
            Font fontTotal  = new Font(Font.COURIER, 13f, Font.BOLD,   new Color(26, 26, 26));
            Font fontStamp  = new Font(Font.COURIER, 14f, Font.BOLD,   new Color(0, 122, 53));
            Font fontThanks = new Font(Font.COURIER,  8f, Font.NORMAL, new Color(136,136,136));
            Font fontDash   = new Font(Font.COURIER,  8f, Font.NORMAL, new Color(200,200,200));

            String dash   = "- - - - - - - - - - - - - - - - - - -";
            String eqDash = "= = = = = = = = = = = = = = = = = = =";

            // ── Store header ───────────────────────────────────────────────
            // Store name
            Paragraph storeName = new Paragraph("TOKYOERA", fontTitle);
            storeName.setAlignment(Element.ALIGN_CENTER);
            storeName.setSpacingAfter(2f);
            document.add(storeName);

            Paragraph storeBy = new Paragraph("BY AKIRA FUKUTOMI", fontSub);
            storeBy.setAlignment(Element.ALIGN_CENTER);
            storeBy.setSpacingAfter(6f);
            document.add(storeBy);

            document.add(new Paragraph(dash, fontDash));

            // ── Order meta (ID + date) ───────────────────────────────────────
            // Order meta
            String dateStr = receipt.getCreatedAt() != null
                    ? receipt.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    : java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));

            Paragraph orderId = new Paragraph("ORDER  #" + receipt.getOrderId(), fontMeta);
            orderId.setSpacingBefore(4f);
            document.add(orderId);
            document.add(new Paragraph("DATE:   " + dateStr, fontMeta));

            document.add(new Paragraph(dash, fontDash));

            // ── Items table ─────────────────────────────────────────────────
            // Items table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{3f, 1f});
            table.setSpacingBefore(4f);
            table.setSpacingAfter(4f);

            for (CartItem item : receipt.getItems()) {
                String label = item.getProduct().getName() + " x" + item.getQuantity();
                String price = String.format("RM %.2f", item.subtotal());

                PdfPCell nameCell  = new PdfPCell(new Phrase(label, fontItem));
                PdfPCell priceCell = new PdfPCell(new Phrase(price, fontItem));
                nameCell.setBorder(Rectangle.NO_BORDER);
                priceCell.setBorder(Rectangle.NO_BORDER);
                priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                nameCell.setBackgroundColor(new Color(254, 249, 231));
                priceCell.setBackgroundColor(new Color(254, 249, 231));
                table.addCell(nameCell);
                table.addCell(priceCell);

                // Size / customization sub-line
                String sub = "  Size: " + item.getSize() + "  |  " + item.customizationLabel();
                PdfPCell subCell = new PdfPCell(new Phrase(sub, fontSub));
                subCell.setColspan(2);
                subCell.setBorder(Rectangle.NO_BORDER);
                subCell.setBackgroundColor(new Color(254, 249, 231));
                subCell.setPaddingBottom(4f);
                table.addCell(subCell);
            }
            document.add(table);

            document.add(new Paragraph(eqDash, fontDash));

            // ── Total row ──────────────────────────────────────────────────
            // Total row
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100f);
            totalTable.setWidths(new float[]{3f, 1f});
            totalTable.setSpacingBefore(2f);
            totalTable.setSpacingAfter(6f);
            PdfPCell totLbl = new PdfPCell(new Phrase("TOTAL", fontTotal));
            PdfPCell totAmt = new PdfPCell(new Phrase(String.format("RM %.2f", receipt.getTotal()), fontTotal));
            totLbl.setBorder(Rectangle.NO_BORDER);
            totAmt.setBorder(Rectangle.NO_BORDER);
            totAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totLbl.setBackgroundColor(new Color(254, 249, 231));
            totAmt.setBackgroundColor(new Color(254, 249, 231));
            totalTable.addCell(totLbl);
            totalTable.addCell(totAmt);
            document.add(totalTable);

            document.add(new Paragraph(dash, fontDash));

            // ── Order confirmed stamp ─────────────────────────────────────────
            // Stamp
            Paragraph stamp = new Paragraph("\u2713  ORDER CONFIRMED", fontStamp);
            stamp.setAlignment(Element.ALIGN_CENTER);
            stamp.setSpacingBefore(10f);
            stamp.setSpacingAfter(6f);
            document.add(stamp);

            // Draw a rounded green border box around the stamp using the PDF canvas directly
            PdfContentByte cb = writer.getDirectContent();
            cb.setColorStroke(new Color(0, 122, 53));
            cb.setLineWidth(2f);
            float stampY = writer.getVerticalPosition(false);
            cb.roundRectangle(40f, stampY - 4f, pageSize.getWidth() - 80f, 26f, 4f);
            cb.stroke();

            Paragraph thanks = new Paragraph("THANK YOU FOR YOUR PURCHASE!", fontThanks);
            thanks.setAlignment(Element.ALIGN_CENTER);
            thanks.setSpacingBefore(14f);
            document.add(thanks);

            // Contact email for customer enquiries
            Paragraph contact = new Paragraph("For more inquiry: aacf1n23@soton.ac.uk", fontSub);
            contact.setAlignment(Element.ALIGN_CENTER);
            contact.setSpacingBefore(6f);
            document.add(contact);

            document.close();
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate PDF", exception);
        }
    }
}
