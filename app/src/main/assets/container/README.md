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

## 2. `proot` + `loader` + `loader32` + `libtalloc.so.2` + `libandroid-shmem.so`

**用 Termux 官方 proot（aarch64）**——它是为 Android untrusted_app 域构建、天天在用、带 `statx`，是本设备唯一验证可用的版本。

从 Termux apt 仓库取这几个 `.deb`，解包(`.deb` = ar 归档套 `data.tar.xz`)后取出对应文件放到本目录：

```
proot_*_aarch64.deb              → usr/bin/proot                → proot
                                   usr/libexec/proot/loader     → loader
                                   usr/libexec/proot/loader32   → loader32
libtalloc_*_aarch64.deb          → usr/lib/libtalloc.so.2.x.y   → libtalloc.so.2   (重命名)
libandroid-shmem_*_aarch64.deb   → usr/lib/libandroid-shmem.so  → libandroid-shmem.so
```

仓库根：`https://packages.termux.dev/apt/termux-main/pool/main/`（p/proot、libt/libtalloc、liba/libandroid-shmem）。

要点：
- proot **动态链接** `libtalloc.so.2`、`libandroid-shmem.so`（`libc.so`/`liblog.so` 走系统 `/system/lib64`），靠 `LD_LIBRARY_PATH` 找到前两者；loader **分离**，靠 `PROOT_LOADER`/`PROOT_LOADER_32` 定位。三者都已在 `LinuxContainerEngine.buildProotInvocation` 配好。
- 安装时代码会自动 `setExecutable`，无需手动 chmod。
- **不要**设 `PROOT_NO_SECCOMP=1`：Termux proot 默认 seccomp 模式即翻译 statx；强制全量 ptrace 在本设备会触发 `ptrace(PEEKDATA): I/O error`。

踩过的坑(勿重蹈)：
- ❌ `skirsten.github.io/proot-portable-android-binaries`：proot **5.1.0、无 statx** → `Cannot find module`。
- ❌ `green-green-avk/build-proot-android`：有 statx 但 loader 分离，本应用沙箱映射失败 → `ptrace(PEEKDATA): I/O error` / `Function not implemented`。
- ❌ `proot-me` 官方通用静态版(v5.3.0)：shell 域能跑,但 **untrusted_app 域里 guest `chdir` 必崩**(用户一 `cd` 就退 255)——非 Android 构建,水土不服。

换 proot 后把 `ContainerInstaller.kt` 的 `INSTALL_VERSION` +1，触发设备上重新解压复制。

## 放置后

```
app/src/main/assets/container/
├── alpine-rootfs.bin
├── proot
├── loader
├── loader32
├── libtalloc.so.2
├── libandroid-shmem.so
└── README.md   (本文件)
```

会随 APK 打包，离线可用。
