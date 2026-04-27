package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.AdminOrder;
import tokyoera.model.ClothingType;
import tokyoera.model.CustomOrderItem;
import tokyoera.model.Product;
import tokyoera.model.SalesRecord;
import tokyoera.model.User;
import tokyoera.service.CouponService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;

// The admin's control panel — a full management screen with tabs for products,
// stock, orders, custom orders, a sales dashboard with charts, and settings.
// Only accessible to users with the ADMIN role.
public class AdminDashboardView {
    // Default material description used when creating a new product
    private static final String DEFAULT_PRODUCT_MATERIAL = "100% combed and ring-spun cotton, 4.2 oz./yd², pre-shrunk, side-seamed, shoulder taping";

    // Shared inline CSS style strings — defined once here so every method uses the same look
    private static final String SIDEBAR_BG   = "-fx-background-color: #0d0d1a;";
    private static final String CONTENT_BG   = "-fx-background-color: #0a0a14;";
    private static final String CARD_STYLE   = "-fx-background-color: #12122a; -fx-background-radius: 8; -fx-padding: 16;";
    private static final String NAV_IDLE     = "-fx-background-color: transparent; -fx-text-fill: #7FF8FF; "
            + "-fx-font-size: 13px; -fx-alignment: CENTER_LEFT; -fx-padding: 10 16; -fx-cursor: hand;";
    private static final String NAV_ACTIVE   = "-fx-background-color: #1a1a3a; -fx-text-fill: #39ff88; "
            + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-alignment: CENTER_LEFT; "
            + "-fx-padding: 10 16; -fx-cursor: hand; "
            + "-fx-border-color: transparent transparent transparent #39ff88; -fx-border-width: 0 0 0 3;";
    private static final String SECTION_TITLE = "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #39ff88;";
    private static final String[] ORDER_STATUSES = {"PENDING", "PAID", "CANCELLED"};

    // Dependencies injected from AppContext
    private final AppContext context;
    private final User user;
    private final Runnable onLogout; // called when the admin clicks Logout

    private int lowStockThreshold;      // loaded from settings; determines Low Stock badge
    private Button activeNavBtn;        // the currently highlighted sidebar button
    private Button stockNavBtn;         // reference kept so we can update the warning badge on it
    private BorderPane root;            // the main layout container

    // Each tab has its own TableView — declared as fields so refreshX() methods can update them
    private final TableView<Product>         productTable     = new TableView<>();
    private final TableView<Product>         stockTable       = new TableView<>();
    private final TableView<AdminOrder>      orderTable       = new TableView<>();
    private final TableView<SalesRecord>     reportTable      = new TableView<>();
    private final TableView<CustomOrderItem> customOrderTable = new TableView<>();
    private final List<CheckBox> newTagChecks = new ArrayList<>();       // one per product in the badge settings section
    private final Map<Integer, TextField> badgeTextFields = new LinkedHashMap<>(); // product ID -> badge text input
    private final TextField productSearchField = new TextField();        // live search box above the product table
    private javafx.beans.value.ChangeListener<Product> stockSelectionListener; // kept so we can remove/re-add it on rebuild
    private List<Product> allCachedProducts = new ArrayList<>();         // used by live search so we don't re-query on every keystroke

    public AdminDashboardView(AppContext context, User user, Runnable onLogout) {
        this.context  = context;
        this.user     = user;
        this.onLogout = onLogout;
    }

    // Builds the full admin scene: loads settings, wires up sidebar, shows default Dashboard tab
    public Scene build() {
        lowStockThreshold = context.settingsService().getInt("low_stock_threshold", 10);
        root = new BorderPane();
        root.setStyle(CONTENT_BG);
        root.setLeft(buildSidebar());
        showSection("Dashboard");
        refreshStockBadge(); // update the warning count on the Stock Management nav button

        // Overlay the music toggle button in the top-right corner
        Button soundBtn = buildSoundButton();
        StackPane shell = new StackPane(root, soundBtn);
        StackPane.setAlignment(soundBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(soundBtn, new Insets(12, 32, 12, 12));

        Scene scene = new Scene(shell, 1380, 900);
        scene.getStylesheets().add(
                getClass().getResource("/tokyoera/styles.css").toExternalForm());
        return scene;
    }

    // Builds the floating Music On/Off toggle button overlaid in the top-right corner
    private Button buildSoundButton() {
        Button soundBtn = new Button();
        soundBtn.getStyleClass().add("neon-btn");
        soundBtn.setStyle(soundBtn.getStyle()
                + " -fx-min-width: 92px;"
                + " -fx-padding: 8 10;"
                + " -fx-background-color: linear-gradient(to bottom, #203a4f, #122633), linear-gradient(to right, rgba(127,248,255,0.24), rgba(57,255,136,0.10));"
                + " -fx-background-insets: 0, 1;"
                + " -fx-background-radius: 10, 9;"
                + " -fx-border-color: #7FF8FF;"
                + " -fx-border-width: 1.2;"
                + " -fx-border-radius: 10;"
                + " -fx-text-fill: #dffcff;");
        Runnable refresh = () -> soundBtn.setText(context.musicService().isMuted() ? "Music Off" : "Music On");
        refresh.run();
        soundBtn.setOnAction(e -> {
            context.musicService().toggleMute();
            refresh.run();
        });
        return soundBtn;
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────

    // Builds the left-side navigation column with the brand name, section buttons, and logout
    private VBox buildSidebar() {
        Label brand = new Label("TokyoEra");
        brand.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #39ff88; -fx-padding: 20 16 4 16;");
        Label sub = new Label("Admin Panel");
        sub.setStyle("-fx-font-size: 11px; -fx-text-fill: #7FF8FF; -fx-padding: 0 16 16 16;");

        VBox nav = new VBox(2,
                navBtn("Dashboard"),
                navBtn("Product Management"),
                (stockNavBtn = navBtn("Stock Management")),
                navBtn("Orders"),
                navBtn("Custom Orders"),
            navBtn("More Settings")
        );
        nav.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(nav, Priority.ALWAYS);

        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle(NAV_IDLE + " -fx-text-fill: #ff6b6b;");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> onLogout.run());
        VBox.setMargin(logoutBtn, new Insets(0, 0, 12, 0));

        VBox sidebar = new VBox(brand, sub, nav, logoutBtn);
        sidebar.setStyle(SIDEBAR_BG);
        sidebar.setPrefWidth(200);
        sidebar.setMinWidth(200);
        return sidebar;
    }

    // Creates a sidebar navigation button that swaps the center panel when clicked
    private Button navBtn(String label) {
        Button btn = new Button(label);
        btn.setStyle(NAV_IDLE);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> { setActiveNav(btn); showSection(label); });
        return btn;
    }

    // Highlights the clicked nav button and un-highlights the previous one
    private void setActiveNav(Button btn) {
        if (activeNavBtn != null) activeNavBtn.setStyle(NAV_IDLE);
        btn.setStyle(NAV_ACTIVE);
        activeNavBtn = btn;
    }

