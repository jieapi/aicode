# CI 自动构建说明

GitHub Actions 在打 `v*` tag 时自动构建**三个签名 Release APK** 并发布 GitHub Release（手动触发走 `Actions → Android Release → Run workflow`）。

三个产物按容器镜像 / CPU 架构拆分：

| 产物 (flavor) | ABI | 容器镜像 | 适用场景 |
|---|---|---|---|
| `universal` | arm64-v8a + x86_64 | arm + x86 两套 | 通用，体积最大 |
| `armsolo` | arm64-v8a | 仅 arm 一套 | 真机首选，体积约为 universal 的一半 |
| `x86solo` | x86_64 | 仅 x86 一套 | x86 模拟器 / Chromebook，体积约为 universal 的一半 |

## 1. 需要配置的 GitHub Secrets

进入仓库 `Settings → Secrets and variables → Actions → New repository secret`，依次添加以下 4 个：

| Secret 名称 | 取值 | 说明 |
|---|---|---|
| `AICODE_KEYSTORE_BASE64` | base64 字符串 | `app/aicode.jks` 的 base64 编码，CI 会解码还原 |
| `AICODE_KEYSTORE_PASSWORD` | 明文 | keystore 的 `storePassword` |
| `AICODE_KEY_ALIAS` | 明文 | 签名 key 的 `keyAlias` |
| `AICODE_KEY_PASSWORD` | 明文 | key 的 `keyPassword` |

> `GITHUB_TOKEN` 由 Actions 自动注入，无需手动配置。

## 2. 生成 base64 keystore（在 PowerShell 里执行）

```powershell
# 仓库根目录下
$bytes = [System.IO.File]::ReadAllBytes("app\aicode.jks")
[System.Convert]::ToBase64String($bytes) | Set-Content -NoNewline keystore.b64
# 打开 keystore.b64 复制全部内容，粘贴到 AICODE_KEYSTORE_BASE64
notepad keystore.b64
```

或者一行命令直接输出到剪贴板（PowerShell 7+）：

```powershell
[System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes("app\aicode.jks")) | Set-Clipboard
```

如果还没有 keystore，先用 keytool 生成（别名/口令自己定，再回填到对应的 3 个 Secret）：

```powershell
keytool -genkeypair -v -keystore app\aicode.jks -alias aicode -keyalg RSA -keysize 2048 -validity 10000
```

## 3. CI 中的还原流程

`.github/workflows/android-release.yml` 会：

1. 把 `AICODE_KEYSTORE_BASE64` 解码为 `app/aicode.jks`；
2. 用其余 3 个 Secret 生成 `app/keystore.properties`（`storeFile=aicode.jks`）；
3. 执行 `./gradlew assembleRelease`——一次性构建 universal/armsolo/x86solo 三个 flavor 的签名 APK；
4. 把三个 APK 重命名为 `aicode-<tag>-<flavor>.apk` 并分别校验签名；
5. 创建 GitHub Release，附带你这次 tag 的 changelog（`generate_release_notes`）并把三个 APK 作为资产上传。

`aicode.jks` 与 `keystore.properties` 都在 `.gitignore` 里，不会误传到仓库，本地签名方式不受影响。

## 4. 触发一次发布

```bash
git add app/build.gradle.kts .github/workflows/
git commit -m "ci: build per-arch flavor APKs (universal/armsolo/x86solo)"
git tag v1.0.0
git push origin main v1.0.0
```

推送后到仓库的 `Actions` 标签页查看构建进度，完成后会看到新的 Release，附带 `aicode-<tag>-universal.apk`、`aicode-<tag>-armsolo.apk`、`aicode-<tag>-x86solo.apk` 三个可下载文件。
