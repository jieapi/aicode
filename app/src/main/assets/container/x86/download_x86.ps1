$ErrorActionPreference = 'Stop'
$outDir = "C:\Users\XiaoJie\Desktop\app\myapp\aicode\app\src\main\assets\container\x86"
Set-Location $outDir

Write-Host "Downloading Alpine rootfs..."
Invoke-WebRequest -Uri "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.3-x86_64.tar.gz" -OutFile "alpine-rootfs.bin"

Write-Host "Downloading proot..."
Invoke-WebRequest -Uri "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.81_x86_64.deb" -OutFile "proot.deb"
tar.exe -xf proot.deb data.tar.xz
mkdir temp_proot | Out-Null
tar.exe -xf data.tar.xz -C temp_proot
Get-ChildItem -Path temp_proot -Recurse -Filter "proot" | Select-Object -First 1 | Move-Item -Destination "." -Force
Get-ChildItem -Path temp_proot -Recurse -Filter "loader" | Select-Object -First 1 | Move-Item -Destination "." -Force
Get-ChildItem -Path temp_proot -Recurse -Filter "loader32" | Select-Object -First 1 | Move-Item -Destination "." -Force

Write-Host "Downloading libtalloc..."
Invoke-WebRequest -Uri "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_x86_64.deb" -OutFile "libtalloc.deb"
tar.exe -xf libtalloc.deb data.tar.xz
mkdir temp_talloc | Out-Null
tar.exe -xf data.tar.xz -C temp_talloc
Get-ChildItem -Path temp_talloc -Recurse -Filter "libtalloc.so.*" | Select-Object -First 1 | Move-Item -Destination ".\libtalloc.so.2" -Force

Write-Host "Downloading libandroid-shmem..."
Invoke-WebRequest -Uri "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_x86_64.deb" -OutFile "libshmem.deb"
tar.exe -xf libshmem.deb data.tar.xz
mkdir temp_shmem | Out-Null
tar.exe -xf data.tar.xz -C temp_shmem
Get-ChildItem -Path temp_shmem -Recurse -Filter "libandroid-shmem.so" | Select-Object -First 1 | Move-Item -Destination "." -Force

Write-Host "Cleaning up..."
Remove-Item -Path "*.deb", "data.tar.xz" -Force
Remove-Item -Path "temp_*" -Recurse -Force
Write-Host "Done!"