    // Switches the center content area to the panel matching the given section name
    private void showSection(String name) {
        Node panel = switch (name) {
            case "Dashboard"     -> buildDashboardPanel();
            case "Product Management" -> buildProductsPanel();
            case "Stock Management"   -> buildStockPanel();
            case "Orders"        -> buildOrdersPanel();
            case "Custom Orders" -> buildCustomOrdersPanel();
            case "More Settings" -> buildSettingsPanel();
            default              -> new Label("Coming soon");
        };
        root.setCenter(panel);
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    // Builds the analytics overview panel with filter controls, stat cards, and three charts.
    // The refreshDashboard lambda recalculates everything each time a filter changes.
    private Node buildDashboardPanel() {
        // Date range pickers let the admin filter all stats by a custom period
        DatePicker startPicker = new DatePicker();
        startPicker.setPromptText("Start date");
        DatePicker endPicker = new DatePicker();
        endPicker.setPromptText("End date");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("All", "Hoodie", "Sweatshirt", "T-shirt");
        typeBox.setValue("All");

        // Each dynVal label gets updated by refreshDashboard; color = visual priority
        Label revVal     = dynVal("#39ff88");
        Label ordersVal  = dynVal("#7FF8FF");
        Label avgVal     = dynVal("#c77dff");
        Label maxVal     = dynVal("#ffaa00");
        Label minVal     = dynVal("#ff6b6b");
        Label qtyVal     = dynVal("#c77dff");
        Label lowStkVal  = dynVal("#ffaa00");
        Label outStkVal  = dynVal("#ff6b6b");

        // Row 1 of stat cards: revenue, order count, average, max single order
        HBox cards1 = new HBox(12,
                dynCard("Total Revenue (PAID)", revVal),
                dynCard("Total Orders", ordersVal),
                dynCard("Avg Order Value", avgVal),
                dynCard("Max Sale per Order", maxVal)
        );
        // Row 2 of stat cards: min order, total items, low stock count, out of stock count
        HBox cards2 = new HBox(12,
                dynCard("Min Sale per Order", minVal),
                dynCard("Total Items Sold", qtyVal),
                dynCard("Low Stock", lowStkVal),
            dynCard("Temporarily Out of Stock", outStkVal)
        );

        // Row 3 of stat cards: cost, net profit, profit margin %
        Label costVal   = dynVal("#ff9f43");
        Label profitVal = dynVal("#39ff88");
        Label marginVal = dynVal("#c77dff");

        HBox cards3 = new HBox(12,
                dynCard("Total Cost (RM)", costVal),
                dynCard("Net Profit (RM)", profitVal),
                dynCard("Profit Margin %", marginVal)
        );

        // Daily revenue line chart — x = date, y = RM
        CategoryAxis xAxisD = new CategoryAxis();
        xAxisD.setLabel("Date");
        NumberAxis salesYAxis = new NumberAxis();
        salesYAxis.setLabel("RM");
        LineChart<String, Number> salesChart = new LineChart<>(xAxisD, salesYAxis);
        salesChart.setTitle("Daily Sales (RM)");
        salesChart.setAnimated(false);
        salesChart.setMinHeight(220);

        CategoryAxis qtyXAxis = new CategoryAxis();
        qtyXAxis.setLabel("Date");
        NumberAxis qtyYAxis = new NumberAxis();
        qtyYAxis.setLabel("Items");
        LineChart<String, Number> quantityChart = new LineChart<>(qtyXAxis, qtyYAxis);
        quantityChart.setTitle("Daily Quantity Sold");
        quantityChart.setAnimated(false);
        quantityChart.setMinHeight(220);

        // Bar chart: total sales grouped by clothing category (Hoodie, T-Shirt, etc.)
        CategoryAxis categoryXAxis = new CategoryAxis();
        categoryXAxis.setLabel("Category");
        NumberAxis categoryYAxis = new NumberAxis();
        categoryYAxis.setLabel("RM");
        BarChart<String, Number> categoryChart = new BarChart<>(categoryXAxis, categoryYAxis);
        categoryChart.setTitle("Sales by Category (RM)");
        categoryChart.setAnimated(false);
        categoryChart.setLegendVisible(false);
        categoryChart.setMinHeight(220);

        Button exportBtn = new Button("Export CSV");
        exportBtn.setOnAction(e -> {
            if (reportTable.getItems().isEmpty()) { UiUtil.error("No report data to export."); return; }
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Dashboard CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            fc.setInitialFileName("dashboard_sales_report.csv");
            java.io.File file = fc.showSaveDialog(root.getScene() != null ? (Stage) root.getScene().getWindow() : null);
            if (file == null) return;
            try {
                context.reportService().exportToCsv(reportTable.getItems(), file.getAbsolutePath());
                infoDialog("Export Successful", "Saved to:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                UiUtil.error("Export failed: " + ex.getMessage());
            }
        });

        // The refreshDashboard lambda does all the heavy lifting:
        // query sales records, aggregate stats, rebuild chart data, and update all the labels
        Runnable refreshDashboard = () -> {
            String start = startPicker.getValue() != null
                    ? startPicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : "";
            String end = endPicker.getValue() != null
                    ? endPicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : "";
            String type = typeBox.getValue();

            List<SalesRecord> records = context.reportService().getSalesRecords(start, end, type).stream()
                    .filter(r -> !isEpochPlaceholder(r.getDate()))
                    .collect(Collectors.toList());
                reportTable.setItems(FXCollections.observableArrayList(records));

            Map<String, Object> orderAgg = context.reportService().getOrderAggregatesByType(start, end, type);
            double rev = (double) orderAgg.get("totalRevenue");
            int totalOrd = (int) orderAgg.get("totalOrders");
            double maxSale = (double) orderAgg.get("maxSale");
            double minSale = (double) orderAgg.get("minSale");

            int qtySold = records.stream().mapToInt(SalesRecord::getQuantity).sum();
            double avgOrder = totalOrd == 0 ? 0 : rev / totalOrd;

            Map<String, Object> stockStats = context.reportService().getDashboardStatsByDate(start, end, lowStockThreshold);
            int lowStock = (int) stockStats.get("lowStockCount");
            int outOfStock = (int) stockStats.get("outOfStockCount");

            revVal.setText(String.format("RM %.2f", rev));
            ordersVal.setText(String.valueOf(totalOrd));
            avgVal.setText(String.format("RM %.2f", avgOrder));
            maxVal.setText(String.format("RM %.2f", maxSale));
            minVal.setText(String.format("RM %.2f", minSale));
            qtyVal.setText(String.valueOf(qtySold));
            lowStkVal.setText(String.valueOf(lowStock));
            outStkVal.setText(String.valueOf(outOfStock));

            Map<String, Object> profitStats = context.reportService().getProfitStats(start, end);
            costVal.setText(String.format("RM %.2f", (double) profitStats.get("totalCost")));
            profitVal.setText(String.format("RM %.2f", (double) profitStats.get("totalProfit")));
            marginVal.setText(String.format("%.1f%%", (double) profitStats.get("profitMarginPercent")));

            Map<String, Double> dailySales = records.stream()
                    .filter(r -> r.getDate() != null && r.getDate().matches("\\d{4}-\\d{2}-\\d{2}"))
                    .collect(Collectors.groupingBy(SalesRecord::getDate, TreeMap::new,
                            Collectors.summingDouble(SalesRecord::getSalesAmount)));
            Map<String, Integer> dailyQty = records.stream()
                    .filter(r -> r.getDate() != null && r.getDate().matches("\\d{4}-\\d{2}-\\d{2}"))
                    .collect(Collectors.groupingBy(SalesRecord::getDate, TreeMap::new,
                            Collectors.summingInt(SalesRecord::getQuantity)));

            TreeSet<String> allDates = new TreeSet<>();
            allDates.addAll(dailySales.keySet());
            allDates.addAll(dailyQty.keySet());

            XYChart.Series<String, Number> salesSeries = new XYChart.Series<>();
            XYChart.Series<String, Number> qtySeries = new XYChart.Series<>();
                XYChart.Series<String, Number> categorySeries = new XYChart.Series<>();
            for (String d : allDates) {
                salesSeries.getData().add(new XYChart.Data<>(d, dailySales.getOrDefault(d, 0.0)));
                qtySeries.getData().add(new XYChart.Data<>(d, dailyQty.getOrDefault(d, 0)));
            }
                Map<String, Double> categorySales = records.stream()
                    .collect(Collectors.groupingBy(SalesRecord::getCategory, TreeMap::new,
                        Collectors.summingDouble(SalesRecord::getSalesAmount)));
                categorySales.forEach((category, amount) ->
                    categorySeries.getData().add(new XYChart.Data<>(category, amount)));
            salesChart.getData().setAll(salesSeries);
            salesChart.setLegendVisible(false);
            quantityChart.getData().setAll(qtySeries);
            quantityChart.setLegendVisible(false);
                categoryChart.getData().setAll(categorySeries);

        };

        // Wire up filters: re-run the refresh whenever any picker or type selector changes
        startPicker.setOnAction(e -> refreshDashboard.run());
        endPicker.setOnAction(e -> refreshDashboard.run());
        typeBox.setOnAction(e -> refreshDashboard.run());

        // Pre-fill the date pickers with the actual range of paid sales in the DB
        String[] range = context.reportService().getPaidSalesDateRange();
        if (range[0] != null && !range[0].isBlank()) {
            startPicker.setValue(java.time.LocalDate.parse(range[0]));
        }
        if (range[1] != null && !range[1].isBlank()) {
            endPicker.setValue(java.time.LocalDate.parse(range[1]));
        }

        HBox filterRow = new HBox(8,
                new Label("From:"), startPicker,
                new Label("To:"), endPicker,
                new Label("Type:"), typeBox,
                exportBtn
        );
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, sectionTitle("Dashboard"), filterRow, cards1, cards2, cards3, salesChart, quantityChart, categoryChart);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        Platform.runLater(refreshDashboard::run);
        return scroll;
    }

    // Creates a bold "—" placeholder label in the given color; value gets filled in by refreshDashboard
    private Label dynVal(String color) {
    Label lbl = new Label("—");
    lbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: " + color + ";");
    return lbl;
    }

    // Wraps a dynVal label in a dark card with a description label underneath
    private VBox dynCard(String label, Label valueLabel) {
    Label keyLabel = new Label(label);
    keyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aabbcc;");
    VBox card = new VBox(4, valueLabel, keyLabel);
    card.setStyle(CARD_STYLE);
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
    }
    // ── Products CRUD ────────────────────────────────────────────────────────

    // Full product creation and editing form.
    // Clicking a row in the table fills all the fields so the admin can edit/delete it.
    // The Create button saves a new product; Update modifies the selected one; Delete removes it.
    private Node buildProductsPanel() {
        configureProductColumns();
        refreshProducts();

        TextField idField          = roField("(auto)");
        TextField nameField        = field("Product Name *");
        TextField priceField       = field("Price *");
        TextField originalCostField = roField("");
        TextField stockField       = field("Stock *");
        ComboBox<String> typeBox   = typeCombo();
        TextField imagePrefixField = field("Image Prefix");
        TextField colorField       = field("Color"); colorField.setText("black");
        TextField defaultEnField   = field("Left Message *");
        TextField defaultJpField   = field("Right Message *");
        TextField backMsgField     = field("Back Message *");
        TextField customFeeField   = field("Customization Fee"); customFeeField.setText("20");
        CheckBox  customCheck      = new CheckBox("Customization Allowed"); customCheck.setSelected(true);
        TextField sizesField       = field("Sizes CSV"); sizesField.setText("XS,S,M,L,XL,2X,3X");
        TextArea  descArea         = ta("Description", 3);

        Runnable updateCostField = () -> {
            String selected = typeBox.getValue();
            if (selected == null || selected.isBlank()) {
                originalCostField.setText("");
                return;
            }
            ClothingType type = ClothingType.valueOf(selected);
            originalCostField.setText(String.format("RM %.2f", type.getCostPrice()));
        };
        typeBox.setOnAction(e -> updateCostField.run());
        updateCostField.run();

        Button createBtn = neonBtn("Create");
        Button updateBtn = neonBtn("Update");
        Button deleteBtn = dangerBtn("Delete");
        Button clearBtn  = new Button("Clear");

        createBtn.setOnAction(e -> {
            try {
                Product p = buildProductFromForm(0, nameField, priceField, stockField, typeBox,
                        imagePrefixField, colorField, defaultEnField, defaultJpField,
                    backMsgField, customCheck, customFeeField, sizesField, descArea);
                context.productService().create(p);
                refreshProducts();
                clearForm(idField, nameField, priceField, stockField, typeBox, imagePrefixField,
                        originalCostField, colorField, defaultEnField, defaultJpField, backMsgField, customCheck, customFeeField,
                    sizesField, descArea);
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        updateBtn.setOnAction(e -> {
            try {
                String raw = trimOrEmpty(idField);
                if (raw.isBlank() || raw.equals("(auto)")) throw new IllegalArgumentException("Select a product row first.");
                int id = Integer.parseInt(raw);
                Product p = buildProductFromForm(id, nameField, priceField, stockField, typeBox,
                        imagePrefixField, colorField, defaultEnField, defaultJpField,
                    backMsgField, customCheck, customFeeField, sizesField, descArea);
                context.productService().update(p);
                refreshProducts();
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        deleteBtn.setOnAction(e -> {
            try {
                String raw = trimOrEmpty(idField);
                if (raw.isBlank() || raw.equals("(auto)")) throw new IllegalArgumentException("Select a product row first.");
                int id = Integer.parseInt(raw);
                if (!confirmDialog("Delete Product", "Delete product ID " + id + "? This cannot be undone.")) return;
                context.productService().delete(id);
                refreshProducts();
                clearForm(idField, nameField, priceField, stockField, typeBox, imagePrefixField,
                    originalCostField, colorField, defaultEnField, defaultJpField, backMsgField, customCheck, customFeeField,
                    sizesField, descArea);
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        clearBtn.setOnAction(e -> clearForm(idField, nameField, priceField, stockField, typeBox,
                imagePrefixField, originalCostField, colorField, defaultEnField, defaultJpField, backMsgField, customCheck,
                customFeeField, sizesField, descArea));

        productTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) return;
            idField.setText(String.valueOf(sel.getId()));
            nameField.setText(sel.getName());
            priceField.setText(String.valueOf(sel.getPrice()));
            stockField.setText(String.valueOf(sel.getStock()));
            typeBox.setValue(sel.getClothingType().name());
            originalCostField.setText(String.format("RM %.2f", sel.getClothingType().getCostPrice()));
            imagePrefixField.setText(sel.getImagePrefix());
            colorField.setText(sel.getColor());
            defaultEnField.setText(sel.getDefaultEnglishMessage());
            defaultJpField.setText(sel.getDefaultJapaneseMessage());
            backMsgField.setText(extractDetail(sel.getDescription(), "Back", "Vaporwave-inspired TokyoEra visual language"));
            customCheck.setSelected(sel.isCustomizationAllowed());
            customFeeField.setText(String.valueOf(sel.getCustomizationFee()));
            sizesField.setText(sel.getSizes());
            descArea.setText(stripDesignDetails(sel.getDescription()));
        });

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(4);
        grid.addRow(0, fLabel("ID"), idField, new Label(""), new Label(""));
        grid.addRow(1, fLabel("Name"), nameField, fLabel("Image Prefix"), imagePrefixField);
        grid.addRow(2, fLabel("Stock"), stockField, fLabel("Left Msg"), defaultEnField);
        grid.addRow(3, fLabel("Price"), priceField, fLabel("Color"), colorField);
        grid.addRow(4, fLabel("Original Cost"), originalCostField, fLabel("Right Msg"), defaultJpField);
        grid.addRow(5, fLabel("Custom Fee"), customFeeField, fLabel("Back Msg"), backMsgField);
        grid.addRow(6, fLabel("Description"), descArea, fLabel("Sizes"), sizesField);
        grid.addRow(7, new Label(""), new Label(""), fLabel("Type"), typeBox);
        grid.addRow(8, new Label(""), new Label(""), customCheck, new Label(""));
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(imagePrefixField, Priority.ALWAYS);
        GridPane.setHgrow(stockField, Priority.ALWAYS);
        GridPane.setHgrow(priceField, Priority.ALWAYS);
        GridPane.setHgrow(colorField, Priority.ALWAYS);
        GridPane.setHgrow(originalCostField, Priority.ALWAYS);
        GridPane.setHgrow(defaultEnField, Priority.ALWAYS);
        GridPane.setHgrow(defaultJpField, Priority.ALWAYS);
        GridPane.setHgrow(backMsgField, Priority.ALWAYS);
        GridPane.setHgrow(sizesField, Priority.ALWAYS);
        GridPane.setHgrow(descArea, Priority.ALWAYS);

        HBox actions = new HBox(8, createBtn, updateBtn, deleteBtn, clearBtn);
        actions.setPadding(new Insets(8, 0, 0, 0));

        productSearchField.setPromptText("Search products by name or type...");
        productSearchField.setMaxWidth(320);
        productSearchField.textProperty().addListener((obs, old, text) -> applyProductSearch());

        VBox.setVgrow(productTable, Priority.ALWAYS);
        VBox content = new VBox(12, sectionTitle("Product Management"), grid, actions, productSearchField, productTable);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        return content;
    }

    // Fetches all products from the database and runs the active search filter on them
    private void refreshProducts() {
        allCachedProducts = context.productService().getAll();
        applyProductSearch();
    }

    // Filters the cached product list by the search box text and updates the table.
    // Searches both the product name and clothing type so "HOODIE" also matches type.
    private void applyProductSearch() {
        String q = productSearchField.getText() == null ? "" : productSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isBlank()) {
            productTable.setItems(FXCollections.observableArrayList(allCachedProducts));
        } else {
            productTable.setItems(FXCollections.observableArrayList(
                    allCachedProducts.stream()
                            .filter(p -> p.getName().toLowerCase(java.util.Locale.ROOT).contains(q)
                                    || p.getClothingType().name().toLowerCase(java.util.Locale.ROOT).contains(q))
                            .collect(Collectors.toList())
            ));
        }
    }

    // Sets up the product table columns — only runs once thanks to the isEmpty() guard
    private void configureProductColumns() {
        if (!productTable.getColumns().isEmpty()) return;
        productTable.getColumns().addAll(
                col("ID",    "id",           60),
                col("Name",  "name",         310),
                col("Type",  "clothingType", 100),
                col("Price", "price",         80),
                col("Stock", "stock",         60),
                col("Color", "color",         80),
                col("Image", "imagePrefix",  120)
        );
    }

    // Resets all product form fields back to their default blank/placeholder values
    private void clearForm(TextField idField, TextField nameField, TextField priceField,
                           TextField stockField, ComboBox<String> typeBox, TextField imagePrefixField,
                           TextField originalCostField,
                           TextField colorField, TextField defaultEnField, TextField defaultJpField,
                           TextField backMsgField,
                           CheckBox customCheck, TextField customFeeField, TextField sizesField,
                           TextArea descArea) {
        idField.setText("(auto)"); nameField.setText(""); priceField.setText(""); stockField.setText("");
        typeBox.setValue("HOODIE"); imagePrefixField.setText(""); colorField.setText("black");
        originalCostField.setText(String.format("RM %.2f", ClothingType.HOODIE.getCostPrice()));
        defaultEnField.setText(""); defaultJpField.setText(""); customCheck.setSelected(true);
        backMsgField.setText("");
        customFeeField.setText("20"); sizesField.setText("XS,S,M,L,XL,2X,3X");
        descArea.setText("");
        productTable.getSelectionModel().clearSelection();
    }

    // ── Stock ────────────────────────────────────────────────────────────────

    private Node buildStockPanel() {
        // Main stock page: one row for size selection + update, one row with threshold hint.
        configureStockColumns();
        refreshStock();

        Label thresholdInfo = new Label("Low stock threshold: " + lowStockThreshold);
        thresholdInfo.setStyle("-fx-text-fill: #ffaa00; -fx-font-size: 12px;");

        ComboBox<String> sizeBox = new ComboBox<>();
        sizeBox.setPromptText("Size");
        sizeBox.setPrefWidth(100);

        TextField sizeStockField = new TextField();
        sizeStockField.setPromptText("Stock for size");
        sizeStockField.setPrefWidth(140);

        Button updateStockBtn = neonBtn("Update Stock");
        updateStockBtn.setOnAction(e -> {
            // Update only the selected size stock. Total stock auto-syncs in service layer.
            Product sel = stockTable.getSelectionModel().getSelectedItem();
            if (sel == null) { UiUtil.error("Select a product row."); return; }
            String size = sizeBox.getValue();
            if (size == null || size.isBlank()) { UiUtil.error("Select a size."); return; }
            try {
                int val = Integer.parseInt(sizeStockField.getText().trim());
                if (val < 0) throw new IllegalArgumentException("Stock cannot be negative.");
                context.productService().updateSizeStock(sel.getId(), size, val);
                refreshStock();
                Product refreshed = stockTable.getItems().stream()
                        .filter(p -> p.getId() == sel.getId())
                        .findFirst()
                        .orElse(null);
                if (refreshed != null) {
                    stockTable.getSelectionModel().select(refreshed);
                    sizeBox.getItems().setAll(refreshed.getSizeList());
                    if (sizeBox.getItems().contains(size)) {
                        sizeBox.setValue(size);
                    } else if (!sizeBox.getItems().isEmpty()) {
                        sizeBox.setValue(sizeBox.getItems().get(0));
                    }
                }
            } catch (NumberFormatException ex) {
                UiUtil.error("Enter a valid integer.");
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        Runnable refreshSelectedSizeControls = () -> {
            // Keep size dropdown + input in sync whenever user picks another product row.
            Product sel = stockTable.getSelectionModel().getSelectedItem();
            String currentSize = sizeBox.getValue();
            sizeBox.getItems().clear();
            sizeBox.setValue(null);
            sizeStockField.clear();
            if (sel == null) {
                return;
            }
            List<String> sizes = sel.getSizeList();
            sizeBox.getItems().addAll(sizes);
            if (currentSize != null && sizes.contains(currentSize)) {
                sizeBox.setValue(currentSize);
            } else if (!sizes.isEmpty()) {
                sizeBox.setValue(sizes.get(0));
            }
        };

        if (stockSelectionListener != null) {
            stockTable.getSelectionModel().selectedItemProperty().removeListener(stockSelectionListener);
        }
        stockSelectionListener = (obs, oldSel, newSel) -> refreshSelectedSizeControls.run();
        stockTable.getSelectionModel().selectedItemProperty().addListener(stockSelectionListener);

        sizeBox.setOnAction(e -> {
            String selectedSize = sizeBox.getValue();
            Product sel = stockTable.getSelectionModel().getSelectedItem();
            if (sel == null || selectedSize == null || selectedSize.isBlank()) {
                sizeStockField.clear();
                return;
            }
            Map<String, Integer> stocks = context.productService().getSizeStocks(sel.getId(), sel.getSizeList(), sel.getStock());
            sizeStockField.setText(String.valueOf(stocks.getOrDefault(selectedSize, 0)));
        });

        refreshSelectedSizeControls.run();

        Button plusOne  = neonBtn("+1");
        Button minusOne = dangerBtn("−1");
        Button plusTen  = neonBtn("+10");
        Button minusTen = dangerBtn("−10");
        plusOne.setOnAction(e -> quickAdjustStock(sizeBox, sizeStockField, 1));
        minusOne.setOnAction(e -> quickAdjustStock(sizeBox, sizeStockField, -1));
        plusTen.setOnAction(e -> quickAdjustStock(sizeBox, sizeStockField, 10));
        minusTen.setOnAction(e -> quickAdjustStock(sizeBox, sizeStockField, -10));

        HBox controls = new HBox(10,
            new Label("Size:"), sizeBox, sizeStockField, updateStockBtn,
            new Label("  Quick ±:"), plusOne, minusOne, plusTen, minusTen);
        controls.setAlignment(Pos.CENTER_LEFT);

        stockTable.setPrefHeight(430);

        VBox.setVgrow(stockTable, Priority.ALWAYS);
        VBox content = new VBox(10, sectionTitle("Stock Management"), controls, thresholdInfo, stockTable);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        return content;
    }

    // Reloads the stock table from the database and updates the sidebar warning badge
    private void refreshStock() {
        stockTable.setItems(FXCollections.observableArrayList(context.productService().getAll()));
        refreshStockBadge();
    }

    // Checks how many products are at or below the low-stock threshold and shows a ⚠ count on the nav button
    private void refreshStockBadge() {
        if (stockNavBtn == null) return;
        long count = context.productService().getAll().stream()
                .filter(p -> p.getStock() <= lowStockThreshold)
                .count();
        if (count > 0) {
            stockNavBtn.setText("Stock Management  ⚠ " + count);
            stockNavBtn.setStyle(NAV_IDLE + " -fx-text-fill: #ffaa33;");
        } else {
            stockNavBtn.setText("Stock Management");
            if (stockNavBtn != activeNavBtn) stockNavBtn.setStyle(NAV_IDLE);
        }
    }

    private void quickAdjustStock(ComboBox<String> sizeBox, TextField sizeStockField, int delta) {
        // Quick +/- buttons update the currently selected size by small steps.
        Product sel = stockTable.getSelectionModel().getSelectedItem();
        if (sel == null) { UiUtil.error("Select a product row first."); return; }
        String size = sizeBox.getValue();
        if (size == null || size.isBlank()) { UiUtil.error("Select a size first."); return; }
        int current = context.productService().getSizeStocks(sel.getId(), sel.getSizeList(), sel.getStock())
                .getOrDefault(size, 0);
        int newVal = current + delta;
        if (newVal < 0) { UiUtil.error("Stock cannot go below 0."); return; }
        try {
            context.productService().updateSizeStock(sel.getId(), size, newVal);
            refreshStock();
            Product refreshed = stockTable.getItems().stream()
                    .filter(p -> p.getId() == sel.getId())
                    .findFirst()
                    .orElse(null);
            if (refreshed != null) {
                stockTable.getSelectionModel().select(refreshed);
            }
            sizeStockField.setText(String.valueOf(newVal));
        } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
    }

    // Sets up the stock table columns. Guard prevents columns being added twice if panel is re-built.
    // Includes a color-coded Status column: red = out of stock, orange = low, green = ok.
    private void configureStockColumns() {
        if (!stockTable.getColumns().isEmpty()) return;
        // Show both per-size values and total so admin can cross-check quickly.
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> bySizeCol = new TableColumn<>("Stock by Size");
        bySizeCol.setPrefWidth(300);
        bySizeCol.setCellValueFactory(data -> {
            Product product = data.getValue();
            Map<String, Integer> sizeStocks = context.productService()
                    .getSizeStocks(product.getId(), product.getSizeList(), product.getStock());
            String text = product.getSizeList().stream()
                    .map(size -> size + ":" + sizeStocks.getOrDefault(size, 0))
                    .collect(Collectors.joining("  "));
            return new javafx.beans.property.SimpleStringProperty(text.isBlank() ? "-" : text);
        });

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(120);
        statusCol.setCellValueFactory(data -> {
            int s = data.getValue().getStock();
            String lbl = s == 0 ? "Temporarily Out of Stock" : s < lowStockThreshold ? "Low Stock" : "In Stock";
            return new javafx.beans.property.SimpleStringProperty(lbl);
        });
        statusCol.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "Temporarily Out of Stock" -> "-fx-text-fill: #ff6b6b;";
                    case "Low Stock"    -> "-fx-text-fill: #ffaa00;";
                    default             -> "-fx-text-fill: #39ff88;";
                });
            }
        });
        stockTable.getColumns().addAll(
            col("ID",          "id",           55),
            col("Name",        "name",         220),
            col("Type",        "clothingType", 95),
            bySizeCol,
            col("Total Stock", "stock",         90),
                statusCol
        );
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    // Orders panel: table of all orders with a status filter and a Delete button.
    // 1970 epoch dates from uninitialized DB rows are stripped out before display.
    private Node buildOrdersPanel() {
        configureOrderColumns(orderTable, true);
        refreshOrders("All");

        ComboBox<String> filterBox = new ComboBox<>();
        filterBox.getItems().add("All");
        filterBox.getItems().addAll(ORDER_STATUSES);
        filterBox.setValue("All");
        filterBox.setOnAction(e -> refreshOrders(filterBox.getValue()));

        Button refreshBtn = neonBtn("Refresh");
        refreshBtn.setOnAction(e -> refreshOrders(filterBox.getValue()));

        Button deleteBtn = dangerBtn("Delete Order");
        deleteBtn.setOnAction(e -> {
            AdminOrder sel = orderTable.getSelectionModel().getSelectedItem();
            if (sel == null) { UiUtil.error("Select an order."); return; }
            if (!confirmDialog("Delete Order", "Delete order #" + sel.getOrderId() + " permanently?")) return;
            try {
                context.orderService().deleteOrder(sel.getOrderId());
                refreshOrders(filterBox.getValue());
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        HBox filterRow = new HBox(8, new Label("Filter:"), filterBox, refreshBtn, deleteBtn);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(orderTable, Priority.ALWAYS);
        VBox content = new VBox(10, sectionTitle("Orders"), filterRow, orderTable);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        return content;
    }

    // Loads all orders from DB, strips epoch placeholder dates, applies status filter
    private void refreshOrders(String filter) {
        List<AdminOrder> orders = context.orderService().getAllOrders(filter).stream()
                .filter(o -> !isEpochPlaceholder(o.getCreatedAt()))
                .collect(Collectors.toList());
        orderTable.setItems(FXCollections.observableArrayList(orders));
    }

    // Configures the order table columns — guard prevents duplicate columns on re-build
    private void configureOrderColumns(TableView<AdminOrder> table, boolean full) {
        if (!table.getColumns().isEmpty()) return;
        table.getColumns().addAll(
                col("Order ID",   "orderId",      70),
                col("Username",   "username",    130),
            col("Items",      "itemCount",    60),
                col("Total (RM)", "totalAmount", 110),
                col("Status",     "status",      120),
                col("Date",       "createdAt",   160)
        );
    }

    // ── Custom Orders ────────────────────────────────────────────────────────

    // Shows only items that have custom sleeve/back text — so the admin knows what to print.
    // Uses the same filter/refresh pattern as the regular orders panel.
    private Node buildCustomOrdersPanel() {
        configureCustomOrderColumns();
        refreshCustomOrders("All");

        ComboBox<String> filterBox = new ComboBox<>();
        filterBox.getItems().add("All");
        filterBox.getItems().addAll(ORDER_STATUSES);
        filterBox.setValue("All");
        filterBox.setOnAction(e -> refreshCustomOrders(filterBox.getValue()));

        Button refreshBtn = neonBtn("Refresh");
        refreshBtn.setOnAction(e -> refreshCustomOrders(filterBox.getValue()));

        Button deleteBtn = dangerBtn("Delete Order");
        deleteBtn.setOnAction(e -> {
            CustomOrderItem sel = customOrderTable.getSelectionModel().getSelectedItem();
            if (sel == null) { UiUtil.error("Select a custom order row."); return; }
            if (!confirmDialog("Delete Custom Order", "Delete order #" + sel.getOrderId() + " permanently?")) return;
            try {
                context.orderService().deleteOrder(sel.getOrderId());
                refreshCustomOrders(filterBox.getValue());
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        HBox toolbar = new HBox(8, new Label("Filter:"), filterBox, refreshBtn, deleteBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(customOrderTable, Priority.ALWAYS);
        VBox content = new VBox(12, sectionTitle("Custom Orders"), toolbar, customOrderTable);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        return content;
    }

    // Loads orders → filters by status → then further filters to only items that have custom text
    private void refreshCustomOrders(String statusFilter) {
        List<AdminOrder> allOrders = context.orderService().getAllOrders("All").stream()
                .filter(o -> !isEpochPlaceholder(o.getCreatedAt()))
                .collect(Collectors.toList());

        Stream<AdminOrder> statusStream = allOrders.stream();
        if (statusFilter != null && !statusFilter.equalsIgnoreCase("All")) {
            statusStream = statusStream.filter(o -> statusFilter.equalsIgnoreCase(o.getStatus()));
        }
        Set<Integer> allowedOrderIds = statusStream
                .map(AdminOrder::getOrderId)
                .collect(Collectors.toSet());

        List<CustomOrderItem> filtered = context.orderService().getCustomOrders().stream()
                .filter(item -> allowedOrderIds.contains(item.getOrderId()))
                .collect(Collectors.toList());
        customOrderTable.setItems(FXCollections.observableArrayList(filtered));
    }

    // Custom orders table columns — shows the product, who ordered it, and exactly what text to print
    private void configureCustomOrderColumns() {
        if (!customOrderTable.getColumns().isEmpty()) return;
        customOrderTable.getColumns().addAll(
                col("Order ID",    "orderId",           70),
                col("Product",     "productName",       200),
                col("Customer",    "customerUsername",  130),
                col("English Msg", "customEnglish",     200),
                col("Japanese Msg","customJapanese",    200),
                col("Fee (RM)",    "customizationFee",   90),
                col("Size",        "size",               60),
                col("Qty",         "quantity",            50)
        );
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    private Node buildSettingsPanel() {
        TextField thresholdField = new TextField(String.valueOf(lowStockThreshold));
        thresholdField.setPrefWidth(120);
        thresholdField.setPromptText("Threshold");

        Button saveBtn = neonBtn("Save Threshold");
        saveBtn.setOnAction(e -> {
            try {
                int val = Integer.parseInt(thresholdField.getText().trim());
                if (val < 0) throw new IllegalArgumentException("Threshold cannot be negative.");
                lowStockThreshold = val;
                context.settingsService().set("low_stock_threshold", String.valueOf(val));
                refreshStockBadge();
                infoDialog("Settings Saved", "Low stock threshold set to " + val + ".");
            } catch (NumberFormatException ex) {
                UiUtil.error("Enter a valid integer.");
            } catch (Exception ex) { UiUtil.error(ex.getMessage()); }
        });

        Label hint = new Label("Products with stock below this threshold are shown as 'Low Stock'.");
        hint.setStyle("-fx-text-fill: #aabbcc; -fx-font-size: 12px;");

        Set<Integer> currentNewIds = parseCsvToIntSet(context.settingsService().get("new_product_ids", ""));
        Map<Integer, String> currentBadgeTexts = parseBadgeTextMap(context.settingsService().get("product_badge_texts", ""));
        String legacyDefaultBadge = context.settingsService().get("new_badge_text", "NEW!");
        ObservableList<Product> allProducts = FXCollections.observableArrayList(context.productService().getAll());

        VBox newProductsBox = new VBox(6);
        newTagChecks.clear();
        badgeTextFields.clear();
        for (Product p : allProducts) {
            CheckBox cb = new CheckBox(p.getName() + " (ID " + p.getId() + ")");
            cb.setStyle("-fx-text-fill: #7FF8FF; -fx-font-weight: 600; -fx-font-size: 12px;");
            cb.setWrapText(true);
            cb.setUserData(p.getId());
            cb.setSelected(currentNewIds.contains(p.getId()));
            newTagChecks.add(cb);

            TextField badgeField = new TextField();
            badgeField.setPromptText("Badge text for this product (e.g. NEW!, 15% OFF)");
            String existingBadgeText = currentBadgeTexts.get(p.getId());
            if ((existingBadgeText == null || existingBadgeText.isBlank()) && cb.isSelected()) {
                existingBadgeText = legacyDefaultBadge;
            }
            if (existingBadgeText != null) {
                badgeField.setText(existingBadgeText);
            }
            badgeField.disableProperty().bind(cb.selectedProperty().not());
            badgeTextFields.put(p.getId(), badgeField);

            VBox row = new VBox(4, cb, badgeField);
            row.setStyle("-fx-padding: 4 2 8 2;");
            newProductsBox.getChildren().add(row);
        }

        ScrollPane newProductsScroll = new ScrollPane(newProductsBox);
        newProductsScroll.setFitToWidth(true);
        newProductsScroll.setPrefViewportHeight(150);

        Button saveNewBtn = neonBtn("Save Badge Settings");
        saveNewBtn.setOnAction(e -> {
            String csv = newTagChecks.stream()
                .filter(CheckBox::isSelected)
                .map(cb -> String.valueOf((int) cb.getUserData()))
                .collect(Collectors.joining(","));
            context.settingsService().set("new_product_ids", csv);

            Map<Integer, String> badgeTexts = new LinkedHashMap<>();
            for (CheckBox cb : newTagChecks) {
                if (!cb.isSelected()) {
                    continue;
                }
                int productId = (int) cb.getUserData();
                TextField tf = badgeTextFields.get(productId);
                String badgeText = tf == null ? "" : trimOrEmpty(tf);
                if (badgeText.isBlank()) {
                    badgeText = "NEW!";
                }
                badgeTexts.put(productId, badgeText);
            }
            context.settingsService().set("product_badge_texts", serializeBadgeTextMap(badgeTexts));
            context.settingsService().set("new_badge_text", "NEW!");
            infoDialog("Settings Saved", "Per-product badge text and product selection have been updated.");
        });

        Label newHint = new Label("Select each product and enter the badge text to show in storefront (e.g. NEW!, 15% OFF). ");
        newHint.setStyle("-fx-text-fill: #aabbcc; -fx-font-size: 12px;");

        VBox saveRow = new VBox(6, saveNewBtn, newHint);

        VBox lowStockSection = new VBox(8,
            new Label("Low Stock Threshold"),
            new HBox(8, thresholdField, saveBtn),
            hint
        );
        lowStockSection.setMaxWidth(Double.MAX_VALUE);

        TextField couponCodeField = new TextField();
        couponCodeField.setPromptText("Coupon code (e.g. HOODIE20)");

        TextField couponPercentField = new TextField();
        couponPercentField.setPromptText("Discount % (e.g. 15)");

        ComboBox<String> couponProductBox = new ComboBox<>();
        couponProductBox.getItems().add("All Products");
        context.productService().getAll().forEach(product ->
                couponProductBox.getItems().add(product.getId() + " - " + product.getName()));
        couponProductBox.setValue("All Products");

        TextField couponExpiryField = new TextField();
        couponExpiryField.setPromptText("Expiry YYYY-MM-DD (optional)");

        CheckBox couponActiveCheck = new CheckBox("Active");
        couponActiveCheck.setSelected(true);

        TableView<CouponService.CouponRow> couponTable = new TableView<>();
        couponTable.getColumns().addAll(
                col("Code", "code", 140),
                col("Type", "discountType", 90),
                col("Value", "discountValue", 90),
                col("Expiry", "expiryDate", 120),
                col("Active", "active", 80),
                col("Product ID", "appliesToProductId", 110)
        );
        couponTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        couponTable.setItems(FXCollections.observableArrayList(context.couponService().getAllCoupons()));
        couponTable.setPrefHeight(180);

        Button saveCouponBtn = neonBtn("Save Coupon");
        saveCouponBtn.setOnAction(e -> {
            try {
                String code = trimOrEmpty(couponCodeField).toUpperCase();
                if (code.isBlank()) throw new IllegalArgumentException("Coupon code is required.");
                double percent = Double.parseDouble(trimOrEmpty(couponPercentField));
                if (percent <= 0 || percent > 100) {
                    throw new IllegalArgumentException("Discount percentage must be between 0 and 100.");
                }

                Integer productId = null;
                String selected = couponProductBox.getValue();
                if (selected != null && !"All Products".equals(selected)) {
                    productId = Integer.parseInt(selected.substring(0, selected.indexOf(" - ")).trim());
                }

                String expiry = trimOrEmpty(couponExpiryField);
                if (!expiry.isBlank()) {
                    java.time.LocalDate.parse(expiry);
                } else {
                    expiry = null;
                }

                context.couponService().upsertPercentCoupon(code, percent, productId, expiry, couponActiveCheck.isSelected());
                couponTable.setItems(FXCollections.observableArrayList(context.couponService().getAllCoupons()));
                infoDialog("Coupon Saved", "Coupon " + code + " has been saved.");
            } catch (NumberFormatException ex) {
                UiUtil.error("Enter a valid discount percentage.");
            } catch (Exception ex) {
                UiUtil.error(ex.getMessage());
            }
        });

        Button deleteCouponBtn = dangerBtn("Delete Coupon");
        deleteCouponBtn.setOnAction(e -> {
            CouponService.CouponRow selected = couponTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiUtil.error("Select a coupon row first.");
                return;
            }
            if (!confirmDialog("Delete Coupon", "Delete coupon " + selected.getCode() + "?")) return;
            try {
                context.couponService().deleteCoupon(selected.getCode());
                couponTable.setItems(FXCollections.observableArrayList(context.couponService().getAllCoupons()));
            } catch (Exception ex) {
                UiUtil.error(ex.getMessage());
            }
        });

        couponTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) return;
            couponCodeField.setText(selected.getCode());
            couponPercentField.setText(String.valueOf(selected.getDiscountValue()));
            couponExpiryField.setText(selected.getExpiryDate() == null ? "" : selected.getExpiryDate());
            couponActiveCheck.setSelected(selected.isActive());
            if (selected.getAppliesToProductId() == null) {
                couponProductBox.setValue("All Products");
            } else {
                String option = couponProductBox.getItems().stream()
                        .filter(v -> v.startsWith(selected.getAppliesToProductId() + " - "))
                        .findFirst()
                        .orElse("All Products");
                couponProductBox.setValue(option);
            }
        });

        VBox couponSection = new VBox(10,
                new Label("Coupon Settings"),
                new HBox(8, couponCodeField, couponPercentField),
                new HBox(8, couponProductBox, couponExpiryField, couponActiveCheck),
                new HBox(8, saveCouponBtn, deleteCouponBtn),
                couponTable
        );
        couponSection.setMaxWidth(Double.MAX_VALUE);

        VBox storefrontSection = new VBox(8,
            new Label("Storefront Badge Settings"),
            newProductsScroll,
            saveRow
        );
        storefrontSection.setMaxWidth(Double.MAX_VALUE);

        VBox lowStockCard = new VBox(10, lowStockSection);
        lowStockCard.setStyle(CARD_STYLE);
        lowStockCard.setMaxWidth(Double.MAX_VALUE);

        VBox storefrontCard = new VBox(10, storefrontSection);
        storefrontCard.setStyle(CARD_STYLE);
        storefrontCard.setMaxWidth(Double.MAX_VALUE);

        VBox couponCard = new VBox(10, couponSection);
        couponCard.setStyle(CARD_STYLE);
        couponCard.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(20, sectionTitle("More Settings"), lowStockCard, storefrontCard, couponCard);
        content.setPadding(new Insets(24));
        content.setStyle(CONTENT_BG);
        return content;
    }

    // ── Product form builder + validation ────────────────────────────────────

    private Product buildProductFromForm(int id,
                                         TextField nameField, TextField priceField, TextField stockField,
                                         ComboBox<String> typeBox, TextField imagePrefixField,
                                         TextField colorField, TextField defaultEnField, TextField defaultJpField,
                                         TextField backMsgField,
                                         CheckBox customCheck, TextField customFeeField,
                                         TextField sizesField,
                                         TextArea descArea) {
        String name = trimOrEmpty(nameField);
        if (name.isBlank()) throw new IllegalArgumentException("Product name is required.");
        String defaultEn = trimOrEmpty(defaultEnField);
        if (defaultEn.isBlank()) throw new IllegalArgumentException("Default English message is required.");
        String defaultJp = trimOrEmpty(defaultJpField);
        if (defaultJp.isBlank()) throw new IllegalArgumentException("Default Japanese message is required.");
        String backMsg = trimOrEmpty(backMsgField);
        if (backMsg.isBlank()) throw new IllegalArgumentException("Back message is required.");

        double price;
        try { price = Double.parseDouble(priceField.getText().trim()); if (price <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Price must be > 0."); }

        int stock;
        try { stock = Integer.parseInt(stockField.getText().trim()); if (stock < 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Stock must be >= 0."); }

        double fee;
        try { fee = Double.parseDouble(customFeeField.getText().trim()); if (fee < 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Customization fee must be >= 0."); }

        ClothingType type = ClothingType.valueOf(typeBox.getValue());
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setDescription(buildDescriptionWithDesignDetails(trimOrEmpty(descArea), defaultEn, defaultJp, backMsg));
        p.setPrice(price);
        p.setStock(stock);
        p.setClothingType(type);
        p.setColor(trimOrEmpty(colorField).isBlank() ? "black" : trimOrEmpty(colorField));
        p.setDefaultEnglishMessage(defaultEn);
        p.setDefaultJapaneseMessage(defaultJp);
        p.setCustomizationAllowed(customCheck.isSelected());
        p.setCustomizationFee(fee);
        p.setSizes(trimOrEmpty(sizesField).isBlank() ? "XS,S,M,L,XL" : trimOrEmpty(sizesField));
        p.setMaterial(DEFAULT_PRODUCT_MATERIAL);
        p.setIncludesInfo("");
        p.setImagePrefix(trimOrEmpty(imagePrefixField));
        return p;
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <S, T> TableColumn<S, T> col(String title, String property, double width) {
        TableColumn<S, T> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        c.setPrefWidth(width);
        return c;
    }

    private Label sectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle(SECTION_TITLE + " -fx-padding: 0 0 8 0;");
        return lbl;
    }

    private Label summaryLbl(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #7FF8FF; -fx-font-size: 12px;");
        return lbl;
    }

    private Label fLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #aabbcc; -fx-font-size: 12px;");
        return lbl;
    }

    private TextField roField(String text) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.setPrefWidth(80);
        tf.setStyle("-fx-text-fill: #555577;");
        return tf;
    }

    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        return tf;
    }

    private TextArea ta(String prompt, int rows) {
        TextArea ta = new TextArea();
        ta.setPromptText(prompt);
        ta.setPrefRowCount(rows);
        return ta;
    }

    private ComboBox<String> typeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll("HOODIE", "SWEATSHIRT", "T_SHIRT");
        cb.setValue("HOODIE");
        return cb;
    }

    private Button neonBtn(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("neon-btn");
        return btn;
    }

    private Button dangerBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #3a0d0d; -fx-text-fill: #ff6b6b; "
                + "-fx-border-color: #ff6b6b; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private String trimOrEmpty(TextField tf) { return tf.getText() == null ? "" : tf.getText().trim(); }
    private String trimOrEmpty(TextArea ta)   { return ta.getText() == null ? "" : ta.getText().trim(); }

    private boolean isEpochPlaceholder(String value) {
        return value != null && value.startsWith("1970-01-01");
    }

    private Set<Integer> parseCsvToIntSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Integer, String> parseBadgeTextMap(String raw) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split(",")) {
            String part = token == null ? "" : token.trim();
            if (part.isBlank()) {
                continue;
            }
            int sep = part.indexOf(':');
            if (sep <= 0 || sep >= part.length() - 1) {
                continue;
            }
            try {
                int productId = Integer.parseInt(part.substring(0, sep));
                String decoded = URLDecoder.decode(part.substring(sep + 1), StandardCharsets.UTF_8);
                if (!decoded.isBlank()) {
                    out.put(productId, decoded);
                }
            } catch (Exception ignored) {
                // skip malformed entries
            }
        }
        return out;
    }

    private String serializeBadgeTextMap(Map<Integer, String> map) {
        return map.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + ":" + URLEncoder.encode(e.getValue().trim(), StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
    }

    private String extractDetail(String description, String key, String fallback) {
        if (description == null || description.isBlank()) {
            return fallback;
        }
        String prefix = key + ":";
        for (String line : description.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return value.isBlank() ? fallback : value;
            }
        }
        return fallback;
    }

    private String stripDesignDetails(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        int marker = description.indexOf("Design Details:");
        if (marker < 0) {
            return description.trim();
        }
        return description.substring(0, marker).trim();
    }

    private String buildDescriptionWithDesignDetails(String baseDescription, String leftMsg, String rightMsg, String backMsg) {
        String intro = baseDescription == null ? "" : baseDescription.trim();
        if (intro.isBlank()) {
            intro = "Tokyo Era is more than streetwear—it’s a statement. Designed to move with you, built to express who you are.";
        }
        return intro
                + "\nDesign Details:\n"
                + "Front: Embroidered Tokyo Era / 東京時代 logo\n"
                + "Left Sleeve: " + leftMsg.trim() + "\n"
                + "Right Sleeve: " + rightMsg.trim() + "\n"
                + "Back: " + backMsg.trim();
    }

    private boolean confirmDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void infoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message);
        alert.showAndWait();
    }
}
