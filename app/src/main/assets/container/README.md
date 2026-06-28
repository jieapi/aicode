# 容器二进制（需手动放置）

本目录的文件**不在代码仓库自带**，需手动下载放入。支持 **arm64-v8a** (arm) 和 **x86_64** (x86)。

## 1. `alpine-rootfs.bin`

Alpine Linux 的最小根文件系统。下载对应架构的版本：

- **ARM64**:
  ```
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.3-aarch64.tar.gz
  ```
- **x86_64**:
  ```
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.3-x86_64.tar.gz
  ```

下载后**重命名为** `alpine-rootfs.bin`，并放入对应的 `arm` 或 `x86` 目录中。
⚠️ 必须用 `.bin` 后缀，**不能**保留 `.tar.gz`/`.tgz`：AGP 打包时会把归档后缀的 assets 自动解压改名，导致找不到文件。文件内容仍是 gzip，代码用 `GZIPInputStream` 解压。

## 2. `proot` + `loader` + `loader32` + `libtalloc.so.2` + `libandroid-shmem.so`

**必须使用 Termux 官方 proot**——它是为 Android untrusted_app 域构建、带 `statx`，是本设备唯一验证可用的版本。

从 Termux apt 仓库取这几个 `.deb`，解包(`.deb` = ar 归档套 `data.tar.xz`)后取出对应文件放到本目录对应的架构文件夹(`arm/` 或 `x86/`)：

架构标识替换说明：如果是 ARM，下载 `*_aarch64.deb`；如果是 x86，下载 `*_x86_64.deb`。

```
proot_*_<arch>.deb              → usr/bin/proot                → proot
                                   usr/libexec/proot/loader     → loader
                                   usr/libexec/proot/loader32   → loader32
libtalloc_*_<arch>.deb          → usr/lib/libtalloc.so.2.x.y   → libtalloc.so.2   (重命名)
libandroid-shmem_*_<arch>.deb   → usr/lib/libandroid-shmem.so  → libandroid-shmem.so
```

仓库根：`https://packages.termux.dev/apt/termux-main/pool/main/`（p/proot、libt/libtalloc、liba/libandroid-shmem）。

## 放置后目录结构

```
app/src/main/assets/container/
├── arm/
│   ├── alpine-rootfs.bin
│   ├── proot
│   ├── loader
│   ├── loader32
│   ├── libtalloc.so.2
│   └── libandroid-shmem.so
├── x86/
│   ├── alpine-rootfs.bin
│   ├── proot
│   ├── loader
│   ├── loader32
│   ├── libtalloc.so.2
│   └── libandroid-shmem.so
└── README.md   (本文件)
```
