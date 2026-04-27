package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.AdminOrder;
import tokyoera.model.User;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

// Modal table showing a user's full order history with color-coded status and a total spent summary.
public class OrderHistoryDialog {

    public static void show(Window owner, AppContext context, User user) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Order History — " + user.getUsername());

        List<AdminOrder> orders = context.orderService().getOrdersByUser(user.getId());

        TableColumn<AdminOrder, Integer> idCol = new TableColumn<>("Order ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        idCol.setPrefWidth(80);

        TableColumn<AdminOrder, Double> totalCol = new TableColumn<>("Total (RM)");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        totalCol.setPrefWidth(110);

        TableColumn<AdminOrder, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(130);
        // Color-code each status cell: green for PAID, red for CANCELLED, orange for PENDING
        statusCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "PAID"       -> "-fx-text-fill: #39ff88;";
                    case "CANCELLED"  -> "-fx-text-fill: #ff6b6b;";
                    case "PENDING"    -> "-fx-text-fill: #ffaa00;";
                    default           -> "-fx-text-fill: #ffaa00;";
                });
            }
        });

        TableColumn<AdminOrder, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateCol.setPrefWidth(170);

        TableView<AdminOrder> table = new TableView<>();
        table.getColumns().addAll(idCol, totalCol, statusCol, dateCol);
        table.setItems(FXCollections.observableArrayList(orders));
        table.setPrefHeight(380);

        if (orders.isEmpty()) {
            Label empty = new Label("You haven't placed any orders yet.");
            empty.setStyle("-fx-text-fill: #7FF8FF; -fx-font-style: italic;");
            table.setPlaceholder(empty);
        }

        // Sum only PAID orders for the "total spent" figure shown at the top
        double totalSpent = orders.stream()
            .filter(order -> "PAID".equals(order.getStatus()))
            .mapToDouble(AdminOrder::getTotalAmount)
            .sum();
        Label summary = new Label(String.format(
                "Total spent: RM %.2f  ·  %d order(s)", totalSpent, orders.size()));
        summary.setStyle("-fx-text-fill: #7FF8FF; -fx-font-size: 13px;");

        Label title = new Label("Order History — " + user.getUsername());
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #39ff88;");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("neon-btn");
        closeBtn.setOnAction(e -> stage.close());

        // Wrap close button in an HBox so it sits at the right edge
        HBox closeRow = new HBox(closeBtn);
        closeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(14, title, summary, table, closeRow);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0a0a14;");
        root.setAlignment(Pos.TOP_LEFT);

        Scene scene = new Scene(root, 550, 560);
        scene.getStylesheets().add(
                OrderHistoryDialog.class.getResource("/tokyoera/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
