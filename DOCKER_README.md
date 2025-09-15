# DeepFake Forensic Docker Environment

This project provides complete Docker environment management scripts, similar to Kafka KRaft mode startup scripts, but specifically designed for the DeepFake Forensic project.

## 🚀 Quick Start

### Start Environment
```powershell
# Normal Start
.\start-docker.ps1

# Start After Cleanup
.\start-docker.ps1 -Clean

# Start Without Showing Logs
.\start-docker.ps1 -Clean -NoLogs
```

### Check Status
```powershell
# Basic Status
.\status-docker.ps1

# View Detailed Health Information
.\status-docker.ps1 -Health

# View Status and Logs
.\status-docker.ps1 -Logs
```

### Stop Environment
```powershell
# Stop Containers (Keep Data)
.\stop-docker.ps1

# Stop and Remove Containers (Keep Data)
.\stop-docker.ps1 -Clean

# Stop and Remove All (Including Data, Use with Caution!)
.\stop-docker.ps1 -Volumes
```

## 📋 Service List

| Service | Port | Purpose | Default Credentials |
|------|------|------|----------|
| MySQL | 3306 | Main Database | root / lyc980820 |
| Redis | 6379 | Cache | No Password |
| Kafka | 9092 | Message Queue | No Authentication Required |
| MinIO | 9000 | Object Storage | minioadmin / minioadmin |
| MinIO Console | 9001 | Management Interface | minioadmin / minioadmin |

## 🔧 Features

### Automated Management
- ✅ Automatically check Docker status
- ✅ Smart container cleanup and conflict handling
- ✅ Health checks and service waiting
- ✅ Auto-initialize Kafka Topics and MinIO Buckets
- ✅ Colorful log output and status display

### Smart Initialization
- ✅ Automatically create necessary directory structure
- ✅ Generate MySQL configuration files
- ✅ Create Kafka Topics: `metadata-analysis`, `file-processing`
- ✅ Create MinIO Bucket: `forensic-media`

### Health Monitoring
- ✅ Real-time container status checking
- ✅ Port connectivity testing
- ✅ Service health status monitoring
- ✅ Log viewing and tracking

## 📁 Directory Structure

The following directories will be automatically created after startup:
```
DeepFake_Forensic/
├── docker/
│   └── mysql/
│       ├── conf/          # MySQL 配置文件
│       └── init/          # 数据库初始化脚本
├── logs/                  # 应用日志目录
├── uploads/               # 文件上传目录
├── docker-compose.yml     # Docker Compose 配置
├── start-docker.ps1       # 启动脚本
├── stop-docker.ps1        # 停止脚本
└── status-docker.ps1      # 状态检查脚本
```

## ⚙️ Configuration

### MySQL Configuration
- Database Name: `forensic_db`
- Character Set: `utf8mb4`
- Timezone: `UTC`
- Auto-create table structure

### Kafka Configuration
- KRaft mode (no Zookeeper required)
- 3 partitions, 1 replica
- Auto-create necessary Topics

### MinIO Configuration
- Object storage service
- Auto-create `forensic-media` bucket
- Public read permissions

## 🐛 Troubleshooting

### Common Issues
1. **Port Conflicts**: Check if other services are using the same ports
2. **Permission Issues**: Ensure you have Docker management permissions
3. **Disk Space**: Ensure sufficient disk space is available

### Log Viewing
```powershell
# 查看特定服务日志
docker-compose logs -f forensic_mysql
docker-compose logs -f forensic_kafka

# 查看所有服务日志
docker-compose logs -f
```

### Reset Environment
```powershell
# 完全重置（删除所有数据）
.\stop-docker.ps1 -Volumes
.\start-docker.ps1 -Clean
```

## 🔗 相关链接

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [MinIO Documentation](https://docs.min.io/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

## 📞 支持

如果遇到问题，请：
1. 先运行 `.\status-docker.ps1 -Health` 检查状态
2. 查看相关服务日志
3. 检查 Docker Desktop 是否正常运行

---

**注意**: 这些脚本会自动管理 Docker 容器生命周期，确保在生产环境中谨慎使用 `-Volumes` 选项。
