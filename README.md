# DeepFake Forensic Tool

A comprehensive forensic tool for detecting and analyzing deepfake, digitally altered, and synthetic media.

## Architecture and Startup

This project uses a hybrid deployment architecture with four main components:

### Component Overview

**1. Docker Infrastructure**
- MySQL 8 (Database) on localhost:3306
- Redis 7 (Cache) on localhost:6379
- Kafka 3.9 (Message Queue) on localhost:9092
- MinIO (Object Storage) on localhost:9000 (Console: localhost:9001)

**2. Backend (Java 17 + Spring Boot)**
- Runs locally on port 8082
- Handles file uploads, authentication, metadata analysis

**3. Frontend (React + TypeScript + Vite)**
- Runs locally on port 3000
- Provides user interface

**4. Python Analysis Service (Flask + Kafka Worker)**
- Flask API runs locally on port 7000
- Kafka Worker consumes analysis tasks
- Provides AI model inference and traditional video analysis

### Complete Startup Steps

**Step 1: Start Docker Infrastructure**
```powershell
# Windows PowerShell
.\start-docker.ps1

# Or clean start
.\start-docker.ps1 -Clean

# Check status
.\status-docker.ps1
```

**Step 2: Start Backend**
```powershell
cd backend
mvn clean compile

# Set database password (must match Docker)
$env:MYSQL_ROOT_PASSWORD="changeme"

mvn spring-boot:run
```

Backend runs at: http://localhost:8082

**Step 3: Start Frontend**
```powershell
cd frontend
npm install
npm run dev
```

Frontend runs at: http://localhost:3000

**Step 4: Start Python Analysis Service (Optional, for AI analysis)**
```powershell
# Terminal 1: Start Flask API
cd py
.\run-app.ps1

# Terminal 2: Start Kafka Worker
cd py
.\run-worker.ps1
```

Python service runs at: http://localhost:7000

---

## Backend

The backend service for the DeepFake Forensic tool provides file upload and metadata analysis capabilities for forensic investigation of deepfake and synthetic media.

### Prerequisites

- **Docker Desktop** (running)

- **Java 17** (JDK)

- **Maven 3.9+**

- **Node.js 18+** and **npm**

### Architecture

```
├── upload/                 # File upload module
│   ├── controller/        # REST API endpoints
│   ├── service/           # Business logic
│   ├── repository/        # Data access layer
│   ├── entity/           # JPA entities
│   └── dto/              # Data transfer objects
├── metadata/              # Metadata analysis module
│   ├── controller/        # REST API endpoints
│   ├── service/           # Analysis logic
│   ├── repository/        # Data access layer
│   ├── entity/           # JPA entities
│   └── dto/              # Data transfer objects
└── config/               # Configuration classes
```


### Getting Started
#### 1) Start infrastructure (Docker)

```bash
# from repo root
chmod +x start-docker.sh
./start-docker.sh
```

This brings up:
- MySQL → localhost:3306

- Redis → localhost:6379

- Kafka → localhost:9092

- MinIO → localhost:9000 (console: http://localhost:9001)

The MySQL root password is read from your local .env and must match what the backend uses (see step 3). Don’t commit your .env.

#### 2) Use Java 17 in this terminal session

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"

java -version   # should show 17.x
mvn -v          # Maven should also report Java 17.x
```

#### 3) Build & run the backend (Spring Boot, port 8082)

```bash
cd backend
mvn -U clean compile

# password you used for Docker MySQL (from your .env)
export MYSQL_ROOT_PASSWORD=changeme

mvn spring-boot:run
```

The backend should now be available at: http://localhost:8082

## Frontend

A modern React TypeScript application for detecting and analyzing deepfake, digitally altered, and synthetic media. The frontend communicates with the backend through REST APIs for file upload, metadata analysis, and file management.

### Prerequisites

- Node.js 20.16+ 
- npm or yarn
- Backend API running on http://localhost:8082

### Main Features
#### 🔄 File Upload
- Chunked upload support for large files (up to 1GB)
- Drag & drop interface
- Real-time upload progress
- File type validation
- Duplicate file detection
- Resume interrupted uploads

#### 📁 File Management
- File list with pagination and filtering
- File preview for images and videos
- Metadata display
- File operations (delete, analyze)
- Search and sort functionality

#### 🔍 Metadata Analysis
- EXIF data extraction and display
- File header analysis
- Hash verification (MD5, SHA-256)
- Anomaly detection with risk scoring
- Interactive metadata tree view
- Analysis history tracking

#### 📊 Dashboard
- Statistics overview
- Recent activity timeline
- System status indicators
- Quick access to key functions

### Project Structure

```
src/
├── components/           # Reusable React components
├── pages/               # Page components
├── services/            # API services
├── hooks/               # Custom React hooks
├── types/               # TypeScript type definitions
├── utils/               # Utility functions
├── constants/           # Application constants
└── App.tsx              # Main application component
```

### Getting Started

```bash
# Start development server
cd frontend
npm install
npm run dev
```

The frontend should now be available at: http://localhost:3000/

## Testing

### Backend unit tests (Maven + JUnit 5 + JaCoCo)

Location:
- Place tests under `backend/src/test/java` following the same package structure as the code.
- Existing tests: `DummySmokeTest.java`, `FileTypeValidationServiceTest.java`, `JwtTokenUtilTest.java`, `NotificationUtilTest.java`

Run tests locally:
```bash
# from repo root
cd backend
mvn test                    # Run tests only
mvn jacoco:report          # Generate coverage report
mvn clean test jacoco:report # Clean, test, and generate report
```

Coverage reports:
- Coverage reports are generated in `backend/target/site/jacoco/` directory
- Open `backend/target/site/jacoco/index.html` in browser to view detailed coverage
- JaCoCo excludes entities, DTOs, config classes, and main application class

Tips:
- Tests focus on pure business logic (services, utilities, validators) without database dependencies
- Uses Mockito for mocking dependencies (e.g., EmailService)
- JWT token tests include expiration and validation scenarios
- File validation tests cover various file types and edge cases

### Frontend unit tests (Vitest + jsdom)

Location:
- Place tests under `frontend/Test` with filenames like `*.test.ts` or `*.test.tsx`. A sample test exists at `frontend/Test/sample.test.ts`.

Install once (if not already):
```bash
cd frontend
npm install
```

Run tests locally:
```bash
cd frontend
npm test                    # Run tests without coverage
npm run test:coverage      # Run tests with coverage report
npm run test:ui            # Open Vitest UI in browser
```

Coverage reports:
- Coverage reports are generated in `frontend/coverage/` directory
- Open `frontend/coverage/index.html` in browser to view detailed coverage
- Coverage thresholds are set to 0% initially (can be adjusted in `vite.config.ts`)

Notes:
- The test environment is `jsdom` and configured in `frontend/vite.config.ts` under the `test` section.
- Coverage excludes test files, config files, and main entry points to avoid 0% coverage on untested files.
- Update the include pattern there if you prefer a different test folder layout.

## License and Copyright

This project is developed as part of an academic program. All intellectual property rights are subject to university and industry partner agreements. 

For academic and educational purposes only. Unauthorized commercial use or redistribution is prohibited.
