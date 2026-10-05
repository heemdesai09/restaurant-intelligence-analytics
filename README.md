# Restaurant Intelligence & Analytics System
**Course:** Advanced Big Data Analytics (ABDA)  
**Student:** Heem Desai (Enrollment: 23162121003)  
**Branch:** B.Tech Big Data Analytics (Semester 7)  
**Technology Stack:** Scala 3, MongoDB Atlas, sbt, HTML5 / CSS3 / Vanilla JS  

---

## 1. Project Overview

The **Restaurant Intelligence & Analytics System** is a big data analytics application designed to store, manage, query, and analyze restaurant inspection records from New York City. The project leverages MongoDB's official `sample_restaurants` dataset (`restaurants` collection) and demonstrates end-to-end Big Data Analytics workflows using **Scala 3** and **MongoDB Atlas**.

The application provides:
1. **Interactive Menu-Driven CLI:** A terminal-based interface supporting full CRUD operations, index inspection, and multi-stage aggregation analytics.
2. **Clean Academic Web Dashboard:** A responsive, light-themed web interface powered by a built-in Scala HTTP server that communicates with MongoDB via REST endpoints.

---

## 2. Technologies Used

- **Programming Language:** Scala 3.3.3
- **Build Tool:** sbt 1.9.9
- **Database:** MongoDB Atlas (or standalone MongoDB 8.x)
- **Database Driver:** `org.mongodb:mongodb-driver-sync:4.11.1`
- **JSON Serialization:** `com.lihaoyi:upickle:3.3.0`
- **HTTP Engine:** Lightweight embedded `com.sun.net.httpserver.HttpServer` (zero external framework bloat)
- **Frontend:** Semantic HTML5, Vanilla CSS3 (Custom Academic Theme), Vanilla JavaScript (ES6 Fetch API)

---

## 3. MongoDB Setup & Dataset

### Database & Collection
- **Database Name:** `sample_restaurants`
- **Collection Name:** `restaurants`
- **Dataset:** Official MongoDB sample restaurant dataset (~25,359 documents).

