# DeepFake Forensic Tool

This repo runs infra (MySQL/Redis/Kafka/MinIO) in Docker and the backend locally with Java 17. The frontend runs with Node (npm). Follow these steps in order.

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