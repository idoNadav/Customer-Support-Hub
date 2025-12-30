# Customer Support Hub

A Spring Boot application for managing customer support tickets with cross-database consistency between MySQL (customers) and MongoDB (tickets).

## Architecture Decision: Multi-Module Monolith

### Why Not Microservices?

This application uses a **multi-module Maven monolith** structure instead of microservices for the following reasons:

1. **Assignment Scope**: The requirements focus on cross-database consistency patterns, not distributed system complexity
2. **Simplicity**: A monolith reduces operational overhead (deployment, monitoring, service discovery)
3. **Performance**: No network latency between ticket and customer operations
4. **Transaction Coordination**: Easier to implement saga pattern within a single process
5. **Development Speed**: Faster iteration without inter-service communication overhead

**Structure:**
- `app/` - Main Spring Boot application (orchestrates modules)
  - Security configuration (JWT, OAuth2)
  - Authentication endpoint (`/api/auth/login`)
  - Global exception handling
  - Integration tests
- `customer-service/` - Customer domain (MySQL)
  - Customer CRUD operations
  - Customer search and profile management
- `ticket-service/` - Ticket domain (MongoDB)
  - Ticket CRUD operations
  - Ticket creation saga orchestration
  - Ticket recovery service

This structure provides **modularity** (clear separation of concerns and domain logic) while maintaining **operational simplicity** (single deployment unit). Each module encapsulates its own domain logic, entities, and business rules, making the codebase easier to understand, test, and maintain.

## Deliberate Design Challenge: Cross-Database Consistency

### Problem Statement

When a ticket is created:
1. Ticket must be stored in **MongoDB**
2. Customer must exist in **MySQL**
3. `customers.openTicketCount` must be incremented in **MySQL**

**Constraint:** No distributed/XA transactions. Must tolerate partial failures, retries, and restarts.

### Chosen Approach: Saga Pattern with Idempotency

#### Implementation Strategy

**1. Orchestrator-Based Saga**
- `TicketCreationOrchestrator` coordinates the two-phase operation
- Phase 1: Save ticket to MongoDB (always succeeds first)
- Phase 2: Increment customer count in MySQL (may fail)

**2. Idempotency Protection**
- Each ticket creation accepts an optional `Idempotency-Key` header
- If not provided, the system auto-generates a UUID
- Duplicate requests with the same key return the existing ticket
- Prevents duplicate ticket creation on retries

**3. Failure Tracking**
- Tickets have `syncStatus` field: `SYNCED`, `FAILED`
- Failed tickets are persisted in MongoDB with `FAILED` status
- No data loss - ticket exists even if MySQL sync fails

**4. Automatic Recovery**
- `TicketRecoveryService` runs every 5 minutes via `@Scheduled`
- Finds all tickets with `syncStatus = FAILED`
- Retries MySQL synchronization
- Ensures eventual consistency

**5. Resilience Mechanisms**
- **Retry**: `@Retryable` on MySQL operations (3 attempts with exponential backoff)
- **Circuit Breaker**: Resilience4j circuit breaker prevents resource waste when MySQL is consistently down
- **Fail Fast**: Circuit breaker opens after 50% failure rate, preventing unnecessary retries


#### Flow Diagram

---------
1. POST /api/tickets (with optional Idempotency-Key header)
   |
2. Auto-generate UUID if key not provided
   |
3. Check if ticket exists (by idempotency key) → Return existing if found
   |
4. Validate customer exists in MySQL
   |
5. Save ticket to MongoDB (status: SYNCED)
   |
6. Try to increment customer count in MySQL
    ─ Success → Update ticket status: SYNCED 
    ─ Failure → Update ticket status: FAILED
   
7. Scheduler (every 5 min) finds FAILED tickets
   |
8. Retry MySQL sync → Eventually consistent 

--------

### Trade-offs

