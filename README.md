# Backend (Spring Boot)

## Prerequisites
- **Java 17** 
- **Maven 3.9+** (installed locally, or use the provided `./mvnw` wrapper)

## How to Run

### 1. Build the project
Using Maven Wrapper:
```bash
./mvnw clean package
```

#### 2. Start the application

Run the JAR:
```bash
java -jar target/forensic-0.0.1-SNAPSHOT.jar
```

Or start with Maven:
```bash
./mvnw spring-boot:run
```

Currently, the application has no REST endpoints defined.
To verify the server is running, open:

http://localhost:8082


You should see the default Spring Boot error page (Whitelabel Error Page), which confirms the backend is up.