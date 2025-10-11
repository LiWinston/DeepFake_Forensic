# 后端启动（Windows 可靠方式）

避免右键“用 PowerShell 运行”带来的工作目录/执行策略/环境差异，推荐以下两种方式：

## 1) 双击批处理（最省心）
- 路径：`py\run-app.bat`
- 作用：自动调用 PowerShell 7（若有）或系统 PowerShell，设置执行策略 Bypass，并在脚本目录下执行。

## 2) 终端中运行（推荐）
在仓库根目录或 `py` 目录下执行：

```powershell
cd E:\Github\DeepFake_Forensic\py
./run-app.ps1
```

## 脚本保障
- `run-app.ps1` 会：
  - 统一切换到脚本所在目录
  - 在 `py/server` 下探测/创建 venv
  - 仅在缺少 CUDA 版 PyTorch 或 torchvision 时安装（幂等）
  - 激活 venv，打印 CUDA/Torch 状态
  - 启动 Flask 服务

## 常见问题
- 右键“用 PowerShell 运行”报错：通常是工作目录非脚本目录导致。请改用上面两种方式之一。
- 首次运行时间较长：首次会创建 venv 并安装依赖，之后会跳过安装，直接启动。
- 强制重新配置（一般无需）：删除 `py/server/.venv` 后再运行。