| Aspect | Trade-off | Justification |
|--------|----------|---------------|
| **Data Loss** | None - tickets always saved | MongoDB is source of truth; MySQL sync is eventually consistent |
| **Consistency** | Eventual (not immediate) | Acceptable for ticket count - slight delay doesn't impact core functionality |
| **Performance** | Slight delay on failure | Normal path is fast; failures handled asynchronously |
| **Recovery Time** | Up to 5 minutes | Configurable scheduler interval; acceptable for non-critical metric |
| **Complexity** | Saga orchestration + recovery | Simpler than distributed transactions; clear failure handling |

### Failure Scenarios

#### Scenario 1: MySQL Fails During Ticket Creation
- **What happens**: Ticket saved in MongoDB, marked `FAILED`
- **User sees**: Error response (500)
- **Recovery**: Scheduler retries within 5 minutes
- **Data safety**: Ticket preserved in MongoDB

#### Scenario 2: MySQL Down for Extended Period
- **What happens**: Circuit breaker opens → fail fast, no retries
- **User sees**: Fast error response (no waiting for retries)
- **Recovery**: Scheduler continues trying; circuit breaker tests recovery every 30 seconds
- **Data safety**: All tickets preserved, will sync when MySQL recovers

#### Scenario 3: Application Restart During Failure
- **What happens**: Failed tickets persist in MongoDB with `FAILED` status
- **Recovery**: Scheduler resumes automatically on startup
- **Data safety**: No data loss, automatic recovery

#### Scenario 4: Duplicate Request (Network Retry)
- **What happens**: Same `Idempotency-Key` header used twice (or client retries with same key)
- **Result**: Returns existing ticket (idempotent)
- **Data safety**: No duplicate tickets created
- **Note**: If no key provided, each request gets a unique auto-generated key


### Justification for Eventual Consistency
Eventual consistency is acceptable because:

1. **Tickets are Source of Truth**: All ticket data is in MongoDB; count can be recalculated if needed
2. **User Experience**: Users can create tickets even if MySQL is temporarily unavailable
3. **Automatic Recovery**: Scheduler ensures consistency within 5 minutes
4. **No Business Impact**: Slight delay in count update doesn't affect ticket creation or customer operations

### Running the Application

From the project root (where `docker-compose.yml` is located):

```bash
mvn -DskipTests clean package
docker compose up --build
```

Then run the application:
```bash
mvn spring-boot:run -pl app
```

**Application runs on:** `http://localhost:8080`

### API Endpoints

- **Authentication**: `/api/auth/login` (public - generates JWT tokens)
- **Tickets**: `/api/tickets`
- **Customers**: `/api/customers`

All endpoints except `/api/auth/login` require JWT authentication.


### Authentication & Authorization

The application uses **OAuth2 JWT-based authentication**:

- **JWT Token**: Required in `Authorization: Bearer <token>` header for all requests
- **JWT Claims**:
  - `sub`: Maps to customer `externalId` (used for customer identification)
  - `roles`: Array of roles (CUSTOMER, AGENT, ADMIN)
- **Role-Based Access**: Roles and permissions are configured in `application.yml` under `security.roles`
- **Role Mapping**: JWT `roles` claim is prefixed with `ROLE_` (e.g., `CUSTOMER` -> `ROLE_CUSTOMER`)

**IDOR (Insecure Direct Object Reference) Protection:**
- **CUSTOMER users** can only access their own data:
  - Can only view/update their own profile via `/api/customers/me`
  - Can only view their own tickets via `/api/tickets/me`
  - Cannot access other customers' profiles or tickets by ID
  - Cannot create tickets for other customers (validated against JWT `sub` claim)
- **AGENT/ADMIN users** have broader access:
  - Can search and view all customers
  - Can view all tickets
  - Can update ticket statuses
- **Enforcement**: JWT `sub` claim (mapped to `customer.externalId`) is used to verify ownership

**Getting JWT Tokens:**

**Option 1: Login Endpoint (Recommended)**
Use the `/api/auth/login` endpoint to generate tokens automatically:

