# Step-by-Step Guide

----------------------------

--------------

### Step 0: Project Restructuring & Preservation (Preparation Step)

#### Goal: Preserve the existing JavaFX client, set up clear separation, and prepare for minimal-disruption migration.

##### Before splitting, preserve my existing JavaFX monolithic app as a desktop client.

Please:

1. Create this top-level structure in the project root:

   /backend/          (new Spring Boot REST API)   /frontend/         (new Angular 17 web UI)   /desktop/          (
   refactored JavaFX client for Win/Mac/Linux)     src/main/java/... (copy existing JavaFX code here)
   pom.xml           (JavaFX + HTTP client dependencies)

2. Copy the entire existing JavaFX source code into /desktop/ without modifications yet. 3. Update the desktop pom.xml
   to use Java 21 initially (we'll upgrade later), add Spring WebFlux WebClient or RestTemplate + WebSocket support for
   future API consumption. 4. Do NOT change any business logic yet.

Expected output: New folder structure with JavaFX code preserved in /desktop/.

\---

### Step 1: Set Up the Workspace Structure (First Prompt) Goal: Create the folder structure for both projects. (Backend + Frontend + preserved Desktop)

##### I want to split my monolithic JavaFX/Spring app into:

1. Backend: Spring Boot REST API (multi-threaded, transactional) 2. Frontend: Angular 17+ (modern UI, responsive,
   themable) 3. Desktop: JavaFX client (existing code preserved as platform client for Win/Mac/Linux)

Please create the following folder structure (building on the existing /desktop/):

/backend/ src/main/java/... src/main/resources/... pom.xml (Spring Boot 3.x)

/frontend/ src/app/... src/assets/... angular.json package.json

/desktop/ (already populated with JavaFX code)

Then generate the initial pom.xml for the backend (Spring Boot 3.x with Spring Web, JPA, PostgreSQL, Security,
WebSocket) and package.json for the frontend (Angular 17, Angular Material, RxJS, ECharts). Expected output: Folder
structure confirmation + pom.xml + package.json.

### Step 2: Extract Data Models (Second Prompt)

#### Goal: Move JPA entities from the old app to the new backend.

##### From my existing JavaFX app, I have these JPA entities:

- UserProfile
- Trade
- PriceAlert
- IndicatorConfig
- ChartDrawing
- OhlcvBar
- etc...

Please copy these entity classes to the new Spring Boot backend (backend/src/main/java/.../model/). Keep all JPA
annotations (@Entity, @Id, @ManyToOne, etc.) and Lombok annotations.

Do NOT add any new logic yet – just copy the entity classes as-is.
Expected output: Entity classes copied over.

### Step 3: Create Repository Interfaces (Third Prompt)

#### Goal: Generate Spring Data JPA repositories for each entity.

text

##### For each entity in backend/src/main/java/.../model/, copy corresponding repository interface in backend/src/main/java/.../repository/ (and create if not exist and also is needed):

1. UserProfileRepository
2. TradeRepository
3. PriceAlertRepository
4. IndicatorConfigRepository
5. ChartDrawingRepository
6. OhlcvBarRepository
7. etc...

Each repository should extend JpaRepository and include any custom query methods I might need (e.g., find by profile,
find by symbol).

Expected output: Repository interfaces.

### Step 4: Design the REST API (Fourth Prompt)

#### Goal: Define all REST endpoints with DTOs.

Prompt to Cursor:

text

##### I need to design REST APIs for my trading platform. Please create:

1. A list of all REST endpoints I need (based on my existing services: TradeService, PriceRouter, AlertService,
   AnalysisService, etc.)

2. Request/response DTO classes for each endpoint (e.g., TradeRequest, TradeResponse, AlertRequest, etc.)

3. Controller skeletons for each category:
    - AuthController
    - ProfileController
    - TradeController
    - ChartController (OHLCV, indicators)
    - AlertController
    - DrawingController
    - SearchController
    - AIController
    - FundamentalsController
    - ExportController
    - SettingsController
    - etc...

Put all controllers in backend/src/main/java/.../controller/ and DTOs in backend/src/main/java/.../dto/.

DO NOT implement the business logic yet – just the endpoints and DTOs.
Expected output: Controller skeletons + DTO classes.

### Step 5: Migrate ONE Service at a Time (Repeat for Each Service)

Prompt : (Example – TradeService):

text

##### I want to migrate my existing TradeService from the JavaFX app to the new Spring Boot backend.

Please:

1. Copy the business logic from the old TradeService (CRUD, close trade, portfolio stats) into a new TradeService in
   backend/src/main/java/.../service/.

2. Inject the TradeRepository and UserProfileRepository via constructor.

3. Add @Service and @Transactional annotations.

4. Connect it to the TradeController endpoints (POST /trades, PUT /trades/{id}, DELETE /trades/{id}, POST
   /trades/{id}/close, GET /portfolio/stats etc...).

5. Do NOT add any external API calls or price-fetching logic yet (I'll do that in the next step).

Repeat this for each service:

PriceRouter / ChartDataService

AlertService

AnalysisService

IndicatorMixerService

DrawingService

SearchService

AINewsService

FundamentalsService

ExportService

SettingsService

### Step 6: Add Authentication (Separate Prompt)

text

##### I need to add JWT authentication to my Spring Boot backend.

Please implement:

1. SecurityConfig – configure Spring Security to use JWT (stateless)
2. JwtUtil – generate/validate JWT tokens
3. JwtAuthFilter – intercept requests and validate tokens
4. AuthController – POST /auth/login and POST /auth/register
5. AuthService – handle login/registration logic
6. CustomUserDetailsService – load user by username

Use the existing AppUser and RolePermission entities.

Show me all the code and any necessary dependency additions.
Expected output: Complete authentication code.

### Step 7: Implement Angular Frontend – ONE Module at a Time

Same strategy – one module per prompt.

(Example – Dashboard Module):

#### I'm building an Angular 17 frontend. Create a reusable, professional **standalone ChartLibraryModule
** (that can be published/used in other applications) with:

1. High-quality, production-grade candlestick chart component with:   - Professional styling (dark/light theme support,
   responsive)   - Technical indicators overlay (SMA, EMA, RSI, MACD, Bollinger Bands, etc.)   - Drawing tools support (
   trend lines, Fibonacci, rectangles, etc.)   - Zoom, pan, crosshair, time-range selector - Real-time price updates via
   WebSocket
2. Use ECharts (or lightweight wrapper) for performance. Make the module tree-shakable and exportable.
3. Include:   - chart-library.module.ts - candlestick-chart.component.ts/html/scss - indicator.service.ts -
   drawing-tools.service.ts - Shared models/DTOs

Use Angular Material where appropriate and SCSS with CSS variables for theming.

Continue with other modules (Dashboard, Journal, etc.) as the below:

1. A DashboardModule (standalone component)
2. DashboardComponent with:
    - KPI cards (total P&L, win rate, trade counts)
    - Equity curve chart (using ECharts or D3)
    - Asset breakdown (CRYPTO/STOCK/FOREX progress bars)
    - Recent trades table (last 20 trades)

3. A DashboardService that calls GET /api/portfolio/stats and GET /api/trades/recent

Use Angular Material for UI components and ECharts for charts.

Show me:

- dashboard.component.ts
- dashboard.component.html
- dashboard.service.ts
- Any necessary models/DTOs
  Repeat for each module:

ChartModule (candlestick chart with indicators, drawing tools)

JournalModule (trade list with CRUD)

AlertsModule

IndicatorMixerModule

ProfileSettingsModule

etc.

### Step 8: Implement WebSocket for Real-Time Data (Separate Prompt)

#### I need to stream real-time price data from the backend to the Angular frontend via WebSocket.

In the backend:

- Implement a WebSocketConfig with STOMP endpoints
- Create a PriceWebSocketController that broadcasts price updates
- Connect to Binance WebSocket and forward updates to connected clients

In the frontend:

- Create a PriceWebSocketService that connects to the STOMP endpoint
- Subscribe to price updates and update the ticker bar and charts in real time

Expected output: WebSocket configuration + service code.

### Step 9: Refactor JavaFX Desktop Client to Consume Backend API Goal: Turn the preserved JavaFX app into a thin client that talks to the new Spring Boot backend.

#### Now that the backend is ready, refactor the JavaFX desktop client in /desktop/ to act as a remote client:

1. Replace direct service calls with HTTP (WebClient/RestTemplate) calls to backend REST endpoints. 2. Add
   WebSocket/STOMP client for real-time price updates. 3. Keep the existing JavaFX UI and business logic structure
   mostly intact. 4. Inject configuration for backend URL (use properties file). 5. Update any local database access to
   use backend APIs instead.

Do this one screen/service at a time if needed. Start with authentication and portfolio/trades.

Show updated classes and any new client utilities.

\---

### Step 10: Final Version Upgrades Goal: Upgrade to latest stable versions (after everything works).

##### Upgrade the entire project to latest stable versions:

Backend: - Java 21 → Java 25 (or latest LTS/stable) - Spring Boot 3.x → Spring Boot 4.1 (latest stable)

Desktop (JavaFX): - Update to Java 25 + latest JavaFX version - Update dependencies accordingly

Frontend: - Ensure Angular 17+ is up-to-date with latest compatible packages

Provide: 1. Updated pom.xml (backend & desktop) 2. Any required code changes for compatibility 3. Build instructions for
all three parts

Test that everything still works after upgrade.

\---

General Rules for Token Efficiency [Keep original section unchanged]

Update the .cursorrules example:

Add to General rules: - Use latest stable Java (target Java 25+) and Spring Boot 4.1 at the end of migration - Desktop
client: JavaFX for cross-platform (Win/Mac/Linux) - Angular: Include a reusable professional ChartLibraryModule

\---

Suggested Prompt Order (Priority-Based – Updated)

1. Step 0: Project restructuring & preserve JavaFX (P0)
2. Step 1: Backend + Frontend structure (P0)
3. Step 2-4: Entities, Repos, API design (P0)
4. Step 6: Authentication (P0)
5. Step 5: Migrate services one by one (P1)
6. Step 7: Angular modules, with emphasis on reusable ChartLibrary (P1)
7. Step 8: WebSocket (P1)
8. Step 9: Refactor JavaFX Desktop client (P1)
9. Step 10: Final Java/Spring upgrades (P2)

Summary [Updated accordingly]

1. Create .cursorrules file (with new rules)
1. Preserve & restructure (Step 0-1)
1. Backend core (Steps 2-6)
1. Angular (Step 7, focused on reusable chart)
1. Desktop client integration (Step 9)
1. WebSocket & polish
1. Final upgrades (Step 10)

#### General Rules for Token Efficiency

Rule Why
One task per prompt Prevents Cursor from generating redundant code
Ask for specific files Avoids "show me the whole project" prompts
Use "DO NOT" constraints Prevents Cursor from adding unwanted features
Ask for code only Skip explanations unless needed
Reference existing code Say "use the same pattern as X" to save tokens
Set up .cursorrules file Persistent instructions that apply to every prompt

#### Example .cursorrules File

Create a .cursorrules file in your project root with:

text:

##### You are an expert Java/Spring Boot/Angular developer.

General rules:

- Use Java 21, Spring Boot 3.x, Angular 17
- Use Lombok for boilerplate code
- Use JPA/Hibernate for database
- Use JWT for authentication
- Use SCSS with CSS variables for theming
- Write clean, well-commented code
- Follow standard REST conventions
- Add OpenAPI/Swagger annotations to all controllers

Backend rules:

- Use @Service, @Repository, @Controller annotations
- Use @Transactional for all write operations
- Validate all incoming DTOs with @Valid
- Use proper HTTP status codes (200, 201, 400, 404, 500)
- Implement global exception handling with @ControllerAdvice

Frontend rules:

- Use Angular 17 standalone components
- Use Angular Material for UI
- Use ECharts for charts
- Use RxJS for state management
- Use lazy loading for routes
  This file tells Cursor your preferences every time – you don't need to repeat them.

Suggested Prompt Order (Priority-Based)
Order Task Priority
1 Set up folder structure P0
2 Copy entities + repositories P0
3 Design REST API + DTOs P0
4 Implement Auth (JWT)    P0
5 TradeService + TradeController P1
6 Portfolio stats + endpoints P1
7 Chart data + OHLCV endpoints P1
8 AlertService + AlertController P1
9 PriceRouter + provider integrations P1
10 DrawingService + DrawingController P2
11 IndicatorMixerService P2
12 AINewsService P2
13 Angular Dashboard P1
14 Angular Chart + drawing tools P1
15 Angular Journal P1
16 Angular Alerts P1
17 WebSocket streaming P2
18 Theme system P2
19 Export functionality P2
20 NFT Explorer P3
Summary
Step Action
1 Create .cursorrules file
2 Start with folder structure (Prompt 1)
3 Entities → Repositories → DTOs → Controllers (Prompts 2–4)
4 Migrate one service at a time (Prompt 5, repeat)
5 Add authentication (Prompt 6)
6 Build Angular one module at a time (Prompt 7, repeat)
7 Add WebSocket (Prompt 8)
8 Test and iterate
Following this pattern, you'll save 70-80% of tokens compared to asking Cursor to do everything in one giant prompt.