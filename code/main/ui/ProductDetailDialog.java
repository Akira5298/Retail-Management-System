package tokyoera.ui;

import tokyoera.model.Product;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

// Full-screen product detail popup with a card-flip animation.
// Front side shows the image carousel with a zoom lens; back side shows design details and the add-to-cart form.
public class ProductDetailDialog {

    // Data the caller receives when the user clicks "Add to Cart"
    public record AddToCartRequest(String size, int quantity, boolean customized, String customEnglish, String customJapanese) {
    }

    // Opens the dialog: front side visible by default, flip button toggles to back side
    public static void show(Window owner, Product product, int lowStockThreshold, Consumer<AddToCartRequest> onAdd) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Product Details");

        StackPane flipPane = new StackPane();
        flipPane.setMinHeight(500);

        VBox front = buildFront(product);
        VBox back = buildBack(product, lowStockThreshold, stage, onAdd);
        back.setVisible(false);

        Button flipButton = new Button("More Info");
        flipButton.getStyleClass().addAll("neon-btn", "neon-green-btn");
        Timeline moreInfoPulse = createMoreInfoPulseAnimation(flipButton);
        moreInfoPulse.play();
        flipButton.setOnAction(event -> {
            boolean showBack = !back.isVisible();
            animateFlip(flipPane, () -> {
                front.setVisible(!showBack);
                back.setVisible(showBack);
            });
        });
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> moreInfoPulse.stop());

        flipPane.getChildren().addAll(front, back);

        Button backButton = new Button("← Back to Main Page");
        backButton.getStyleClass().add("neon-btn");
        backButton.setOnAction(event -> stage.close());

        HBox topBar = new HBox(10, flipButton, backButton);
        topBar.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setTop(topBar);
        root.setCenter(flipPane);
        root.getStyleClass().add("panel");

        Scene scene = new Scene(root, 1020, 820);
        scene.getStylesheets().add(ProductDetailDialog.class.getResource("/tokyoera/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    // Front side: product name + scrollable hero image carousel
    private static VBox buildFront(Product product) {
        StackPane heroImagePane = buildHeroImagePane(product);

        Label name = new Label(product.getName());
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("heading");

        VBox frontContent = new VBox(10, name, heroImagePane);
        VBox.setVgrow(heroImagePane, Priority.ALWAYS);
        frontContent.setFillWidth(true);
        frontContent.setPadding(new Insets(10));

        ScrollPane frontScrollPane = new ScrollPane(frontContent);
        frontScrollPane.setFitToWidth(true);
        frontScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox front = new VBox(frontScrollPane);
        VBox.setVgrow(frontScrollPane, Priority.ALWAYS);
        return front;
    }

    // Builds the large image viewer with prev/next slide animation and a circular zoom lens
    private static StackPane buildHeroImagePane(Product product) {
        final int[] imageIndex = {0};

        // Pre-load all 10 images once so navigation never reloads from disk
        javafx.scene.image.Image[] cachedImages = new javafx.scene.image.Image[10];
        for (int i = 0; i < 10; i++) {
            cachedImages[i] = UiUtil.loadImageByBaseName(product.getImagePrefix() + (char) ('a' + i));
        }

        StackPane heroImagePane = new StackPane();
        heroImagePane.setMinSize(200, 200);
        heroImagePane.setPrefSize(700, 700);
        heroImagePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(false);
        NumberBinding side = Bindings.min(heroImagePane.widthProperty(), heroImagePane.heightProperty()).subtract(16);
        imageView.fitWidthProperty().bind(side);
        imageView.fitHeightProperty().bind(side);

        TranslateTransition[] slideAnim = {null};

        java.util.function.BiConsumer<Integer, Boolean> slideToIndex = (newIndex, goRight) -> {
            if (slideAnim[0] != null) return; // ignore while animating
            double width = heroImagePane.getWidth() > 0 ? heroImagePane.getWidth() : 700;
            double outX = goRight ? -width : width;
            double inX  = goRight ?  width : -width;

            // Slide current image out
            TranslateTransition out = new TranslateTransition(Duration.millis(220), imageView);
            out.setToX(outX);

            // Prepare incoming image off-screen
            ImageView incoming = new ImageView(cachedImages[newIndex]);
            incoming.setPreserveRatio(false);
            incoming.fitWidthProperty().bind(imageView.fitWidthProperty());
            incoming.fitHeightProperty().bind(imageView.fitHeightProperty());
            incoming.setTranslateX(inX);
            heroImagePane.getChildren().add(0, incoming);

            TranslateTransition in = new TranslateTransition(Duration.millis(220), incoming);
            in.setToX(0);

            slideAnim[0] = in;
            out.play();
            in.play();
            in.setOnFinished(e -> {
                imageIndex[0] = newIndex;
                imageView.setImage(cachedImages[newIndex]);
                imageView.setTranslateX(0);
                heroImagePane.getChildren().remove(incoming);
                slideAnim[0] = null;
            });
        };

        Button previousButton = new Button("◀");
        previousButton.getStyleClass().add("neon-btn");
        previousButton.setOnAction(event -> {
            int next = (imageIndex[0] + 9) % 10;
            slideToIndex.accept(next, false);
        });

        Button nextButton = new Button("▶");
        nextButton.getStyleClass().add("neon-btn");
        nextButton.setOnAction(event -> {
            int next = (imageIndex[0] + 1) % 10;
            slideToIndex.accept(next, true);
        });

        Runnable refreshImage = () -> imageView.setImage(cachedImages[imageIndex[0]]);

        StackPane.setAlignment(previousButton, Pos.CENTER_LEFT);
        StackPane.setMargin(previousButton, new Insets(0, 0, 0, 8));
        StackPane.setAlignment(nextButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(nextButton, new Insets(0, 8, 0, 0));

        refreshImage.run();
        heroImagePane.getChildren().addAll(imageView, previousButton, nextButton);

        // --- Zoom Lens ---
        final int LENS_SIZE = 190;
        final double ZOOM = 2.5;

        ImageView zoomedView = new ImageView();
        zoomedView.setPreserveRatio(false);
        StackPane.setAlignment(zoomedView, Pos.TOP_LEFT);

        Circle lensClip = new Circle(LENS_SIZE / 2.0, LENS_SIZE / 2.0, LENS_SIZE / 2.0);
        Circle lensBorder = new Circle(LENS_SIZE / 2.0);
        lensBorder.setFill(null);
        lensBorder.setStroke(Color.web("#7FF8FF"));
        lensBorder.setStrokeWidth(2.5);
        lensBorder.setMouseTransparent(true);

        StackPane lensPane = new StackPane(zoomedView, lensBorder);
        lensPane.setPrefSize(LENS_SIZE, LENS_SIZE);
        lensPane.setMaxSize(LENS_SIZE, LENS_SIZE);
        lensPane.setMinSize(LENS_SIZE, LENS_SIZE);
        lensPane.setClip(lensClip);
        lensPane.setMouseTransparent(true);
        lensPane.setVisible(false);
        heroImagePane.getChildren().add(lensPane);

        heroImagePane.setOnMouseEntered(e -> lensPane.setVisible(true));
        heroImagePane.setOnMouseExited(e -> lensPane.setVisible(false));
        heroImagePane.setOnMouseMoved(e -> {
            double mx = e.getX();
            double my = e.getY();
            double paneW = heroImagePane.getWidth();
            double paneH = heroImagePane.getHeight();
            double imgW = imageView.getFitWidth();
            double imgH = imageView.getFitHeight();
            double imgStartX = (paneW - imgW) / 2.0;
            double imgStartY = (paneH - imgH) / 2.0;
            double relX = mx - imgStartX;
            double relY = my - imgStartY;
            if (relX < 0 || relY < 0 || relX > imgW || relY > imgH) {
                lensPane.setVisible(false);
                return;
            }
            lensPane.setVisible(true);
            lensPane.setTranslateX(mx - paneW / 2.0);
            lensPane.setTranslateY(my - paneH / 2.0);
            zoomedView.setImage(imageView.getImage());
            zoomedView.setFitWidth(imgW * ZOOM);
            zoomedView.setFitHeight(imgH * ZOOM);
            zoomedView.setTranslateX(LENS_SIZE / 2.0 - relX * ZOOM);
            zoomedView.setTranslateY(LENS_SIZE / 2.0 - relY * ZOOM);
        });

        return heroImagePane;
    }

    // Back side: design details, size selector, quantity, customization options, and add-to-cart button
    private static VBox buildBack(Product product, int lowStockThreshold, Stage stage, Consumer<AddToCartRequest> onAdd) {
        // Parse motto and design detail lines from description
        String[] descLines = product.getDescription().split("\n");
        List<String> mottoParagraphs = new ArrayList<>();
        StringBuilder currentParagraph = new StringBuilder();
        Map<String, String> designDetails = new LinkedHashMap<>();
        String[] detailKeys = {"Front", "Left Sleeve", "Right Sleeve", "Back"};
        boolean inDetails = false;
        for (String line : descLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Design Details:")) {
                if (currentParagraph.length() > 0) {
                    mottoParagraphs.add(currentParagraph.toString());
                    currentParagraph.setLength(0);
                }
                inDetails = true;
                continue;
            }
            if (inDetails) {
                for (String key : detailKeys) {
                    if (trimmed.startsWith(key + ":")) {
                        designDetails.put(key, trimmed.substring(key.length() + 1).trim());
                        break;
                    }
                }
            } else if (!trimmed.isEmpty()) {
                if (currentParagraph.length() > 0) {
                    currentParagraph.append(' ');
                }
                currentParagraph.append(trimmed);
            } else if (currentParagraph.length() > 0) {
                mottoParagraphs.add(currentParagraph.toString());
                currentParagraph.setLength(0);
            }
        }
        if (currentParagraph.length() > 0) {
            mottoParagraphs.add(currentParagraph.toString());
        }

        designDetails.put("Left Sleeve", normalizedOrDash(product.getDefaultEnglishMessage()));
        designDetails.put("Right Sleeve", normalizedOrDash(product.getDefaultJapaneseMessage()));

        // Title
        Label title = new Label("Details & Add to Cart · 東京時代");
        title.getStyleClass().add("heading");

        // Motto — styled distinctly
        Label mottoLabel = new Label(String.join("\n\n", mottoParagraphs));
        mottoLabel.getStyleClass().add("motto");
        mottoLabel.setWrapText(true);
        mottoLabel.setMaxWidth(Double.MAX_VALUE);
        Timeline mottoGlitch = createMottoGlitchAnimation(mottoLabel);
        mottoGlitch.play();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> mottoGlitch.stop());

        // About This Product card (merged details + specs)
        VBox aboutBox = new VBox(5);
        aboutBox.getStyleClass().add("card");
        Label designTitle = new Label("About This Product");
        designTitle.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-font-size: 13px;");
        aboutBox.getChildren().add(designTitle);
        final double detailsLabelColumnWidth = 120;
        for (String key : detailKeys) {
            String val = designDetails.getOrDefault(key, "\u2014");
            aboutBox.getChildren().add(createDetailRow(
                    key,
                    val,
                    detailsLabelColumnWidth,
                    "-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-font-size: 12px;",
                    "-fx-text-fill: #e0e0e0; -fx-font-size: 12px;"
            ));
        }

        for (String[] pair : new String[][]{
            {"Price", UiUtil.rm(product.getPrice())},
            {"Stock", String.valueOf(product.getStock())},
            {"Color", "Black"},
            {"Material", normalizedOrDash(product.getMaterial())},
            {"Fabric weight", "4.2 oz./yd.² (142 g/m²)"},
            {"Sourced", "Nicaragua"},
            {"Embroidery", "Left sleeve (EN) \u00B7 Right sleeve (JP)"}
        }) {
            aboutBox.getChildren().add(createDetailRow(
                    pair[0],
                    pair[1],
                    detailsLabelColumnWidth,
                    "-fx-text-fill: #00E6FF; -fx-font-weight: bold;",
                    "-fx-text-fill: #e0e0e0;"
            ));
        }

        // Stock status badge
        String stockText = product.getStock() == 0
            ? "TEMPORARILY OUT OF STOCK - Add to Cart Disabled"
            : product.getStock() < lowStockThreshold
            ? "LOW STOCK - Only " + product.getStock() + " left"
            : "IN STOCK";
        Label stockStatus = new Label(stockText);
        stockStatus.getStyleClass().add(product.getStock() == 0 ? "badge-out-stock" : "badge-low-stock");
        if (product.getStock() >= lowStockThreshold) {
            stockStatus.setStyle("-fx-background-color: #173d2a; -fx-text-fill: #39ff88; -fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 999;");
        }

        // Size & Quantity row
        Label sizeLabel = new Label("Size:");
        sizeLabel.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold;");
        ComboBox<String> sizeCombo = new ComboBox<>();
        sizeCombo.getItems().addAll(product.getSizeList());
        sizeCombo.setValue(product.getSizeList().contains("L") ? "L" : product.getSizeList().isEmpty() ? null : product.getSizeList().get(0));
        sizeCombo.setPromptText("Select size (Required)");
        Label qtyLabel = new Label("Quantity:");
        qtyLabel.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold;");
        Spinner<Integer> quantitySpinner = new Spinner<>(1, Math.max(1, product.getStock()), 1);
        quantitySpinner.setEditable(true);
        Label sizesNote = new Label("Available: XS \u00B7 S \u00B7 M \u00B7 L \u00B7 XL \u00B7 2X \u00B7 3X");
        sizesNote.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        HBox sizeQtyRow = new HBox(16, new VBox(4, sizeLabel, sizeCombo), new VBox(4, qtyLabel, quantitySpinner));
        sizeQtyRow.setAlignment(Pos.CENTER_LEFT);

        // Customization card
        CheckBox customize = new CheckBox("Use custom sleeve messages (+RM 20)");
        customize.setStyle("-fx-text-fill: #00E6FF;");
        TextArea englishInput = new TextArea();
        englishInput.setPromptText("Custom English text for left sleeve (max 200 chars)");
        englishInput.setPrefRowCount(2);
        englishInput.setStyle("-fx-control-inner-background: #f5f5f5; -fx-text-fill: #111111;");
        TextArea japaneseInput = new TextArea();
        japaneseInput.setPromptText("Custom Japanese text for right sleeve (max 200 chars)");
        japaneseInput.setPrefRowCount(2);
        japaneseInput.setStyle("-fx-control-inner-background: #f5f5f5; -fx-text-fill: #111111;");
        englishInput.disableProperty().bind(customize.selectedProperty().not());
        japaneseInput.disableProperty().bind(customize.selectedProperty().not());
        VBox customizeBox = new VBox(6, customize, englishInput, japaneseInput);
        customizeBox.getStyleClass().add("card");

        // Designer
        Label designer = new Label("Designed by Akira Fukutomi");
        designer.getStyleClass().add("designer-highlight");

        // Buttons
        Button addButton = new Button("Add to Cart");
        addButton.getStyleClass().add("neon-btn");
        addButton.setDisable(product.getStock() <= 0);
        addButton.setOnAction(event -> {
            String size = sizeCombo.getValue();
            int qty = quantitySpinner.getValue();
            if (size == null || size.isBlank()) {
                UiUtil.error("Size selection is required.");
                return;
            }
            if (qty > product.getStock()) {
                UiUtil.error("Quantity cannot exceed available stock.");
                return;
            }
            boolean customized = customize.isSelected();
            String en = englishInput.getText() == null ? "" : englishInput.getText().trim();
            String jp = japaneseInput.getText() == null ? "" : japaneseInput.getText().trim();
            if (customized) {
                if (en.isBlank() || jp.isBlank()) {
                    UiUtil.error("Custom messages cannot be empty when customization is selected.");
                    return;
                }
                if (en.length() > 200 || jp.length() > 200) {
                    UiUtil.error("Custom messages must be 200 characters or less.");
                    return;
                }
            }
            onAdd.accept(new AddToCartRequest(size, qty, customized, en, jp));
            stage.close();
        });

        // Designer label on the left, Add to Cart button on the right — same row
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox designerAndCartRow = new HBox(10, designer, footerSpacer, addButton);
        designerAndCartRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, title, mottoLabel, aboutBox, stockStatus,
            sizesNote, sizeQtyRow, customizeBox, designerAndCartRow);
        content.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox back = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return back;
    }

        // Smooth pulse effect for the More Info button to make it feel alive without glitching.
        private static Timeline createMoreInfoPulseAnimation(Button button) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(button.scaleXProperty(), 1.0),
                new KeyValue(button.scaleYProperty(), 1.0),
                new KeyValue(button.opacityProperty(), 1.0)
            ),
            new KeyFrame(Duration.millis(900),
                new KeyValue(button.scaleXProperty(), 1.07),
                new KeyValue(button.scaleYProperty(), 1.07),
                new KeyValue(button.opacityProperty(), 0.92)
            )
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
        }

    private static Timeline createMottoGlitchAnimation(Label mottoLabel) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(85), event -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double baseX = random.nextDouble(-2.6, 2.6);
            double baseY = random.nextDouble(-3.2, 3.2);
            double baseRotate = random.nextDouble(-1.4, 1.4);
            double opacity = random.nextDouble(0.82, 1.0);

            // Occasionally add a stronger glitch burst to make movement feel less predictable.
            if (random.nextDouble() < 0.14) {
                baseX += random.nextDouble(-6.0, 6.0);
                baseY += random.nextDouble(-4.0, 4.0);
                baseRotate += random.nextDouble(-3.0, 3.0);
                opacity = random.nextDouble(0.7, 0.94);
            }

            mottoLabel.setTranslateX(baseX);
            mottoLabel.setTranslateY(baseY);
            mottoLabel.setRotate(baseRotate);
            mottoLabel.setOpacity(opacity);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }

    private static HBox createDetailRow(String key, String value, double labelWidth, String keyStyle, String valueStyle) {
        HBox row = new HBox(8);
        Label keyLabel = new Label(key + ":");
        keyLabel.setStyle(keyStyle);
        keyLabel.setMinWidth(labelWidth);
        keyLabel.setPrefWidth(labelWidth);

        Label valueLabel = new Label(value);
        valueLabel.setStyle(valueStyle);
        valueLabel.setWrapText(true);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);

        row.getChildren().addAll(keyLabel, valueLabel);
        return row;
    }

    // Plays a 3D card-flip animation (scale X down to 0, swap content, scale back up)
    private static void animateFlip(StackPane flipPane, Runnable switchContent) {
        RotateTransition hide = new RotateTransition(Duration.millis(150), flipPane);
        hide.setFromAngle(0);
        hide.setToAngle(90);
        hide.setAxis(Rotate.Y_AXIS);

        RotateTransition show = new RotateTransition(Duration.millis(150), flipPane);
        show.setFromAngle(270);
        show.setToAngle(360);
        show.setAxis(Rotate.Y_AXIS);

        hide.setOnFinished(event -> {
            switchContent.run();
            show.play();
        });
        hide.play();
    }

    // Returns the value as-is, or "—" if it's null/blank
    private static String normalizedOrDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value.trim();
    }
}
