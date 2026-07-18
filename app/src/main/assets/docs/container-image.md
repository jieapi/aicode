# 容器镜像 (Container)

进入「设置」->「容器镜像」，管理 AI 运行所在的 Linux 容器镜像。列表单选切换当前生效的 profile。

## 1. 内置镜像
默认提供内置 Alpine 镜像，shell 路径自动选择（bash/sh），无需配置。

## 2. 自定义镜像
点击「导入自定义镜像 (tar.gz)」新建自定义 profile：
*   **名称**：镜像配置别名。
*   **shell 路径**：如 `/bin/sh`、`/bin/bash`。
*   **额外绑定**：空格分隔，如 `/sdcard:/mnt`。
*   **额外 proot 参数**：空格分隔。
*   **镜像文件**：选择 tar.gz / tgz / tar.xz / txz 格式的 rootfs 镜像文件。

自定义镜像只保证能起 shell 执行命令，不 provision、不接管镜像源——所需工具由用户自行在容器内安装。

## 3. 编辑与删除
自定义镜像可编辑上述字段、可删除（删除会一并清理其 rootfs 目录，内置 Alpine 不受影响）。
