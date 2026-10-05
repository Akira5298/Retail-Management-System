# Lamina Control Panel

Coursework Type
- Retail management system built with Java 21 and JavaFX.

How to Run on Mac / Linux:
1. Open a terminal and navigate to the project root folder
2. Run: ./mvnw clean test
3. Run: ./mvnw javafx:run

How to run on Windows (Command Prompt or PowerShell):
1. Open a terminal and navigate to the project root folder
2. Run: mvnw.cmd clean test
3. Run: mvnw.cmd javafx:run

Default Demo Accounts
- Admin login: admin / Admin@123!
- User login: user / User@123!
- Demo users (seeded): demo_user_01 to demo_user_20 / Demo@123!

Important Files Included
- code/main/App.java : target file / main application entry point using class tokyoera.App
- tokyoera.db : SQLite database storage
- pics/products/ : product images grouped by product (product1/ through product5/)
- pics/videos/ : background video assets (Matrix1-5.mp4)
- pics/music/ : background music tracks
- code/main/ : application source code
- code/test/ : JUnit test source code
- target/ : compiled build output folder

Main Features
- Login system with admin and user privileges
- SQLite data storage for users, products, orders, order items, transactions, and settings
- Login lockout after 5 failed attempts (5-minute lock)
- Cart persistence to SQLite (loads on login, saves on logout)
- User CRUD flow through cart and order management
- Admin CRUD for products, stock, orders, and storefront settings
- Coupon system with percentage/fixed discounts and product-specific applicability
- Profit dashboard metrics (revenue, cost, net profit, margin)
- Product original cost tracking by clothing type (Hoodie 230 RM, Sweatshirt 200 RM, T-shirt 160 RM; shipping excluded)
- Reporting dashboard with date/category filters, sales charts, CSV export, and receipt/PDF generation
- Validation and error handling across login, registration, cart, checkout, stock, and admin product management
- JUnit tests for models, services, validation logic, integrations, and reporting

User Navigation
1. Start the app and log in using the user account (user / User@123!).
2. You will enter the storefront dashboard.
3. Use the search bar, type filter, and sort filter to browse products.
4. Click a product to open the product details dialog.
5. Choose size and quantity, optionally enter custom sleeve text, then add the item to the cart.
6. Open the cart to update quantity, remove items, apply coupon code, cancel an order, or complete checkout.
7. After checkout, view the payment success dialog and export the PDF receipt.
8. Use Order History to review previous orders.

Admin Navigation
1. Start the app and log in using the admin account (admin / Admin@123!).
2. You will enter the admin dashboard.
3. Use the Dashboard page to view sales cards, charts, category summaries, and filtered reporting.
4. Use the Products page to create, view, update, and delete products (includes original cost display by type).
5. Use the Stock page to review inventory and low-stock items.
6. Use the Orders and Custom Orders pages to review order records and update statuses.
7. Use More Settings to adjust low-stock threshold, storefront badge settings, and coupon settings (percentage and target product).

User Functionality
- Login and sign-up with privilege-based routing
- Product browsing with search, type filter, sort, and product detail dialogs
- Cart create, read, update, and delete operations
- Checkout flow with order tracking and payment receipt generation
- PDF receipt export after successful checkout
- Order history view for previous purchases

Admin Functionality
- Admin product management CRUD (create, read, update, delete)
- Stock management and low-stock monitoring
- Order and custom-order review with status filtering
- Business reporting with sales summaries, daily trends, category charts, date filters, and CSV export
- Low-stock threshold, storefront badge, and coupon settings management
- SQLite persistent storage and automatic seed/demo data
- JUnit tests covering business logic, validation, integration flows, and report generation

Technical Notes
- The JavaFX Maven plugin is configured with JVM options in pom.xml for more stable startup on larger UI sessions.
- If JavaFX launch fails from outside the project root, run it using the full project path or change into the project folder first.

Original Creative Work
- All shirt designs used in this project were created by Akira Fukutomi, using Printful & Etsy.
- All music/audio used in this project was also created by Akira Fukutomi, using FL Studio.