```bash
# Note: For CUSTOMER role, use the auto-generated externalId from the created customer
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "sub": "customer123456",
    "role": "CUSTOMER"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Option 2: Manual Token Creation (jwt.io)**
Create tokens manually using [jwt.io](https://jwt.io):

- **Algorithm**: `HS256` (HmacSHA256)
- **Secret**: `your-256-bit-secret-key-for-testing-purposes-only`
  - This is the default secret configured in `application.yml` under `jwt.secret`
  - Can be overridden via `JWT_SECRET` environment variable
- **Payload examples:**

**CUSTOMER Token:**
```json
{
  "sub": "customer123456",
  "roles": ["CUSTOMER"]
}
```

**AGENT Token:**
```json
{
  "sub": "agent456",
  "roles": ["AGENT"]
}
```

**ADMIN Token:**
```json
{
  "sub": "admin789",
  "roles": ["ADMIN"]
}
```

**Note on Login Endpoint:**
The current `/api/auth/login` endpoint is **simplified for testing purposes**. It accepts `sub` and `role` directly without password validation. 
In a **production system**, this should be replaced with:
- Username/password authentication
- User credential validation against a database
- Proper OAuth2 authorization server (Keycloak, Auth0,..)
- Password hashing and security best practices

---

## API Testing

### Prerequisites

> **Recommended:** Use the `/api/auth/login` endpoint to generate tokens automatically. This is the recommended approach for testing and development.

> **Tip:** A Postman collection is available to simplify testing. Import the collection to quickly test all endpoints with pre-configured requests.

> **Important Workflow:** 
> 1. **First**: Login as **ADMIN** or **AGENT** to create customers
> 2. **Then**: Create a customer using `POST /api/customers` with ADMIN/AGENT token (externalId is auto-generated in format `customer{number}`)
> 3. **Finally**: Login as **CUSTOMER** using the auto-generated `externalId` from the created customer
> 
> The login endpoint validates that the customer exists in the database for CUSTOMER role. AGENT and ADMIN roles do not require pre-registration.

**Step 1: Login as ADMIN/AGENT to Create Customers**
```bash
# Login as ADMIN OR AGENT
curl --location --request POST 'http://localhost:8080/api/auth/login' \
--header 'Content-Type: application/json' \
--data-raw '{
  "sub": "admin-2123",
  "role": "ADMIN"
}'
```

**Step 2: Create a Customer**
```bash
# Create customer (externalId is auto-generated in format "customer{number}")
curl -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ido Cohen",
    "email": "Ido.Cohen@gmail.com"
  }'

# Response includes the auto-generated externalId (use it for customer login)
# {
#   "externalId": "customer123456",
#   ...
# }
```

**Step 3: Login as CUSTOMER**

```bash
# Login as CUSTOMER using the externalId from Step 2 ("customer123456")
CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"sub": "customer123456", "role": "CUSTOMER"}' | jq -r '.token')

export JWT_TOKEN=$CUSTOMER_TOKEN
```

**Option 2: Manual Token Creation (Alternative)**
> **Note:** This method is available but not recommended. Use Option 1 (Login Endpoint) instead.

Use [jwt.io](https://jwt.io) with:
- Algorithm: `HS256`
- Secret: `your-256-bit-secret-key-for-testing-purposes-only`
- Payload: See examples in Authentication & Authorization section

Then set token variable:
```bash
export JWT_TOKEN="<your-generated-jwt-token>"
```

### Authentication Endpoints

#### Login (Get JWT Token)

**Important:** For CUSTOMER role, the customer must be created first by ADMIN/AGENT. Use the `externalId` from the created customer as the `sub` value.

```bash
# Step 1: Login as ADMIN/AGENT (to create customers)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "sub": "admin-456",
    "role": "ADMIN"
  }'

# Step 2: After creating a customer, login as CUSTOMER
# Use the auto-generated externalId from the created customer ("customer123456")
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "sub": "customer123456",  // Must match the auto-generated externalId from created customer
    "role": "CUSTOMER"
  }'

# AGENT role (for creating customers and managing tickets)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "sub": "agent-456",
    "role": "AGENT"
  }'
```

### Customer Endpoints

#### 1. Create Customer (ADMIN/AGENT only)
> **Note:** Only ADMIN and AGENT roles can create customers. CUSTOMER role cannot create accounts. The `externalId` is automatically generated in the format `customer{number}` (e.g., `customer123456`).

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com"
  }'
```