### MongoDB Atlas Setup Steps:
1. Log in to your [MongoDB Atlas](https://cloud.mongodb.com) account.
2. Under your cluster, click **"..."** (More Options) and select **"Load Sample Dataset"**. MongoDB will automatically load `sample_restaurants`.
3. In **Database Access**, create a user (e.g., `abda_db_user`) with `readWrite` privileges on `sample_restaurants`.
4. In **Network Access**, add your current IP address (or `0.0.0.0/0` for development/cloud access).
5. Click **Connect** &rarr; **Drivers** &rarr; Copy your connection string.

---

## 4. Environment Variable Configuration

The application strictly reads the connection URI from the environment variable `MONGODB_URI` (or from a local `.env` file). Credentials are never hardcoded in source code.

Copy the provided `.env.example` to `.env`:

```bash
cp .env.example .env
```

Edit `.env` with your MongoDB Atlas connection string:

```env
# MongoDB Atlas Connection URI
MONGODB_URI=mongodb+srv://<username>:<password>@<cluster-url>/?retryWrites=true&w=majority

# Target database: sample_restaurants
# Target collection: restaurants

# Optional Server Port (default: 8080 locally; set dynamically by cloud providers)
PORT=8080
```

> **Note:** If running a local MongoDB instance, you can use:
> `MONGODB_URI=mongodb://localhost:27017`

---

## 5. Project Structure

```
.
├── backend/
│   ├── build.sbt                            # sbt project definition & dependencies
│   ├── project/
│   │   ├── build.properties                 # Declares sbt version (1.9.9)
│   │   └── plugins.sbt                      # sbt-assembly plugin for fat JAR packaging
│   └── src/main/scala/restaurant/
│       ├── Main.scala                       # Entry point, CLI menu loop, server lifecycle
│       ├── MongoConfig.scala                # MongoDB Atlas connection, .env loader, ping check
│       ├── Restaurant.scala                 # Domain models (case classes) & JSON codecs
│       ├── RestaurantRepository.scala       # Trait & Mongo implementation (CRUD & BSON filters)
│       ├── RestaurantService.scala          # Business validation, orchestration, error handling
│       ├── AnalyticsService.scala           # 5 MongoDB aggregation pipelines & index management
│       └── WebServer.scala                  # Embedded HTTP server & REST route handlers
├── frontend/
│   ├── index.html                           # Clean academic college dashboard
│   ├── style.css                            # Custom light-theme CSS
│   └── app.js                               # REST API integration & SVG chart renderer
├── Dockerfile                               # Multi-stage production container build for Render
├── .dockerignore                            # Excludes secrets and build artifacts from container
├── .env.example                             # Environment variable template with safe placeholders
├── .gitignore                               # Git ignore for target/, .bsp/, .env
└── README.md                                # Project documentation
```

---

## 6. How to Run

Navigate to the `backend/` directory or run from the project root:

### Option A: Run Both Web Server & CLI (Default)
```bash
cd backend
sbt run
```
- Automatically verifies MongoDB Atlas connection.
- Ensures required indexes.
- Starts the embedded web server on `http://localhost:8080`.
- Launches the interactive menu-driven CLI in the terminal simultaneously.

### Option B: Run Web Server Mode Only
```bash
cd backend
sbt "run web"
```
Then open `http://localhost:8080` in your web browser.

### Option C: Run CLI Mode Only
```bash
cd backend
sbt "run cli"
```

---

## 7. CRUD Operations

### 1. Add Restaurant (Create)
- **CLI:** Option `1` &rarr; prompts for Name, Cuisine, Borough, Building, Street, ZIP, Inspection Score, and Grade.
- **Web UI:** Navigate to **"Add Restaurant"** tab &rarr; Fill form or click **'Load "Heem Test Bistro 01"'** &rarr; Submit.
- **Server Validation:**
  - Name cannot be empty.
  - Inspection score cannot be negative.
  - Safe defaults applied for missing address components.

### 2. View / Search Restaurants (Read)
- **CLI:** Option `2` &rarr; Enter filter criteria (or leave blank to view latest records). Displays a formatted ASCII table.
- **Web UI:** Navigate to **"Restaurant Finder"** tab &rarr; Enter search filters &rarr; View responsive table with pagination up to 50 documents.

### 3. Update Restaurant (Update)
- **CLI:** Option `3` &rarr; Enter Restaurant ID (e.g. `HEEM_01`) &rarr; Enter new name, cuisine, address, or press Enter to retain current values.
- **Web UI:** In the search table, click the **"Edit"** button on any record &rarr; Modify fields in modal &rarr; Click **"Save Changes"**.

### 4. Delete Restaurant (Delete)
- **CLI:** Option `4` &rarr; Enter Restaurant ID &rarr; Type `y` to confirm deletion.
- **Web UI:** In the search table, click the **"Delete"** button &rarr; Confirm prompt &rarr; Document is deleted from MongoDB Atlas.

### Test Records Created for Assignment Verification:
- `Heem Test Bistro 01` (ID: `HEEM_01`, Cuisine: `Indian`, Borough: `Queens`, Score: `15`, Grade: `A`)
- `Heem Test Bistro 02` (ID: `HEEM_02`, Cuisine: `Italian`, Borough: `Manhattan`, Score: `14`, Grade: `B`) &mdash; deleted to verify delete functionality.

---

## 8. Multi-Filter Search Capabilities

The application queries MongoDB directly using `com.mongodb.client.model.Filters` combined with `Filters.and(...)`, preventing memory exhaustion by never loading the full collection unnecessarily.

Supported filters (usable individually or combined):
- **Restaurant Name:** Case-insensitive regular expression (`Filters.regex("name", "(?i)...")`)
- **Cuisine:** Case-insensitive regular expression (`Filters.regex("cuisine", "(?i)...")`)
- **Borough:** Case-insensitive exact/prefix match (`Filters.regex("borough", "(?i)...")`)
- **ZIP Code:** Exact match on embedded field (`Filters.eq("address.zipcode", zip)`)
- **Minimum Inspection Score:** Range query on array subdocument (`Filters.gte("grades.score", minScore)`)

**Assignment Example:**
- `Cuisine = Indian`
- `Borough = Queens`
- `Minimum Score = 8`
*(Click the quick shortcut button on the web interface to execute this exact query).*

---

## 9. MongoDB Indexes

The application automatically creates and verifies 3 key indexes:

1. **`name_1` (Single Field Index):**
   ```scala
   Indexes.ascending("name")
   ```
   *Purpose:* Optimizes name lookups, case-insensitive substring searches, and alphabetical sorting.

2. **`cuisine_1` (Single Field Index):**
   ```scala
   Indexes.ascending("cuisine")
   ```
   *Purpose:* Accelerates cuisine filtering and `$group` aggregations by cuisine.

3. **`borough_1_cuisine_1` (Compound Index):**
   ```scala
   Indexes.compoundIndex(Indexes.ascending("borough"), Indexes.ascending("cuisine"))
   ```
   *Purpose:* Enables index prefix scans and optimal performance when queries filter simultaneously by Borough and Cuisine (e.g., finding Italian restaurants in Manhattan or Indian restaurants in Queens).

---

## 10. MongoDB Aggregation Pipelines

Implemented in `AnalyticsService.scala` using genuine MongoDB aggregation stages:

### Pipeline 1: Restaurants by Cuisine (Top 10)
```javascript
[
  { $match: { cuisine: { $ne: null, $ne: "" } } },
  { $group: { _id: "$cuisine", count: { $sum: 1 } } },
  { $sort: { count: -1 } },
  { $limit: 10 }
]
```

### Pipeline 2: Restaurants by Borough
```javascript
[
  { $match: { borough: { $nin: [null, "", "Missing"] } } },
  { $group: { _id: "$borough", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
]
```

### Pipeline 3: Average Inspection Score by Cuisine
```javascript
[
  { $unwind: "$grades" },
  { $match: { "grades.score": { $gte: 0 }, cuisine: { $nin: [null, ""] } } },
  { $group: { _id: "$cuisine", avgScore: { $avg: "$grades.score" }, count: { $sum: 1 } } },
  { $match: { count: { $gte: 20 } } },
  { $sort: { avgScore: -1 } },
  { $limit: 10 }
]
```

### Pipeline 4: Top-Rated Restaurants
```javascript
[
  { $unwind: "$grades" },
  { $match: { "grades.score": { $gte: 0 } } },
  { $group: {
      _id: "$restaurant_id",
      name: { $first: "$name" },
      cuisine: { $first: "$cuisine" },
      borough: { $first: "$borough" },
      avgScore: { $avg: "$grades.score" },
      reviewCount: { $sum: 1 }
    }
  },
  { $match: { reviewCount: { $gte: 2 } } },
  { $sort: { avgScore: -1 } },
  { $limit: 10 }
]
```

### Pipeline 5: Inspection Grade Distribution
```javascript
[
  { $unwind: "$grades" },
  { $match: { "grades.grade": { $in: ["A", "B", "C", "P", "Z", "Not Yet Graded"] } } },
  { $group: { _id: "$grades.grade", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
]
```

---

## 11. Scala Concepts Demonstrated

- **Variables:** `val` for immutability; controlled `var` in CLI loops.
- **Data Types:** `String`, `Int`, `Double`, `Long`, `Boolean`, `List[T]`, `Map[K, V]`.
- **Conditions:** `if / else` chains for validation and parameter fallbacks.
- **Functions & Methods:** Modular methods across traits, classes, and companion objects.
- **Collections:** `List`, `Seq`, `Map`, `Array`.
- **Higher-Order Functions:** `.map`, `.filter`, `.groupBy`, `.flatMap`, `.foreach`.
- **Option Type:** `Option[String]`, `Option[Int]` with `getOrElse`, `fold`, and pattern matching for safe nullable representation.
- **Pattern Matching:** Exhaustive matching on CLI inputs, API route tuples `(method, path)`, and `Try` results (`Success` vs `Failure`).
- **Exception Handling:** Robust `try / catch / finally` and `scala.util.Try` protecting all database calls and HTTP request decoders.
- **Case Classes:** Typed data models (`Restaurant`, `Address`, `Grade`, `SearchFilter`, `IndexInfo`, `SystemOverview`).
- **Companion Objects:** Factory objects with implicit upickle JSON codecs (`object Restaurant`, `object Address`).
- **Encapsulation:** Clear separation of configuration (`MongoConfig`), persistence (`RestaurantRepository`), validation (`RestaurantService`), and analytics (`AnalyticsService`).
- **Trait / Composition:** `trait RestaurantRepository` defining interface abstraction with `class MongoRestaurantRepository` implementation.

---

## 12. Deployment (Render Web Service)

This application is containerized with a production multi-stage [Dockerfile](file:///Users/heemdesai/YEAR%204/ABDA/ASSIGNMENT/Dockerfile) and can be deployed directly to [Render](https://render.com) as a **Web Service**.

### Step-by-Step Render Deployment Guide:

1. **Push your code to GitHub:**
   Ensure your repository is pushed to your GitHub account (see Git instructions below).

2. **Create New Web Service on Render:**
   - Log in to your [Render Dashboard](https://dashboard.render.com).
   - Click **"New +"** &rarr; select **"Web Service"**.
   - Connect your GitHub repository.

3. **Configure Service Settings:**
   - **Name:** `restaurant-intelligence-analytics` (or your preferred name)
   - **Region:** Choose the region closest to your MongoDB Atlas cluster (e.g., Oregon, Frankfurt, Singapore).
   - **Environment / Runtime:** Select **Docker** (Render automatically detects the root `Dockerfile`).
   - **Plan:** Free (or Starter).

4. **Set Environment Variables:**
   Under **Environment Variables**, add:
   - `MONGODB_URI`: Your MongoDB Atlas connection URI:
     ```text
     mongodb+srv://<username>:<password>@<cluster-url>/?retryWrites=true&w=majority
     ```
   *(Render will automatically inject the `PORT` variable and bind traffic to `0.0.0.0:$PORT`).*

5. **Deploy:**
   - Click **"Deploy Web Service"**.
   - Render will build the Docker container using sbt, package the standalone assembly JAR, and launch the web server.
   - Once deployed, your web dashboard will be live at `https://<your-service-name>.onrender.com`.

---

## 13. GitHub Repository Setup

To initialize this repository locally and push to your GitHub account:

```bash
# 1. Initialize git (if not already initialized)
git init

# 2. Verify git status (confirm .env is ignored)
git status

# 3. Stage all files (excluding ignored items)
git add .

# 4. Commit changes
git commit -m "feat: complete Restaurant Intelligence & Analytics System with Scala, MongoDB, and Docker"

# 5. Link to your GitHub remote repository
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git branch -M main

# 6. Push to GitHub
git push -u origin main
```

