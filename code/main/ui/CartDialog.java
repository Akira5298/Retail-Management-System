package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.CartItem;
import tokyoera.model.OrderReceipt;
import tokyoera.model.User;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

public class CartDialog {
    public static void show(Window owner, AppContext context, User user, Runnable onUpdated) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Cart");

        VBox itemBox = new VBox(8);
        Label totalLabel = new Label();
        totalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #00E6FF;");

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            itemBox.getChildren().clear();
            List<CartItem> items = context.cartService().snapshot();
            if (items.isEmpty()) {
                Label empty = new Label("The cart is empty...");
                empty.setStyle("-fx-text-fill: #00E6FF; -fx-font-style: italic;");
                itemBox.getChildren().add(empty);
            }
            for (CartItem item : items) {
                Label desc = new Label(item.getProduct().getName() + "\nSize: " + item.getSize()
                        + "\nCustomization: " + item.customizationLabel()
                        + "\nSubtotal: " + UiUtil.rm(item.subtotal()));
                desc.setWrapText(true);

                Button minus = new Button("-");
                Button plus = new Button("+");
                Label qty = new Label("Qty: " + item.getQuantity());
                Button remove = new Button("Remove");

                minus.setOnAction(event -> {
                    if (item.getQuantity() > 1) {
                        item.setQuantity(item.getQuantity() - 1);
                        refresh[0].run();
                        onUpdated.run();
                    }
                });
                plus.setOnAction(event -> {
                    // Keep cart quantity aligned with live stock for this exact size.
                    int availableForSize = context.productService()
                            .getAvailableStockForSize(item.getProduct().getId(), item.getSize());
                    if (item.getQuantity() < availableForSize) {
                        item.setQuantity(item.getQuantity() + 1);
                        refresh[0].run();
                        onUpdated.run();
                    } else {
                        UiUtil.sizeUnavailable(item.getSize());
                    }
                });
                remove.setOnAction(event -> {
                    context.cartService().remove(item);
                    refresh[0].run();
                    onUpdated.run();
                });

                HBox controls = new HBox(8, minus, plus, qty, remove);
                VBox row = new VBox(6, desc, controls);
                row.getStyleClass().add("card");
                itemBox.getChildren().add(row);
            }
            totalLabel.setText("Total: " + UiUtil.rm(context.cartService().total()));
        };

        context.cartService().getItems().addListener((ListChangeListener<CartItem>) change -> refresh[0].run());
        refresh[0].run();

        Button pay = new Button("Pay Order");
        pay.getStyleClass().add("neon-btn");

        double[] discountAmount = {0.0};
        String[] appliedCoupon = {null};

        Label discountLabel = new Label();
        discountLabel.setStyle("-fx-text-fill: #39ff88; -fx-font-weight: bold;");
        discountLabel.setVisible(false);

        Label finalTotalLabel = new Label();
        finalTotalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffd700;");
        finalTotalLabel.setVisible(false);

        TextField couponField = new TextField();
        couponField.setPromptText("Enter coupon code");
        couponField.setPrefWidth(160);

        Button applyBtn = new Button("Apply");
        applyBtn.setOnAction(event -> {
            String code = couponField.getText() == null ? "" : couponField.getText().trim().toUpperCase();
            if (code.isBlank()) { UiUtil.error("Please enter a coupon code."); return; }
            java.util.Optional<String> error = context.couponService().validate(code, context.cartService().snapshot());
            if (error.isPresent()) {
                UiUtil.error(error.get());
                return;
            }
            double subtotal = context.cartService().total();
            discountAmount[0] = context.couponService().getDiscountAmount(code, context.cartService().snapshot());
            appliedCoupon[0] = code;
            discountLabel.setText("Discount (" + code + "): -" + UiUtil.rm(discountAmount[0]));
            discountLabel.setVisible(true);
            finalTotalLabel.setText("Final Total: " + UiUtil.rm(Math.max(0, subtotal - discountAmount[0])));
            finalTotalLabel.setVisible(true);
        });

        HBox couponRow = new HBox(8, couponField, applyBtn);
        couponRow.setAlignment(Pos.CENTER_LEFT);

        pay.setOnAction(event -> {
            if (context.cartService().snapshot().isEmpty()) {
                UiUtil.error("Cannot pay with empty cart.");
                return;
            }

            try {
                OrderReceipt receipt = context.orderService().checkout(
                        user, context.cartService().snapshot(), "CARD",
                        appliedCoupon[0], discountAmount[0]);
                context.cartService().clearSavedCart(user.getId());
                context.cartService().clear();
                onUpdated.run();
                stage.close();
                Platform.runLater(() -> PaymentSuccessDialog.show(owner, context, receipt));
            } catch (Exception exception) {
                // If stock changed while user was paying, show the size-unavailable popup instead of raw error text.
                String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
                if (message.contains("insufficient stock") || message.contains("stock exceeded")) {
                    UiUtil.sizeUnavailable(null);
                } else {
                    UiUtil.error(exception.getMessage());
                }
            }
        });

        Button cancel = new Button("Cancel Order");
        cancel.setOnAction(event -> {
            if (context.cartService().snapshot().isEmpty()) {
                UiUtil.info("Cart", "Cart is already empty.");
                return;
            }
            if (UiUtil.confirm("Cancel Order", "Are you sure you want to cancel this order?")) {
                try {
                    context.orderService().saveCartAsOrder(user, context.cartService().snapshot(), "CANCELLED");
                    context.cartService().clearSavedCart(user.getId());
                    context.cartService().clear();
                    onUpdated.run();
                    refresh[0].run();
                } catch (Exception exception) {
                    UiUtil.error("Failed to track cancelled order: " + exception.getMessage());
                }
            }
        });

        Button close = new Button("Close Cart");
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(10, pay, cancel, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        ScrollPane scrollPane = new ScrollPane(itemBox);
        scrollPane.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setCenter(scrollPane);
        root.setBottom(new VBox(8, totalLabel, discountLabel, finalTotalLabel, couponRow, actions));
        root.getStyleClass().add("panel");

        Scene scene = new Scene(root, 760, 580);
        scene.getStylesheets().add(CartDialog.class.getResource("/tokyoera/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
