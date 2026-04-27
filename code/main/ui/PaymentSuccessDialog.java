package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.CartItem;
import tokyoera.model.OrderReceipt;
import java.awt.Desktop;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

// Popup shown right after a successful checkout.
// It renders a retro receipt layout and plays a stamp bounce animation when it opens.
public class PaymentSuccessDialog {
    public static void show(Window owner, AppContext context, OrderReceipt receipt) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Order Confirmed");

        // ── Receipt paper ──────────────────────────────────────────
        String MONO = "-fx-font-family: 'Courier New', 'Courier', monospace;";
        String dateStr = receipt.getCreatedAt() != null
                ? receipt.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));

        Label storeName = new Label("TOKYOERA");
        storeName.setStyle(MONO + "-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #1a1a1a;");
        storeName.setMaxWidth(Double.MAX_VALUE);
        storeName.setAlignment(Pos.CENTER);

        Label storeBy = new Label("BY AKIRA FUKUTOMI");
        storeBy.setStyle(MONO + "-fx-font-size: 10px; -fx-text-fill: #666;");
        storeBy.setMaxWidth(Double.MAX_VALUE);
        storeBy.setAlignment(Pos.CENTER);

        Label dash1 = dash(MONO);

        Label orderIdLabel = new Label("ORDER  #" + receipt.getOrderId());
        orderIdLabel.setStyle(MONO + "-fx-font-size: 12px; -fx-text-fill: #333;");

        Label dateLabel = new Label("DATE:   " + dateStr);
        dateLabel.setStyle(MONO + "-fx-font-size: 11px; -fx-text-fill: #555;");

        Label dash2 = dash(MONO);

        // Items
        VBox itemsBox = new VBox(3);
        for (CartItem item : receipt.getItems()) {
            HBox row = new HBox();
            Label n = new Label(item.getProduct().getName() + " x" + item.getQuantity());
            n.setStyle(MONO + "-fx-font-size: 11px; -fx-text-fill: #333;");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label pr = new Label(UiUtil.rm(item.subtotal()));
            pr.setStyle(MONO + "-fx-font-size: 11px; -fx-text-fill: #333;");
            row.getChildren().addAll(n, sp, pr);
            itemsBox.getChildren().add(row);
        }

        Label dash3 = new Label("= = = = = = = = = = = = = = = = = = = =");
        dash3.setStyle(MONO + "-fx-font-size: 10px; -fx-text-fill: #aaa;");
        dash3.setMaxWidth(Double.MAX_VALUE);

        HBox totalRow = new HBox();
        Label totalLbl = new Label("TOTAL");
        totalLbl.setStyle(MONO + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a1a;");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Label totalAmt = new Label(UiUtil.rm(receipt.getTotal()));
        totalAmt.setStyle(MONO + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a1a;");
        totalRow.getChildren().addAll(totalLbl, sp2, totalAmt);

        Label dash4 = dash(MONO);

        // Stamp
        Label stamp = new Label("✓  ORDER CONFIRMED");
        stamp.setStyle(MONO + "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #007a35;"
                + "-fx-border-color: #007a35; -fx-border-width: 3; -fx-border-radius: 4;"
                + "-fx-padding: 5 14; -fx-rotate: -10;");
        stamp.setScaleX(0);
        stamp.setScaleY(0);

        Label thankYou = new Label("THANK YOU FOR YOUR PURCHASE!");
        thankYou.setStyle(MONO + "-fx-font-size: 10px; -fx-text-fill: #888;");
        thankYou.setMaxWidth(Double.MAX_VALUE);
        thankYou.setAlignment(Pos.CENTER);

        Label contact = new Label("For more inquiry: aacf1n23@soton.ac.uk");
        contact.setStyle(MONO + "-fx-font-size: 10px; -fx-text-fill: #666;");
        contact.setMaxWidth(Double.MAX_VALUE);
        contact.setAlignment(Pos.CENTER);

        StackPane stampWrap = new StackPane(stamp);
        stampWrap.setPadding(new Insets(10, 0, 4, 0));

        VBox receipt1 = new VBox(6,
                storeName, storeBy, dash1,
                orderIdLabel, dateLabel, dash2,
                itemsBox, dash3, totalRow, dash4,
            stampWrap, thankYou, contact);
        receipt1.setStyle("-fx-background-color: #fef9e7; -fx-background-radius: 4;"
                + "-fx-padding: 20 24; -fx-min-width: 360;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 14, 0.2, 0, 3);");
        receipt1.setAlignment(Pos.TOP_LEFT);

        // Buttons
        Button printBtn = new Button("🖨  Print PDF Receipt");
        printBtn.getStyleClass().add("neon-btn");
        printBtn.setOnAction(event -> {
            try {
                Path pdf = context.pdfService().exportReceipt(receipt);
                Desktop desktop = Desktop.getDesktop();
                if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(pdf.toFile());
                } else {
                    UiUtil.info("PDF Saved", "Receipt saved to:\n" + pdf);
                }
            } catch (Exception ex) {
                UiUtil.error("Could not generate PDF: " + ex.getMessage());
            }
        });
        Button close = new Button("Close");
        close.setOnAction(event -> stage.close());
        HBox buttons = new HBox(10, printBtn, close);
        buttons.setAlignment(Pos.CENTER);

        VBox outer = new VBox(16, receipt1, buttons);
        outer.setAlignment(Pos.CENTER);
        outer.setPadding(new Insets(20));
        outer.setStyle("-fx-background-color: #0f0524;");

        Scene scene = new Scene(outer);
        scene.getStylesheets().add(PaymentSuccessDialog.class.getResource("/tokyoera/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.sizeToScene();

        // Stamp bounce animation after 400ms
        // Stamp bounce animation after 400ms (two-step bounce, no invalid SPLINE values)
        PauseTransition delay = new PauseTransition(Duration.millis(420));
        delay.setOnFinished(e -> {
            ScaleTransition grow = new ScaleTransition(Duration.millis(260), stamp);
            grow.setFromX(0); grow.setFromY(0);
            grow.setToX(1.15); grow.setToY(1.15);
            grow.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition settle = new ScaleTransition(Duration.millis(140), stamp);
            settle.setToX(1.0); settle.setToY(1.0);
            settle.setInterpolator(Interpolator.EASE_IN);
            new SequentialTransition(grow, settle).play();
        });
        stage.setOnShown(e -> delay.play());

        stage.showAndWait();
    }

    // Creates a styled dashed-line separator label for the receipt
    private static Label dash(String mono) {
        Label d = new Label("- - - - - - - - - - - - - - - - - - - - - - -");
        d.setStyle(mono + "-fx-font-size: 10px; -fx-text-fill: #ccc;");
        d.setMaxWidth(Double.MAX_VALUE);
        return d;
    }
}
