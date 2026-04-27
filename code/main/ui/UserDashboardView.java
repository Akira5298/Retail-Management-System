package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.CartItem;
import tokyoera.model.ClothingType;
import tokyoera.model.Product;
import tokyoera.model.User;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.util.Duration;

public class UserDashboardView {
    private final AppContext context;
    private final User user;
    private final Runnable onLogout;

    private final VBox rowsBox = new VBox(12);
    private final Label cartCountLabel = new Label("0");
    private final TextField searchField = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final ComboBox<String> sortFilter = new ComboBox<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    // Search preview popup
    private final Popup searchPopup = new Popup();
    private final HBox searchPreviewBox = new HBox(8);
    // Mini-cart panel components
    private final VBox miniCartItemsBox = new VBox(6);
    private final Label miniCartTotalLabel = new Label("Total: RM 0.00");
    private final Button openFullCartBtn = new Button("Open Full Cart");
    private StackPane overlayRoot;
    private Button floatCartButton;
    private Timeline titleGlitch;
    private ScrollPane mainScrollPane;

    public UserDashboardView(AppContext context, User user, Runnable onLogout) {
        this.context = context;
        this.user = user;
        this.onLogout = onLogout;
    }

    public Scene build() {
        VBox page = new VBox();

        HBox top = buildTopBar(page);

        rowsBox.setPadding(new Insets(10));
        rowsBox.setFillWidth(true);
        mainScrollPane = new ScrollPane(rowsBox);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        mainScrollPane.setPannable(true);

        // Keep animations active only for rows near the viewport to reduce scroll jank.
        mainScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> updateVisibleRowAnimations());
        mainScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> updateVisibleRowAnimations());
        rowsBox.boundsInLocalProperty().addListener((obs, oldVal, newVal) -> updateVisibleRowAnimations());

        VBox.setVgrow(mainScrollPane, Priority.ALWAYS);
        page.getChildren().setAll(top, mainScrollPane);

        refreshCartCount();
        renderRows();
        Platform.runLater(this::updateVisibleRowAnimations);

        // Feature 12: Mini-cart floating panel
        VBox miniCartPanel = buildMiniCartPanel();
        miniCartPanel.setTranslateX(320);
        miniCartPanel.setVisible(false);
        StackPane.setAlignment(miniCartPanel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(miniCartPanel, new Insets(0, 0, 16, 0));

        floatCartButton = new Button("🛒 " + context.cartService().itemCount());
        floatCartButton.setStyle("-fx-background-color: linear-gradient(to right, #ff4fd8, #8c52ff); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px; -fx-background-radius: 999; -fx-min-width: 82px; -fx-min-height: 82px; -fx-padding: 0; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(255,79,216,0.65), 22, 0.35, 0, 3);");
        StackPane.setAlignment(floatCartButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(floatCartButton, new Insets(0, 20, 20, 0));

        context.cartService().getItems().addListener((ListChangeListener<CartItem>) c -> {
            floatCartButton.setText("🛒 " + context.cartService().itemCount());
            if (miniCartPanel.isVisible()) refreshMiniCart();
        });
        floatCartButton.setOnAction(e -> {
            if (miniCartPanel.isVisible()) {
                TranslateTransition tt = new TranslateTransition(Duration.millis(220), miniCartPanel);
                tt.setToX(320);
                tt.setOnFinished(ev -> miniCartPanel.setVisible(false));
                tt.play();
            } else {
                refreshMiniCart();
                miniCartPanel.setVisible(true);
                TranslateTransition tt = new TranslateTransition(Duration.millis(300), miniCartPanel);
                tt.setFromX(320);
                tt.setToX(0);
                tt.play();
            }
        });

        overlayRoot = new StackPane(page, miniCartPanel, floatCartButton);

        Scene scene = new Scene(overlayRoot, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/tokyoera/styles.css").toExternalForm());

        openFullCartBtn.setOnAction(e -> {
            miniCartPanel.setVisible(false);
            CartDialog.show(scene.getWindow(), context, user, this::refreshCartCount);
        });

        return scene;
    }

    private HBox buildTopBar(Node sceneOwner) {
        Label title = new Label("~Tokyo Era~ by Akira Fukutomi");
        title.getStyleClass().addAll("heading", "motto-heading");
        titleGlitch = UiUtil.createMottoGlitchAnimation(title);
        titleGlitch.play();

        searchField.setPromptText("Search products...");
        searchField.textProperty().addListener((obs, old, text) -> {
            searchDebounce.setOnFinished(e -> {
                renderRows();
                updateSearchPreview(text, searchField);
            });
            searchDebounce.playFromStart();
        });
        // Feature 9: Search preview popup setup
        searchPreviewBox.setStyle("-fx-background-color: rgba(8, 8, 30, 0.97); -fx-padding: 8 10; -fx-background-radius: 0 0 10 10; -fx-border-color: #00E6FF; -fx-border-width: 0 1 1 1; -fx-border-radius: 0 0 10 10;");
        searchPreviewBox.setAlignment(Pos.CENTER_LEFT);
        searchPopup.getContent().add(searchPreviewBox);
        searchPopup.setAutoHide(true);
        searchPopup.setHideOnEscape(true);

        typeFilter.getItems().addAll("All", "Hoodie", "Sweatshirt", "T-shirt");
        typeFilter.setValue("All");
        typeFilter.valueProperty().addListener((obs, old, value) -> renderRows());

        sortFilter.getItems().addAll("All", "Newest", "A–Z", "Price low → high", "Price high → low", "Low stock", "In stock", "Hoodie", "Sweatshirt", "T-shirt");
        sortFilter.setValue("All");
        sortFilter.valueProperty().addListener((obs, old, value) -> renderRows());

        Button cartButton = new Button("🛒 Cart");
        Button ordersBtn = new Button("Order History");
        ordersBtn.getStyleClass().add("neon-btn");
        ordersBtn.setOnAction(event -> OrderHistoryDialog.show(sceneOwner.getScene().getWindow(), context, user));

        Button soundBtn = buildSoundButton();

        Button logout = new Button("Logout");
        logout.setOnAction(event -> onLogout.run());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox left = new HBox(10, title, searchField, typeFilter, sortFilter);
        left.setAlignment(Pos.CENTER_LEFT);

        HBox right = new HBox(8, soundBtn, ordersBtn, logout);
        right.setAlignment(Pos.CENTER_RIGHT);

        sceneOwner.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        newWindow.setOnHidden(event -> {
                            if (titleGlitch != null) {
                                titleGlitch.stop();
                            }
                        });
                    }
                });
            }
        });

        HBox top = new HBox(14, left, spacer, right);
        top.getStyleClass().add("top-bar");
        top.setAlignment(Pos.CENTER_LEFT);
        top.setViewOrder(-1000);
        top.toFront();
        return top;
    }

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

    private void renderRows() {
        rowsBox.getChildren().clear();
        List<Product> products = new ArrayList<>(context.productService().getAll());
        int lowStockThreshold = context.settingsService().getInt("low_stock_threshold", 10);

        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (!query.isBlank()) {
            products = products.stream()
                    .filter(product -> product.getName().toLowerCase(Locale.ROOT).contains(query)
                            || product.getDescription().toLowerCase(Locale.ROOT).contains(query))
                    .collect(Collectors.toList());
        }

        String selectedType = typeFilter.getValue();
        if ("Hoodie".equals(selectedType)) {
            products = products.stream().filter(product -> product.getClothingType() == ClothingType.HOODIE).collect(Collectors.toList());
        } else if ("Sweatshirt".equals(selectedType)) {
            products = products.stream().filter(product -> product.getClothingType() == ClothingType.SWEATSHIRT).collect(Collectors.toList());
        } else if ("T-shirt".equals(selectedType)) {
            products = products.stream().filter(product -> product.getClothingType() == ClothingType.T_SHIRT).collect(Collectors.toList());
        }

        switch (sortFilter.getValue()) {
            case "Newest" -> products.sort(Comparator.comparingInt(Product::getId).reversed());
            case "A–Z" -> products.sort(Comparator.comparing(Product::getName));
            case "Price low → high" -> products.sort(Comparator.comparingDouble(Product::getPrice));
            case "Price high → low" -> products.sort(Comparator.comparingDouble(Product::getPrice).reversed());
                case "Low stock" -> products = products.stream()
                    .filter(product -> product.getStock() > 0 && product.getStock() < lowStockThreshold)
                    .collect(Collectors.toList());
            case "In stock" -> products = products.stream().filter(product -> product.getStock() > 0).collect(Collectors.toList());
            case "Hoodie" -> products = products.stream().filter(product -> product.getClothingType() == ClothingType.HOODIE).collect(Collectors.toList());
            case "Sweatshirt" -> products = products.stream().filter(product -> product.getClothingType() == ClothingType.SWEATSHIRT).collect(Collectors.toList());
            case "T-shirt" -> products = products.stream().filter(product -> product.getClothingType() == ClothingType.T_SHIRT).collect(Collectors.toList());
            default -> {
            }
        }

        if (products.isEmpty()) {
            Label none = new Label("No products match your filters.");
            rowsBox.getChildren().add(none);
            return;
        }

        Set<Integer> newProductIds = parseCsvToIntSet(context.settingsService().get("new_product_ids", ""));
        Map<Integer, String> badgeTextByProduct = parseBadgeTextMap(context.settingsService().get("product_badge_texts", ""));
        String fallbackBadgeText = context.settingsService().get("new_badge_text", "NEW!");
        if (fallbackBadgeText == null || fallbackBadgeText.isBlank()) {
            fallbackBadgeText = "NEW!";
        }

        for (int rowIndex = 0; rowIndex < products.size(); rowIndex++) {
            Product product = products.get(rowIndex);
            double direction = (rowIndex % 2 == 0) ? 1.0 : -1.0;
            boolean showNewTag = newProductIds.contains(product.getId());
            String badgeText = badgeTextByProduct.getOrDefault(product.getId(), fallbackBadgeText);
            final GalleryRow[] rowRef = new GalleryRow[1];
                rowRef[0] = new GalleryRow(product, lowStockThreshold, clicked -> ProductDetailDialog.show(
                    rowsBox.getScene().getWindow(),
                    clicked,
                    lowStockThreshold,
                    request -> {
                        // Simple guard: check selected size stock before adding anything to cart.
                        int availableForSize = context.productService()
                                .getAvailableStockForSize(clicked.getId(), request.size());
                        if (availableForSize <= 0) {
                            UiUtil.sizeUnavailable(request.size());
                            return;
                        }
                        if (request.quantity() > availableForSize) {
                            UiUtil.sizeUnavailable(request.size());
                            return;
                        }
                        String customEn = request.customized() ? request.customEnglish() : clicked.getDefaultEnglishMessage();
                        String customJp = request.customized() ? request.customJapanese() : clicked.getDefaultJapaneseMessage();
                        context.cartService().addToCart(clicked, request.size(), request.quantity(), request.customized(), customEn, customJp);
                        playAddToCartAnimation(rowRef[0], request.quantity());
                        refreshCartCount();
                        }
                    ), direction, showNewTag, badgeText);
            GalleryRow row = rowRef[0];
            row.setMaxWidth(Double.MAX_VALUE);
            rowsBox.getChildren().add(row);
        }

        // After rows are rebuilt, recompute which ones should animate.
        Platform.runLater(this::updateVisibleRowAnimations);
    }

    private void updateVisibleRowAnimations() {
        if (mainScrollPane == null) {
            return;
        }

        Bounds viewport = mainScrollPane.getViewportBounds();
        double viewportHeight = viewport.getHeight();
        double contentHeight = rowsBox.getBoundsInLocal().getHeight();
        double scrollable = Math.max(0, contentHeight - viewportHeight);
        double viewportTop = mainScrollPane.getVvalue() * scrollable;
        double viewportBottom = viewportTop + viewportHeight;
        double padding = 120.0;

        for (Node node : rowsBox.getChildren()) {
            if (!(node instanceof GalleryRow row)) {
                continue;
            }
            double y = node.getBoundsInParent().getMinY();
            double h = node.getBoundsInParent().getHeight();
            boolean visible = (y + h) >= (viewportTop - padding) && y <= (viewportBottom + padding);
            row.setAnimationActive(visible);
        }
    }

    private void refreshCartCount() {
        cartCountLabel.setText(String.valueOf(context.cartService().itemCount()));
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

    // Feature 12: Mini-cart panel builder
    private VBox buildMiniCartPanel() {
        miniCartItemsBox.setPadding(new Insets(4, 12, 4, 12));
        ScrollPane sp = new ScrollPane(miniCartItemsBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(220);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        miniCartTotalLabel.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 14;");

        openFullCartBtn.getStyleClass().add("neon-btn");
        openFullCartBtn.setMaxWidth(Double.MAX_VALUE);
        HBox btnBar = new HBox(openFullCartBtn);
        HBox.setHgrow(openFullCartBtn, Priority.ALWAYS);
        btnBar.setPadding(new Insets(6, 12, 12, 12));

        Label header = new Label("🛒  Quick Cart");
        header.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-font-size: 15px; -fx-padding: 12 14 8 14; -fx-border-color: transparent transparent #00E6FF transparent; -fx-border-width: 0 0 1 0;");

        VBox panel = new VBox(0, header, sp, miniCartTotalLabel, btnBar);
        panel.setPrefWidth(290);
        panel.setMaxHeight(380);
        panel.setStyle("-fx-background-color: rgba(8, 8, 30, 0.97); -fx-background-radius: 14 0 0 14; -fx-border-color: #00E6FF; -fx-border-width: 1 0 1 1; -fx-border-radius: 14 0 0 14; -fx-effect: dropshadow(gaussian, rgba(0,230,255,0.25), 20, 0, -4, 0);");
        return panel;
    }

    private void refreshMiniCart() {
        miniCartItemsBox.getChildren().clear();
        List<CartItem> items = context.cartService().snapshot();
        if (items.isEmpty()) {
            Label empty = new Label("Cart is empty");
            empty.setStyle("-fx-text-fill: #7FF8FF; -fx-font-style: italic; -fx-font-size: 12px;");
            miniCartItemsBox.getChildren().add(empty);
        } else {
            for (CartItem item : items) {
                Label nameLabel = new Label(item.getProduct().getName() + "  ×" + item.getQuantity());
                nameLabel.setStyle("-fx-text-fill: #7FF8FF; -fx-font-size: 12px;");
                Label priceLabel = new Label(UiUtil.rm(item.subtotal()));
                priceLabel.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-font-size: 12px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(nameLabel, spacer, priceLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                miniCartItemsBox.getChildren().add(row);
            }
        }
        miniCartTotalLabel.setText("Total: " + UiUtil.rm(context.cartService().total()));
    }

    private void playAddToCartAnimation(Node sourceNode, int quantityAdded) {
        if (overlayRoot == null || floatCartButton == null || overlayRoot.getScene() == null) {
            return;
        }

        Node visualSource = sourceNode;
        if (sourceNode instanceof GalleryRow galleryRow) {
            ImageView pickedTile = galleryRow.pickRandomImageNode();
            if (pickedTile != null) {
                visualSource = pickedTile;
            }
        }

        Bounds sourceBounds = visualSource.localToScene(visualSource.getBoundsInLocal());
        Bounds overlayBounds = overlayRoot.localToScene(overlayRoot.getBoundsInLocal());
        Bounds cartBounds = floatCartButton.localToScene(floatCartButton.getBoundsInLocal());
        if (sourceBounds == null || overlayBounds == null || cartBounds == null) {
            return;
        }

        WritableImage snap = visualSource.snapshot(new SnapshotParameters(), null);
        ImageView ghost = new ImageView(snap);
        ghost.setMouseTransparent(true);
        ghost.setManaged(false);
        ghost.setOpacity(0.95);
        ghost.setScaleX(0.86);
        ghost.setScaleY(0.86);
        ghost.setRotate(-8);

        double overlayCenterX = overlayBounds.getMinX() + overlayBounds.getWidth() / 2.0;
        double overlayCenterY = overlayBounds.getMinY() + overlayBounds.getHeight() / 2.0;
        double sourceCenterX = sourceBounds.getMinX() + sourceBounds.getWidth() / 2.0;
        double sourceCenterY = sourceBounds.getMinY() + sourceBounds.getHeight() / 2.0;
        double cartCenterX = cartBounds.getMinX() + cartBounds.getWidth() / 2.0;
        double cartCenterY = cartBounds.getMinY() + cartBounds.getHeight() / 2.0;

        ghost.setTranslateX(sourceCenterX - overlayCenterX);
        ghost.setTranslateY(sourceCenterY - overlayCenterY);
        overlayRoot.getChildren().add(ghost);

        ImageView trailGhost = new ImageView(snap);
        trailGhost.setMouseTransparent(true);
        trailGhost.setManaged(false);
        trailGhost.setOpacity(0.42);
        trailGhost.setScaleX(0.58);
        trailGhost.setScaleY(0.58);
        trailGhost.setRotate(-16);
        trailGhost.setTranslateX(sourceCenterX - overlayCenterX);
        trailGhost.setTranslateY(sourceCenterY - overlayCenterY);
        overlayRoot.getChildren().add(trailGhost);

        TranslateTransition move = new TranslateTransition(Duration.millis(680), ghost);
        move.setToX(cartCenterX - overlayCenterX);
        move.setToY(cartCenterY - overlayCenterY);
        move.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(700), ghost);
        shrink.setToX(0.08);
        shrink.setToY(0.08);
        shrink.setInterpolator(Interpolator.EASE_IN);

        javafx.animation.RotateTransition spin = new javafx.animation.RotateTransition(Duration.millis(700), ghost);
        spin.setToAngle(28);
        spin.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition fade = new FadeTransition(Duration.millis(700), ghost);
        fade.setToValue(0.03);

        TranslateTransition trailMove = new TranslateTransition(Duration.millis(520), trailGhost);
        trailMove.setToX(cartCenterX - overlayCenterX);
        trailMove.setToY(cartCenterY - overlayCenterY + 12);
        trailMove.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition trailShrink = new ScaleTransition(Duration.millis(520), trailGhost);
        trailShrink.setToX(0.03);
        trailShrink.setToY(0.03);
        FadeTransition trailFade = new FadeTransition(Duration.millis(520), trailGhost);
        trailFade.setToValue(0.0);
        ParallelTransition trailAnim = new ParallelTransition(trailMove, trailShrink, trailFade);
        trailAnim.setOnFinished(e -> overlayRoot.getChildren().remove(trailGhost));
        trailAnim.play();

        ParallelTransition fly = new ParallelTransition(move, shrink, spin, fade);
        fly.setOnFinished(e -> overlayRoot.getChildren().remove(ghost));
        fly.play();

        ScaleTransition sourcePopOut = new ScaleTransition(Duration.millis(120), sourceNode);
        sourcePopOut.setToX(1.03);
        sourcePopOut.setToY(1.03);
        sourcePopOut.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition sourcePopIn = new ScaleTransition(Duration.millis(120), sourceNode);
        sourcePopIn.setToX(1.0);
        sourcePopIn.setToY(1.0);
        sourcePopIn.setInterpolator(Interpolator.EASE_IN);
        new SequentialTransition(sourcePopOut, sourcePopIn).play();

        ScaleTransition punchOut = new ScaleTransition(Duration.millis(160), floatCartButton);
        punchOut.setToX(1.38);
        punchOut.setToY(1.38);
        punchOut.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition settle1 = new ScaleTransition(Duration.millis(110), floatCartButton);
        settle1.setToX(0.92);
        settle1.setToY(0.92);
        settle1.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition settle2 = new ScaleTransition(Duration.millis(110), floatCartButton);
        settle2.setToX(1.0);
        settle2.setToY(1.0);
        settle2.setInterpolator(Interpolator.EASE_IN);

        new SequentialTransition(punchOut, settle1, settle2).play();

        Circle impactRing = new Circle(24);
        impactRing.setManaged(false);
        impactRing.setMouseTransparent(true);
        impactRing.setFill(Color.TRANSPARENT);
        impactRing.setStroke(Color.web("#00E6FF"));
        impactRing.setStrokeWidth(3.0);
        impactRing.setOpacity(0.95);
        impactRing.setTranslateX(cartCenterX - overlayCenterX);
        impactRing.setTranslateY(cartCenterY - overlayCenterY);
        overlayRoot.getChildren().add(impactRing);

        ScaleTransition ringExpand = new ScaleTransition(Duration.millis(320), impactRing);
        ringExpand.setToX(2.6);
        ringExpand.setToY(2.6);
        FadeTransition ringFade = new FadeTransition(Duration.millis(320), impactRing);
        ringFade.setToValue(0.0);
        ParallelTransition ringBurst = new ParallelTransition(ringExpand, ringFade);
        ringBurst.setOnFinished(e -> overlayRoot.getChildren().remove(impactRing));
        ringBurst.play();

        javafx.animation.TranslateTransition shakeLeft = new javafx.animation.TranslateTransition(Duration.millis(55), floatCartButton);
        shakeLeft.setToX(-6);
        javafx.animation.TranslateTransition shakeRight = new javafx.animation.TranslateTransition(Duration.millis(85), floatCartButton);
        shakeRight.setToX(7);
        javafx.animation.TranslateTransition shakeCenter = new javafx.animation.TranslateTransition(Duration.millis(60), floatCartButton);
        shakeCenter.setToX(0);
        new SequentialTransition(shakeLeft, shakeRight, shakeCenter).play();

        Label qtyBadge = new Label("+" + Math.max(1, quantityAdded));
        qtyBadge.setManaged(false);
        qtyBadge.setMouseTransparent(true);
        qtyBadge.setStyle("-fx-background-color: #00E6FF; -fx-text-fill: #101020; -fx-font-weight: bold; -fx-background-radius: 999; -fx-padding: 6 10;");
        StackPane.setAlignment(qtyBadge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(qtyBadge, new Insets(0, 35, 100, 0));
        overlayRoot.getChildren().add(qtyBadge);

        TranslateTransition badgeRise = new TranslateTransition(Duration.millis(560), qtyBadge);
        badgeRise.setFromY(0);
        badgeRise.setToY(-42);
        badgeRise.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition badgeFade = new FadeTransition(Duration.millis(560), qtyBadge);
        badgeFade.setFromValue(1.0);
        badgeFade.setToValue(0.0);
        ParallelTransition badgeAnim = new ParallelTransition(badgeRise, badgeFade);
        badgeAnim.setOnFinished(e -> overlayRoot.getChildren().remove(qtyBadge));
        badgeAnim.play();
    }

    // Feature 9: Live search preview
    private void updateSearchPreview(String query, TextField field) {
        if (field.getScene() == null || field.getScene().getWindow() == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            searchPopup.hide();
            return;
        }
        List<Product> matches = context.productService().getAll().stream()
                .filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(q)
                        || p.getDescription().toLowerCase(Locale.ROOT).contains(q))
                .limit(7)
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            searchPopup.hide();
            return;
        }
        searchPreviewBox.getChildren().clear();
        for (Product p : matches) {
            ImageView thumb = new ImageView(UiUtil.loadImageByBaseName(p.getImagePrefix() + "a"));
            thumb.setFitWidth(58);
            thumb.setFitHeight(62);
            thumb.setPreserveRatio(false);
            Label name = new Label(p.getName());
            name.setStyle("-fx-text-fill: #00E6FF; -fx-font-size: 10px; -fx-wrap-text: true;");
            name.setMaxWidth(58);
            Label price = new Label(UiUtil.rm(p.getPrice()));
            price.setStyle("-fx-text-fill: #7FF8FF; -fx-font-size: 10px;");
            VBox cell = new VBox(3, thumb, name, price);
            cell.setAlignment(Pos.TOP_CENTER);
            cell.setStyle("-fx-cursor: hand; -fx-padding: 4; -fx-background-radius: 6;");
            cell.setOnMouseEntered(ev -> cell.setStyle("-fx-cursor: hand; -fx-padding: 4; -fx-background-color: rgba(0,230,255,0.1); -fx-background-radius: 6;"));
            cell.setOnMouseExited(ev -> cell.setStyle("-fx-cursor: hand; -fx-padding: 4; -fx-background-radius: 6;"));
            cell.setOnMouseClicked(ev -> {
                field.setText(p.getName());
                searchPopup.hide();
            });
            searchPreviewBox.getChildren().add(cell);
        }
        Bounds bounds = field.localToScreen(field.getBoundsInLocal());
        if (bounds != null && field.getScene().getWindow().isShowing()) {
            searchPopup.show(field.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 2);
        }
    }
}
