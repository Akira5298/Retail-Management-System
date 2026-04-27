package tokyoera.ui;

import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import tokyoera.AppContext;
import tokyoera.model.CartItem;
import tokyoera.model.OrderReceipt;

// Simple invoice/receipt popup that shows order details and a PDF export button.
public class InvoiceDialog {
    public static void show(Window owner, AppContext context, OrderReceipt receipt) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Invoice / Receipt");

        Label title = new Label("TokyoEra Invoice");
        title.getStyleClass().add("heading");
        Label orderMeta = new Label("Order ID: " + receipt.getOrderId() + " | Date: " + receipt.getCreatedAt());

        // Build one card per line item, then show the grand total below
        VBox lines = new VBox(8);
        for (CartItem item : receipt.getItems()) {
            Label line = new Label(item.getProduct().getName() + "\nSize: " + item.getSize() + " | Qty: " + item.getQuantity()
                    + "\nCustomization: " + item.customizationLabel() + "\nSubtotal: " + UiUtil.rm(item.subtotal()));
            line.setWrapText(true);
            line.getStyleClass().add("card");
            lines.getChildren().add(line);
        }

        Label total = new Label("TOTAL: " + UiUtil.rm(receipt.getTotal()));
        total.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #00E6FF;");

        Button exportPdf = new Button("Print PDF Receipt");
        exportPdf.getStyleClass().add("neon-btn");
        exportPdf.setOnAction(event -> {
            Path path = context.pdfService().exportReceipt(receipt);
            UiUtil.info("PDF Exported", "Receipt saved to: " + path);
        });

        Button close = new Button("Close");
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(10, exportPdf, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scrollPane = new ScrollPane(lines);
        scrollPane.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setTop(new VBox(8, title, orderMeta));
        root.setCenter(scrollPane);
        root.setBottom(new VBox(10, total, actions));
        root.getStyleClass().add("panel");

        Scene scene = new Scene(root, 740, 600);
        scene.getStylesheets().add(InvoiceDialog.class.getResource("/tokyoera/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
