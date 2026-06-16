# 容器二进制（需手动放置）

本目录的文件**不在代码仓库自带**，需手动下载放入。仅支持 **arm64-v8a**。

## 1. `alpine-rootfs.bin`

Alpine Linux 的最小根文件系统。下载（aarch64）：

```
https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz
```

- 下载后**重命名为** `alpine-rootfs.bin`。
  ⚠️ 必须用 `.bin` 后缀，**不能**保留 `.tar.gz`/`.tgz`：AGP 打包时会把归档后缀的 assets 自动解压改名，
  导致运行时 `assets.open("...tar.gz")` 找不到文件。文件内容仍是 gzip，代码用 `GZIPInputStream` 解压。
- 下载前用同目录的 `.sha256` 校验完整性。
- 若换版本，请同步修改 `ContainerInstaller.kt` 中的 `INSTALL_VERSION` 常量（+1 触发设备上重装）。

## 2. `proot`

Android 专用的 PRoot 静态二进制（aarch64）：

```
https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot
```

- 文件名保持 `proot`，无扩展名。
- 安装时代码会自动 `setExecutable`，无需手动 chmod。

## 放置后

```
app/src/main/assets/container/
├── alpine-rootfs.bin
├── proot
└── README.md   (本文件)
```

两个二进制约 5MB。会随 APK 打包，离线可用。
