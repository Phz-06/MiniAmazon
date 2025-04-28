## 1. Shopping Cart Supporting Multiple Items in One Order
Our Amazon project implements a full shopping cart system that allows users to order multiple products at once. This goes beyond the basic project requirements, which only mandated a single product purchase flow. Although the UPS side originally planned to support delivering multiple items within one shipment, it was not fully realized in practice. Our system's shopping cart enables a seamless checkout experience similar to real-world e-commerce platforms, enhancing usability and realism beyond the bare minimum specification.

## 2. Admin Panel for Management and UPS Connection Control
We built a dedicated admin user system with a secure admin panel. This panel provides dynamic controls such as connecting to the world server, disconnecting, purchasing products for warehouses, starting or stopping the UPS socket server, and more. The base project assumed hardcoded or manually scripted control flows for these operations. By providing a graphical, role-protected admin panel, we made managing the simulation environment safer, more flexible, and user-friendly.

## 3. Product Description Page
Instead of only displaying a simple product list, our Amazon site offers individual product description pages. Users can click into a product to view detailed information and then proceed to purchase from that page. This significantly enhances the user experience by mimicking the behavior of real-world online shopping platforms, providing depth and clarity that was not required by the project baseline.

## 4. Shopping Cart Item Quantity Updates
Users can not only add items to their shopping cart, but also update the quantity of each item directly within the cart before checkout. This dynamic cart editing feature mirrors the functionality expected from a professional e-commerce website. The base project did not require any shopping cart functionality, making this a clear point of differentiation for our system.

## 5. Auto-Generated Product Image Placeholders
Our system automatically generates and displays placeholder images for products in the catalog and product detail pages. Although image support was not required by the specification, we implemented it to greatly improve the user interface and visual consistency, creating a more professional, appealing experience for users.

## 6. Role-Based Admin Access Control
We implemented role-based access control where only users with the admin role can access critical management features such as world connection settings, product stocking, and server socket operations. Regular users are restricted from accessing these administrative interfaces. This added security model was not required by the original project but ensures a more secure and organized system operation.

## 7. Amazon-Like User Interface
We designed our Amazon frontend with a focus on replicating the familiar look and feel of the real Amazon website. Through thoughtful layout, intuitive design patterns, and consistent styling, we delivered a user experience that feels professional and comfortable. While UI/UX was not a graded component, a polished frontend makes the project stand out, and better demonstrates the quality of our work.

## 8. Mock Payment with Credit Card Input
Our Amazon frontend implements a mock payment system that requires users to enter a credit card number at checkout. Although the project specification explicitly stated that a payment system was not necessary, we chose to add a realistic payment step to enhance the authenticity of the shopping experience. By requiring credit card input, our system mirrors the workflow of real-world e-commerce platforms more closely, providing a professional and immersive checkout process that goes beyond the base requirements.

## 9. Amazon-to-UPS Connection
In our implementation, the Amazon server is capable of actively initiating a connection to the UPS server, rather than passively waiting for UPS to connect first. While the basic project requirements only required UPS to connect to Amazon, we extended the functionality to allow Amazon to proactively establish the communication channel as well.

## 10. Product Searching, Sorting & Filtering
Our Amazon frontend offers product sorting and filtering capabilities, allowing users to refine their shopping experience based on criteria such as product name, price, or other attributes. While the project specification required only a basic searchable catalog, it did not mandate any sorting or filtering features. By providing advanced browsing options, we greatly improve the usability and realism of the shopping interface, bringing it closer to the experience users expect from professional e-commerce platforms.
