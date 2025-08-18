# DeepFake Forensic Backend

This is the backend service for the DeepFake Forensic tool, providing file upload and metadata analysis capabilities for forensic investigation of deepfake and synthetic media.

## Features

### File Upload Module
- **Chunked Upload Support**: Large file upload with resume capability
- **File Type Validation**: Supports images (JPEG, PNG, GIF, TIFF, BMP, WebP) and videos (MP4, AVI, MOV, WMV, FLV, MKV, WebM)
- **Hash Verification**: MD5, SHA1, SHA256 hash generation and verification
- **Progress Tracking**: Real-time upload progress monitoring
- **Storage**: MinIO object storage integration

### Metadata Analysis Module
- **EXIF Data Extraction**: Camera information, date/time, GPS coordinates
- **Video Metadata**: Duration, frame rate, codec information, bitrate
- **Hash Analysis**: Multiple hash algorithms for file integrity verification
- **Forensic Indicators**: Automatic detection of suspicious patterns
- **Async Processing**: Kafka-based message queue for background analysis

## Architecture

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

## Technology Stack

- **Framework**: Spring Boot 3.5.4
- **Database**: MySQL 8.0
- **Cache**: Redis
- **Message Queue**: Apache Kafka
- **Object Storage**: MinIO
- **Metadata Extraction**: 
  - Drew Noakes Metadata Extractor (Images)
  - JavaCV/FFmpeg (Videos)
- **File Type Detection**: Apache Tika
- **Build Tool**: Maven

## API Endpoints

### Upload Endpoints

```
POST /api/v1/upload/chunk              # Upload file chunk
GET  /api/v1/upload/progress/{fileMd5}  # Get upload progress
GET  /api/v1/upload/check/{fileMd5}     # Check if file exists
GET  /api/v1/upload/supported-types    # Get supported file types
POST /api/v1/upload/validate           # Validate file type
```

### Metadata Endpoints

```
GET /api/v1/metadata/analysis/{fileMd5}    # Get complete metadata analysis
GET /api/v1/metadata/basic/{fileMd5}       # Get basic metadata
GET /api/v1/metadata/exif/{fileMd5}        # Get EXIF data
GET /api/v1/metadata/video/{fileMd5}       # Get video metadata
GET /api/v1/metadata/suspicious/{fileMd5}  # Get suspicious indicators
GET /api/v1/metadata/hashes/{fileMd5}      # Get hash verification
```

## Setup Instructions

### Prerequisites

1. **Java 17** or higher
2. **MySQL 8.0**
3. **Redis Server**
4. **Apache Kafka**
5. **MinIO Server**

### Database Setup

```sql
CREATE DATABASE forensic_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'forensic_user'@'localhost' IDENTIFIED BY 'forensic_password';
GRANT ALL PRIVILEGES ON forensic_db.* TO 'forensic_user'@'localhost';
FLUSH PRIVILEGES;
```

### MinIO Setup

```bash
# Start MinIO server
minio server /data --console-address ":9001"

# Default credentials
# Access Key: minioadmin
# Secret Key: minioadmin
```

### Kafka Setup

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka
bin/kafka-server-start.sh config/server.properties

# Create topics
bin/kafka-topics.sh --create --topic metadata-analysis --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic file-processing --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

### Application Configuration

Update `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/forensic_db
spring.datasource.username=forensic_user
spring.datasource.password=forensic_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# MinIO
minio.endpoint=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
```

### Build and Run

```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Or run the JAR
mvn clean package
java -jar target/forensic-0.0.1-SNAPSHOT.jar
```

## Usage Examples

### Upload a File (Chunked)

```bash
# Upload first chunk
curl -X POST "http://localhost:8082/api/v1/upload/chunk" \
  -F "file=@/path/to/chunk_0" \
  -F "fileMd5=abc123def456" \
  -F "fileName=test_image.jpg" \
  -F "chunkIndex=0" \
  -F "totalChunks=3" \
  -F "totalSize=1048576" \
  -F "uploadedBy=investigator"

# Check upload progress
curl "http://localhost:8082/api/v1/upload/progress/abc123def456"
```

### Get Metadata Analysis

```bash
# Get complete analysis
curl "http://localhost:8082/api/v1/metadata/analysis/abc123def456"

# Get EXIF data only
curl "http://localhost:8082/api/v1/metadata/exif/abc123def456"

# Get suspicious indicators
curl "http://localhost:8082/api/v1/metadata/suspicious/abc123def456"
```

## Forensic Analysis Features

### Automatic Suspicious Indicator Detection

- Missing camera information in EXIF data
- Future dates in metadata
- Unusual image dimensions (common in AI-generated content)
- Missing GPS data from devices that typically include location
- Inconsistent metadata patterns

### Hash Verification

- MD5, SHA1, SHA256 hash generation
- File integrity verification
- Duplicate detection

### Metadata Extraction

#### Images
- Camera make and model
- Date and time taken
- GPS coordinates
- Image dimensions and orientation
- Compression settings
- Color space information

#### Videos
- Duration and frame rate
- Video and audio codecs
- Bitrate information
- Resolution
- Container metadata

## Security Considerations

- File type validation to prevent malicious uploads
- Hash verification for file integrity
- Size limits for uploads
- Metadata sanitization for sensitive information

## Future Enhancements

- Advanced AI-based deepfake detection
- Blockchain-based evidence chain of custody
- Advanced forensic analysis algorithms
- User authentication and authorization
- Audit logging
- Report generation (PDF/JSON)

## Troubleshooting

### Common Issues

1. **Database Connection Error**: Check MySQL service and credentials
2. **Redis Connection Error**: Ensure Redis server is running
3. **Kafka Connection Error**: Verify Kafka and Zookeeper are running
4. **MinIO Access Error**: Check MinIO server status and credentials
5. **Large File Upload Fails**: Increase `spring.servlet.multipart.max-file-size`

### Logs

Check application logs for detailed error information:
```bash
tail -f logs/application.log
```

## Contributing

1. Follow the coding standards defined in `.github/instructions/backend_Rules.instructions.md`
2. Use English comments, keep them concise
3. Follow the simplified module structure (no parent-child POM)
4. Ensure proper error handling and logging

## License

This project is part of the DeepFake Forensic Tool development.