**Response:**
```json
{
  "id": 1,
  "externalId": "customer123456",  // Auto-generated, use this for CUSTOMER login
  "name": "John Doe",
  "email": "john.doe@example.com",
  "openTicketCount": 0
}
```

#### 2. Get Own Profile (CUSTOMER role)
```bash
curl -X GET http://localhost:8080/api/customers/me \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 3. Update Own Profile (CUSTOMER role)
```bash
curl -X PUT http://localhost:8080/api/customers/me \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Updated",
    "email": "john.updated@example.com"
  }'
```

#### 4. Search Customers (AGENT/ADMIN role)
```bash
# Search by name
curl -X GET "http://localhost:8080/api/customers?name=Ido" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Search by email
curl -X GET "http://localhost:8080/api/customers?email=example.com" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Search by externalId
curl -X GET "http://localhost:8080/api/customers?externalId=customer123" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Get all customers
curl -X GET http://localhost:8080/api/customers \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 5. Get Customer by External ID (AGENT/ADMIN role)
```bash
curl -X GET http://localhost:8080/api/customers/customer123 \
  -H "Authorization: Bearer $JWT_TOKEN"
```


### Ticket Endpoints

#### 1. Create Ticket (CUSTOMER/AGENT/ADMIN role)
```bash
# With Idempotency-Key (recommended for retries - prevents duplicates)
curl -X POST http://localhost:8080/api/tickets \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-12345" \
  -d '{
    "customerExternalId": "customer123",
    "title": "Unable to login",
    "description": "I cannot access my account",
    "status": "OPEN",
    "priority": "HIGH"
  }'

# Without Idempotency-Key (system auto-generates UUID)
curl -X POST http://localhost:8080/api/tickets \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "customerExternalId": "customer123",
    "title": "Feature request",
    "description": "Please add dark mode",
    "priority": "MEDIUM"
  }'
```

#### 2. Get Tickets (AGENT/ADMIN role)
```bash
# Get all tickets
curl -X GET http://localhost:8080/api/tickets \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by status
curl -X GET "http://localhost:8080/api/tickets?status=OPEN" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by priority
curl -X GET "http://localhost:8080/api/tickets?priority=HIGH" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by customer
curl -X GET "http://localhost:8080/api/tickets?customerExternalId=customer123" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by date range
curl -X GET "http://localhost:8080/api/tickets?fromDate=2024-01-01T00:00:00&toDate=2024-12-31T23:59:59" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Combined filters
curl -X GET "http://localhost:8080/api/tickets?status=OPEN&priority=HIGH&customerExternalId=customer123" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 3. Get Own Tickets (CUSTOMER role)
```bash
# Get all own tickets
curl -X GET http://localhost:8080/api/tickets/me \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by status
curl -X GET "http://localhost:8080/api/tickets/me?status=OPEN" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Filter by priority
curl -X GET "http://localhost:8080/api/tickets/me?priority=HIGH" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 4. Get Ticket by ID (CUSTOMER/AGENT/ADMIN role)
```bash
curl -X GET http://localhost:8080/api/tickets/<ticket-id> \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 5. Add Comment to Ticket (CUSTOMER/AGENT/ADMIN role)
```bash
curl -X POST http://localhost:8080/api/tickets/<ticket-id>/comments \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '"This is a comment on the ticket"'
```

#### 6. Update Ticket Status (AGENT/ADMIN role)
```bash
curl -X PUT http://localhost:8080/api/tickets/<ticket-id>/status \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '"IN_PROGRESS"'
```

### Testing notes
1. **Replace `<ticket-id>`** with actual ticket ID from create response
2. **Use different JWT tokens** for different roles to test authorization
3. **Idempotency-Key**: Use the same key to test idempotency (should return existing ticket)
4. **Date format**: Use format: `YYYY-MM-DDTHH:mm:ss`

---

## Testing

Integration tests use Testcontainers for isolated database testing.

