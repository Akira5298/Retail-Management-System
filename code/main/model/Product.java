package tokyoera.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Represents a single product in the store catalogue.
// Contains all the data that gets shown in the storefront and the product detail dialog.
public class Product {
    private int id;
    private String name;
    private String description;            // multi-line text shown on the detail page
    private double price;                  // selling price in RM
    private int stock;                     // total units available
    private ClothingType clothingType;     // HOODIE / SWEATSHIRT / T_SHIRT
    private String color;
    private String defaultEnglishMessage;  // default sleeve print text (English)
    private String defaultJapaneseMessage; // default sleeve print text (Japanese)
    private boolean customizationAllowed;  // whether buyers can enter custom sleeve text
    private double customizationFee;       // extra charge for customization in RM
    private String sizes;                  // comma-separated sizes, e.g. "XS,S,M,L,XL"
    private String material;               // fabric description
    private String includesInfo;           // what's included in the package
    private String imagePrefix;            // base path prefix used to load product images

    public Product() {
    }

    public Product(int id, String name, String description, double price, int stock, ClothingType clothingType, String color,
                   String defaultEnglishMessage, String defaultJapaneseMessage, boolean customizationAllowed,
                   double customizationFee, String sizes, String material, String includesInfo, String imagePrefix) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.clothingType = clothingType;
        this.color = color;
        this.defaultEnglishMessage = defaultEnglishMessage;
        this.defaultJapaneseMessage = defaultJapaneseMessage;
        this.customizationAllowed = customizationAllowed;
        this.customizationFee = customizationFee;
        this.sizes = sizes;
        this.material = material;
        this.includesInfo = includesInfo;
        this.imagePrefix = imagePrefix;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public ClothingType getClothingType() {
        return clothingType;
    }

    public String getColor() {
        return color;
    }

    public String getDefaultEnglishMessage() {
        return defaultEnglishMessage;
    }

    public String getDefaultJapaneseMessage() {
        return defaultJapaneseMessage;
    }

    public boolean isCustomizationAllowed() {
        return customizationAllowed;
    }

    public double getCustomizationFee() {
        return customizationFee;
    }

    public String getSizes() {
        return sizes;
    }

    public String getMaterial() {
        return material;
    }

    public String getIncludesInfo() {
        return includesInfo;
    }

    public String getImagePrefix() {
        return imagePrefix;
    }

    public void setStock(int stock) {
        // Stock can't go below 0 — negative stock would be a bug
        if (stock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        this.stock = stock;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(double price) {
        // Price of 0 or below doesn't make sense for a retail system
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }
        this.price = price;
    }

    public void setClothingType(ClothingType clothingType) {
        this.clothingType = clothingType;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setDefaultEnglishMessage(String defaultEnglishMessage) {
        this.defaultEnglishMessage = defaultEnglishMessage;
    }

    public void setDefaultJapaneseMessage(String defaultJapaneseMessage) {
        this.defaultJapaneseMessage = defaultJapaneseMessage;
    }

    public void setCustomizationAllowed(boolean customizationAllowed) {
        this.customizationAllowed = customizationAllowed;
    }

    public void setCustomizationFee(double customizationFee) {
        this.customizationFee = customizationFee;
    }

    public void setSizes(String sizes) {
        this.sizes = sizes;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public void setIncludesInfo(String includesInfo) {
        this.includesInfo = includesInfo;
    }

    public void setImagePrefix(String imagePrefix) {
        this.imagePrefix = imagePrefix;
    }

    // Parses the comma-separated sizes string into a list.
    // Returns an empty list if sizes is null or blank.
    public List<String> getSizeList() {
        if (sizes == null || sizes.isBlank()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.stream(sizes.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    }

    // Generates the 10 image filenames for this product (suffix a–j) used by the gallery
    public List<String> getImageNames() {
        List<String> names = new ArrayList<>();
        for (char suffix = 'a'; suffix <= 'j'; suffix++) {
            names.add(imagePrefix + suffix);
        }
        return names;
    }
}
