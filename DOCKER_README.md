# DeepFake Forensic Docker Environment

这个项目提供了完整的 Docker 环境管理脚本，类似于 Kafka KRaft 模式的启动脚本，但专门为 DeepFake Forensic 项目设计。

## 🚀 快速开始

### 启动环境
```powershell
# 普通启动
.\start-docker.ps1

# 清理后启动
.\start-docker.ps1 -Clean

# 启动但不显示日志
.\start-docker.ps1 -Clean -NoLogs
```

### 查看状态
```powershell
# 基本状态
.\status-docker.ps1

# 查看详细健康信息
.\status-docker.ps1 -Health

# 查看状态和日志
.\status-docker.ps1 -Logs
```

### 停止环境
```powershell
# 停止容器（保留数据）
.\stop-docker.ps1

# 停止并删除容器（保留数据）
.\stop-docker.ps1 -Clean

# 停止并删除所有（包括数据，谨慎使用！）
.\stop-docker.ps1 -Volumes
```

## 📋 服务列表

| 服务 | 端口 | 用途 | 默认凭据 |
|------|------|------|----------|
| MySQL | 3306 | 主数据库 | root / lyc980820 |
| Redis | 6379 | 缓存 | 无密码 |
| Kafka | 9092 | 消息队列 | 无需认证 |
| MinIO | 9000 | 对象存储 | minioadmin / minioadmin |
| MinIO Console | 9001 | 管理界面 | minioadmin / minioadmin |

## 🔧 功能特性

### 自动化管理
- ✅ 自动检查 Docker 状态
- ✅ 智能容器清理和冲突处理
- ✅ 健康检查和服务等待
- ✅ 自动初始化 Kafka Topics 和 MinIO Buckets
- ✅ 彩色日志输出和状态显示

### 智能初始化
- ✅ 自动创建必要目录结构
- ✅ 生成 MySQL 配置文件
- ✅ 创建 Kafka Topics: `metadata-analysis`, `file-processing`
- ✅ 创建 MinIO Bucket: `forensic-media`

### 健康监控
- ✅ 实时容器状态检查
- ✅ 端口连通性测试
- ✅ 服务健康状态监控
- ✅ 日志查看和跟踪

## 📁 目录结构

启动后会自动创建以下目录：
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

## ⚙️ 配置说明

### MySQL 配置
- 数据库名: `forensic_db`
- 字符集: `utf8mb4`
- 时区: `UTC`
- 自动创建表结构

### Kafka 配置
- KRaft 模式（无需 Zookeeper）
- 3个分区，1个副本
- 自动创建必要的 Topics

### MinIO 配置
- 对象存储服务
- 自动创建 `forensic-media` bucket
- 公共读取权限

## 🐛 故障排除

### 常见问题
1. **端口冲突**: 检查是否有其他服务占用相同端口
2. **权限问题**: 确保有 Docker 管理权限
3. **磁盘空间**: 确保有足够的磁盘空间

### 日志查看
```powershell
# 查看特定服务日志
docker-compose logs -f forensic_mysql
docker-compose logs -f forensic_kafka

# 查看所有服务日志
docker-compose logs -f
```

### 重置环境
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
