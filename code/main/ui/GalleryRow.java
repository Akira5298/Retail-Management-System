package tokyoera.ui;

import tokyoera.model.Product;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// A horizontally scrolling strip of product images for the storefront.
// Each row auto-scrolls, has a magnetic tilt effect on hover, and shows stock badges.
public class GalleryRow extends StackPane {
    private static final double IMAGE_W = 220;
    private static final double IMAGE_H = 230;
    private static final double GAP = 14;
    private static final double SPEED = 80;
    private static final double LEFT_BOUND = -IMAGE_W;

    private final Pane track = new Pane();
    private final List<ImageView> images = new ArrayList<>();
    private final Product product;
    private final int lowStockThreshold;
    private final Consumer<Product> onClick;
    private final double direction;
    private boolean paused = false;
    private AnimationTimer timer;
    private Timeline pulseScheduler;
    private final Set<ImageView> pulsing = new HashSet<>();
    private boolean animationActive = true;

    public GalleryRow(Product product, int lowStockThreshold, Consumer<Product> onClick, double direction, boolean showNewTag, String badgeText) {
        getStyleClass().add("card");
        setMinHeight(270);
        setPrefHeight(270);
        setMaxHeight(270);
        this.product = product;
        this.lowStockThreshold = lowStockThreshold;
        this.onClick = onClick;
        this.direction = direction;

        setOnMouseEntered(event -> paused = true);
        setOnMouseExited(event -> {
            paused = false;
            setRotate(0);
        });
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                this.onClick.accept(this.product);
            }
        });

        // Feature 2: Magnetic tilt toward cursor
        addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            double dx = (event.getX() - cx) / Math.max(1, cx);
            double dy = (event.getY() - cy) / Math.max(1, cy);
            setRotationAxis(new Point3D(-dy, dx, 0));
            setRotate(5.0 * Math.sqrt(dx * dx + dy * dy));
        });

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty().subtract(4));
        clip.heightProperty().bind(heightProperty().subtract(4));
        track.setClip(clip);

        for (int i = 0; i < 10; i++) {
            String name = product.getImagePrefix() + (char) ('a' + i);
            ImageView imageView = new ImageView(UiUtil.loadImageByBaseName(name));
            imageView.setFitWidth(IMAGE_W);
            imageView.setFitHeight(IMAGE_H);
            imageView.setPreserveRatio(false);
            imageView.setLayoutX(i * (IMAGE_W + GAP));
            imageView.setLayoutY(14);
            imageView.setOnMouseEntered(event -> paused = true);
            imageView.setOnMouseExited(event -> paused = false);
            images.add(imageView);
        }

        HBox titleBox = new HBox(6);
        titleBox.setStyle("-fx-padding: 4 6;");
        if (showNewTag) {
            Label newTag = new Label(badgeText == null || badgeText.isBlank() ? "NEW!" : badgeText);
            newTag.getStyleClass().add("badge-out-stock");
            titleBox.getChildren().add(newTag);
        }
        Label productLabel = new Label(product.getName());
        productLabel.setStyle("-fx-text-fill: #00E6FF; -fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.65); -fx-background-radius: 4; -fx-padding: 2 6;");
        titleBox.getChildren().add(productLabel);
        titleBox.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        titleBox.setMouseTransparent(true);
        StackPane.setAlignment(titleBox, Pos.TOP_LEFT);

        getChildren().addAll(track, titleBox);
        track.getChildren().addAll(images);

        addEventFilter(ScrollEvent.SCROLL, event -> {
            double horizontalDelta = event.getDeltaX();
            if (Math.abs(horizontalDelta) < 0.01) {
                return;
            }
            shiftImages(horizontalDelta * 0.35);
            event.consume();
        });

        addStockIndicators(product);
        startAnimation();
        startPulseAnimations();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                if (timer != null) timer.stop();
                if (pulseScheduler != null) pulseScheduler.stop();
            } else {
                if (timer != null) timer.start();
                if (pulseScheduler != null) pulseScheduler.play();
            }
        });
    }

    // Overlay a badge on the image strip depending on current stock level
    private void addStockIndicators(Product product) {
        if (product.getStock() == 0) {
                Label badge = new Label("TEMPORARILY OUT OF STOCK");
            badge.getStyleClass().add("badge-out-stock");
            badge.setMouseTransparent(true);
            StackPane.setAlignment(badge, Pos.CENTER);
            getChildren().add(badge);
        } else if (product.getStock() < lowStockThreshold) {
            Label badge = new Label("LOW STOCK");
            badge.getStyleClass().add("badge-low-stock");
            badge.setMouseTransparent(true);
            StackPane.setAlignment(badge, Pos.CENTER_RIGHT);
            getChildren().add(badge);
        }
    }

    // AnimationTimer that shifts all images left/right every frame to create the scroll loop
    private void startAnimation() {
        timer = new AnimationTimer() {
            long last = -1;

            @Override
            public void handle(long now) {
                if (last < 0) {
                    last = now;
                    return;
                }
                if (!animationActive || paused) {
                    last = now;
                    return;
                }

                if (getWidth() <= 1) {
                    last = now;
                    return;
                }

                double dt = (now - last) / 1_000_000_000.0;
                last = now;
                double rightBound = getWidth() + IMAGE_W;

                for (Node node : images) {
                    ImageView imageView = (ImageView) node;
                    double nextX = imageView.getLayoutX() + (SPEED * direction * dt);
                    imageView.setLayoutX(nextX);
                }

                normalizeLoopPositions(rightBound);
            }
        };
        timer.start();
    }

    private void shiftImages(double deltaX) {
        double rightBound = getWidth() + IMAGE_W;
        for (Node node : images) {
            ImageView imageView = (ImageView) node;
            imageView.setLayoutX(imageView.getLayoutX() + deltaX);
        }
        normalizeLoopPositions(rightBound);
    }

    public ImageView pickRandomImageNode() {
        if (images.isEmpty()) {
            return null;
        }
        return images.get(ThreadLocalRandom.current().nextInt(images.size()));
    }

    // Randomly picks one visible image and plays a brief scale "pulse" animation on it
    private void startPulseAnimations() {
        pulseScheduler = new Timeline(new KeyFrame(Duration.millis(1800), event -> {
            if (!animationActive || paused || images.isEmpty()) return;
            List<ImageView> candidates = images.stream()
                    .filter(iv -> !pulsing.contains(iv))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) return;
            ImageView target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            pulsing.add(target);
            ScaleTransition grow = new ScaleTransition(Duration.millis(380), target);
            grow.setToX(1.18);
            grow.setToY(1.18);
            grow.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition shrink = new ScaleTransition(Duration.millis(300), target);
            shrink.setToX(1.0);
            shrink.setToY(1.0);
            shrink.setInterpolator(Interpolator.EASE_IN);
            SequentialTransition pulse = new SequentialTransition(grow, shrink);
            pulse.setOnFinished(e -> pulsing.remove(target));
            pulse.play();
        }));
        pulseScheduler.setCycleCount(Animation.INDEFINITE);
        pulseScheduler.play();
    }

    // Called by the dashboard to pause row animations when the row is outside the viewport.
    public void setAnimationActive(boolean active) {
        if (this.animationActive == active) {
            return;
        }
        this.animationActive = active;
        if (!active) {
            setRotate(0);
        }
    }

    // Wraps images in both directions so manual horizontal scrolling always stays infinite.
    private void normalizeLoopPositions(double rightBound) {
        double span = images.size() * (IMAGE_W + GAP);
        if (span <= 0) {
            return;
        }

        for (ImageView imageView : images) {
            double x = imageView.getLayoutX();
            while (x > rightBound) {
                x -= span;
            }
            while (x < LEFT_BOUND) {
                x += span;
            }
            imageView.setLayoutX(x);
        }
    }
}
