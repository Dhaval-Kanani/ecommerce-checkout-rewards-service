# Uniblox SDE Assignment - E-commerce Store API

A Spring Boot backend application for an e-commerce store with shopping cart and checkout functionality, including automatic discount code generation for every nth order.

## Features

- **Shopping Cart Management**
  - Add items to cart
  - View cart contents
  - Remove items from cart
  
- **Checkout System**
  - Process orders
  - Validate and apply discount codes
  - Automatic discount code generation for every nth order (configurable)
  
- **Admin Dashboard**
  - View purchase statistics
  - Track discount codes
  - Monitor total sales and discounts

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Maven** - Dependency management
- **Lombok** - Reduce boilerplate code
- **JUnit 5** - Unit testing
- **In-memory storage** - No database required

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Setup Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Uniblox-SDE-Assignment
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

## API Endpoints

### Cart Management

#### Add Item to Cart
```http
POST /api/cart/items
Content-Type: application/json

{
  "cartId": "string (optional - new cart created if not provided)",
  "itemId": "string",
  "quantity": integer
}
```

#### Get Cart Details
```http
GET /api/cart/{cartId}
```

#### Remove Item from Cart
```http
DELETE /api/cart/{cartId}/items/{itemId}
```

### Checkout

#### Process Checkout
```http
POST /api/checkout
Content-Type: application/json

{
  "cartId": "string",
  "discountCode": "string (optional)"
}
```

**Response includes:**
- Order ID
- Items purchased
- Subtotal
- Discount amount (if applicable)
- Total amount
- Generated discount code (if this is the nth order)

### Admin APIs

#### Get Statistics
```http
GET /api/admin/stats
```

**Returns:**
- Total number of orders
- Total items purchased
- Total purchase amount
- List of discount codes
- Total discount amount

#### List Discount Codes
```http
GET /api/admin/discount-codes
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Change the server port
server.port=8080

# Configure nth order for discount generation (default: 3)
app.discount.nth-order=3
```

## Discount Code Logic

- Every **nth order** generates a 10% discount code
- Discount codes can be used only **once**
- Discount codes must be used before the next nth order generates a new code
- Discount applies to the **entire order**, not individual items

## Project Structure

```
Uniblox-SDE-Assignment/
├── src/
│   ├── main/
│   │   ├── java/com/ecommerce/
│   │   │   ├── controller/       # REST API controllers
│   │   │   ├── service/          # Business logic layer
│   │   │   ├── repository/       # In-memory data store
│   │   │   ├── model/            # Domain models
│   │   │   ├── dto/              # Data transfer objects
│   │   │   ├── exception/        # Custom exceptions
│   │   │   └── EcommerceStoreApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/ecommerce/   # Unit tests
├── pom.xml
└── README.md
```

## Running Tests

```bash
mvn test
```

## Sample API Flow

1. **Add items to cart**
   ```bash
   POST /api/cart/items
   {
     "itemId": "item1",
     "quantity": 2
   }
   ```

2. **Checkout (1st and 2nd orders - no discount code generated)**
   ```bash
   POST /api/checkout
   {
     "cartId": "cart-id-from-step-1"
   }
   ```

3. **Checkout (3rd order - discount code generated)**
   ```bash
   POST /api/checkout
   {
     "cartId": "cart-id"
   }
   # Response includes a discount code
   ```

4. **Use discount code on next order**
   ```bash
   POST /api/checkout
   {
     "cartId": "cart-id",
     "discountCode": "DISCOUNT10-ABC123"
   }
   ```

## Testing with Postman/REST Client

Import the API collection (to be provided) or manually create requests using the endpoints above.

## Assumptions

1. **In-memory storage** - Data is lost when the application restarts
2. **No user authentication** - All endpoints are publicly accessible
3. **Pre-populated items** - Sample items are available in the store
4. **Single currency** - All prices in USD
5. **No inventory management** - Items are always in stock

## Future Enhancements

- Persistent database integration
- User authentication and authorization
- Payment gateway integration
- Email notifications
- Frontend UI
- Inventory management
- Multiple currency support

## Author

Built for Uniblox SDE Assignment

## License

This project is created for assignment purposes.
