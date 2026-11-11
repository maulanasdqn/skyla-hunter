# Native Kali + Namespace Kernel — Skyla NetHunter

Catatan kerja: cara Kali NetHunter di Redmi Note 10 Pro (`sweet`) dibuat berjalan
**native di atas kernel** dan cara kernel di-rebuild supaya namespace
(`USER_NS` / `PID_NS`) aktif.

Ditulis setelah semuanya diuji di device, bukan dari asumsi. Semua angka di
dokumen ini hasil pengukuran pada 11 Nov 2026.

---

## 1. Ringkasan hasil

| | Kernel stock | Kernel hasil rebuild |
|---|---|---|
| `CONFIG_USER_NS` (`unshare -U`) | ❌ | ✅ `max_user_namespaces=29019` |
| `CONFIG_PID_NS` (`unshare -p`) | ❌ | ✅ isolasi nyata |
| `CONFIG_IPC_NS` (`unshare -i`) | ❌ | ✅ |
| uts / net / mnt / cgroup | ✅ | ✅ |
| Mount namespace benar-benar isolasi | ❌ | ❌ (lihat §7) |
| Eksekusi binary ARM64 | native | native |

Kernel yang sekarang jalan di device:

```
Linux version 4.14.357-openela-perf-ge03e5ba22586
(root@b344fa5ca6bc) (Debian clang version 14.0.6, Debian LLD 14.0.6)
#1 SMP PREEMPT Wed Nov 11 07:45:34 UTC 2026
```

Kernel stock sebelumnya (`root@64f0e9a51a0d`, Ubuntu clang 14.0.0, 4 Nov) sudah
di-replace. Backup-nya ada di `/data/local/tmp/boot-current-BP4A.251205.006.img`.

### Bukti isolasi proses

```
# di dalam namespace
pid1=kali-native  procs=5    hostname=kali

# di host Android
pid1=init         procs=862  hostname=sweet
```

Host tidak tersentuh: jumlah proses kembali `862` setelah namespace keluar.

---

## 2. Kenapa "native" itu bisa, dan apa bedanya dengan Nix

Kali di chroot **bukan emulasi**. Binary ARM64 dieksekusi langsung oleh kernel:

- `arch = aarch64`
- `Seccomp: 0` — tidak ada filter syscall
- `CapEff: 000000ffffffffff` — full capabilities, root asli
- Tidak ada qemu, tidak ada proot, tidak ada penerjemah syscall

Ini level yang sama dengan yang dicapai Nix di Android. Yang membedakan:

| | Kali chroot (sebelum rebuild) | Nix di Android |
|---|---|---|
| Syscall | langsung ke kernel | langsung ke kernel |
| Butuh root | ya | tidak |
| User namespace | tidak tersedia | tidak tersedia (sama) |
| Alasan Nix jalan tanpa root | desain Nix (non-root store) | — |

Kesimpulan: eksekusinya sama-sama native. Yang selama ini kurang bukan
eksekusi, tapi **isolasi** — dan itu yang diperbaiki lewat rebuild kernel.

---

## 3. Kapabilitas kernel stock (sebelum)

Diukur di device sebelum rebuild:

```
CONFIG_NAMESPACES=y
CONFIG_UTS_NS=y
CONFIG_NET_NS=y
# CONFIG_USER_NS is not set      <- blocker utama
# CONFIG_PID_NS is not set       <- blocker kedua
```

Konsekuensinya:

- `unshare -U` → `Invalid argument`
- `unshare -p` → `Invalid argument`
- `unshare -i` → `Invalid argument`
- proot, bubblewrap, podman, distrobox: **tidak bisa jalan**

Mount namespace memang terdaftar "ada", tapi tidak mengisolasi (§7).

---

## 4. Rebuild kernel

### 4.1 Sumber

```
repo   : https://github.com/LineageOS/android_kernel_xiaomi_sm6150
branch : lineage-23.2
commit : e03e5ba22 "ARM64: configs: sweet: Enable ln8000 charger"
```

Commit ini **sama** dengan kernel yang berjalan di device, jadi hasil rebuild
kompatibel dengan ROM yang terpasang (LineageOS 23.2 nightly).

### 4.2 Config yang ditambahkan

Ditambahkan ke `nethunter.config` (dipakai sebagai fragmen config tambahan):

```
CONFIG_USER_NS=y
CONFIG_PID_NS=y
CONFIG_SYSVIPC=y        # wajib: IPC_NS depends on (SYSVIPC || POSIX_MQUEUE)
CONFIG_IPC_NS=y
CONFIG_NET_NS=y
CONFIG_UTS_NS=y
CONFIG_NAMESPACES=y
CONFIG_CHECKPOINT_RESTORE=y
```

Catatan penting: `CONFIG_IPC_NS` **butuh** `SYSVIPC` atau `POSIX_MQUEUE`. Defconfig
`sweet` mematikan keduanya, jadi kalau hanya `CONFIG_IPC_NS=y` yang ditulis,
Kconfig akan diam-diam membuangnya. Ini terjadi di percobaan pertama —
guard di build script menangkapnya sebelum build panjang dijalankan.

### 4.3 Build

Build dilakukan seluruhnya di dalam container (filesystem case-sensitive),
hasil hanya dikembalikan ke host. Ini mengikuti flow build NetHunter yang sudah
terbukti sebelumnya.

```bash
cd ~/Downloads/nethunter-sweet/kbuild
docker run --rm -v "$PWD":/work debian:bookworm-slim bash /work/build4.sh
```

Script: `~/Downloads/nethunter-sweet/kbuild/build4.sh` (nethunter-sweet).
Build script melakukan tiga hal sebelum build panjang:

1. `make vendor/sdmsteppe-perf_defconfig vendor/sweet.config vendor/nethunter.config`
2. **verifikasi** opsi hasil defconfig benar-benar masuk (`=y`/`=m`)
3. abort kalau ada yang hilang, baru kemudian `make -j$(nproc)`

Output:

```
kbuild/out-kernel4/Image.gz              18 MB
kbuild/out-kernel4/modules/*.ko          18 module
kbuild/out-kernel4/kernel.config         config hasil build
```

Module yang dihasilkan (driver Wi-Fi USB + IR + gspca):

```
ath.ko           ath9k_common.ko  ath9k_htc.ko    ath9k_hw.ko
carl9170.ko      eeprom_93cx6.ko  gspca_main.ko   mmc_test.ko
msm-geni-ir.ko   rt2800lib.ko     rt2800usb.ko    rt2x00lib.ko
rt2x00usb.ko     rtl_usb.ko       rtl8187.ko      rtl8192c-common.ko
rtl8192cu.ko     rtlwifi.ko
```

### 4.4 Repack boot image

Boot image punya **AVB footer** (`AVBf`, hash descriptor, algorithm `NONE`).
Kalau footer dibuang, device menolak boot. Karena itu prosesnya: unpack
reference → ganti kernel → repack → **tambahkan AVB footer lagi**.

```bash
cd ~/Downloads/nethunter-sweet
./repack.sh                       # -> boot-ns.img (128 MB, AVB valid)

avbtool add_hash_footer \
  --image boot-ns.img \
  --partition_name boot \
  --partition_size 134217728 \
  --hash_algorithm sha256 \
  --algorithm NONE
```

Yang dipertahankan dari boot image asli: ramdisk (md5 identik), dtb, cmdline,
alamat load, header v2, os version/patch level.

### 4.5 Flash

Device ini non-A/B (`/dev/block/by-name/boot` tunggal), Magisk 31.1.

```bash
# backup dulu
adb shell su -c 'dd if=/dev/block/by-name/boot of=/data/local/tmp/boot-current-$(getprop ro.build.display.id).img'

# flash
adb push boot-ns.img /data/local/tmp/
adb shell su -c 'dd if=/data/local/tmp/boot-ns.img of=/dev/block/by-name/boot bs=1M'
adb shell su -c 'sync'
```

Verifikasi MD5 partisi == image setelah tulis, baru reboot.

### 4.6 Rollback

```bash
adb push boot-current-BP4A.251205.006.img /data/local/tmp/
adb shell su -c 'dd if=/data/local/tmp/boot-current-BP4A.251205.006.img of=/dev/block/by-name/boot bs=1M'
adb shell su -c 'sync'
adb reboot
```

Atau via fastboot: `fastboot flash boot boot-current-*.img`.

---

## 5. Verifikasi setelah flash

### 5.1 Kapabilitas namespace

```
$ su -c 'for f in -u -p -U -i -n -m -C; do printf "%s=" $f; unshare $f true && echo OK; done'
-u=OK  -p=OK  -U=OK  -i=OK  -n=OK  -m=OK  -C=OK

$ cat /proc/sys/user/max_user_namespaces
29019
```

`max_user_namespaces` hanya ada kalau `CONFIG_USER_NS` di-compile — ini bukti
konfigurasi benar-benar masuk ke binary, bukan cuma tertulis di `.config`.

### 5.2 Isolasi nyata

```
$ su -c 'kali-native --isolated bash -c "echo pid1=$(cat /proc/1/comm) procs=$(ls /proc|grep -c [0-9])"'
pid1=kali-native  procs=5

$ echo pid1=$(cat /proc/1/comm) procs=$(ls /proc|grep -c [0-9])
pid1=init         procs=862
```

### 5.3 Toolchain native

```
Nmap version 7.991 ( https://nmap.org )
Python 3.14.8
OpenSSL 3.6.5 29 Sep 2026
```

Salinan konfigurasi kedua kernel disimpan di repo ini:
- `kernel-config-stock.txt` — kernel sebelum rebuild
- `kernel-config-userpidns.txt` — kernel hasil rebuild

---

## 6. Launcher: `kali-native`

Script di Magisk module `skyla-tools`
(`/data/adb/modules/skyla-tools/system/bin/kali-native`).

Script mendeteksi kapabilitas saat dijalankan, jadi satu file yang sama bekerja
di kernel stock maupun kernel hasil rebuild — tidak ada flag yang di-hardcode.

```bash
su
kali-native --check              # laporkan kapabilitas + mode yang dipakai
kali-native                      # shell Kali, hostname kali
kali-native nmap -sV 10.0.0.1    # satu perintah
kali-native --isolated bash      # PID namespace sendiri (pid1 baru, /proc 5 proses)
kali-native --net                # network stack private
```

Output `--check` di device sekarang:

```
kernel       4.14.357-openela-perf-ge03e5ba22586
user_ns      yes
pid_ns       yes
ipc_ns       yes
uts_ns       yes
net_ns       yes
seccomp      0
cap_eff      000000ffffffffff
chroot       ready
packages     3465
mode         full — user+pid namespaces, proot/podman capable
```

### Soal `/sdcard`

`/sdcard` di bind mount ke `/storage/emulated/0` dan bertahan selama sesi.
Mount yang dibuat script ini di-teardown lewat `trap` saat keluar, karena
namespace tidak bisa diandalkan untuk membersihkannya (§7).

---

## 7. Batasan yang **tidak** beres dengan rebuild kernel ini

### Mount namespace tidak mengisolasi

`/` di Android adalah mount `shared` (`shared:1`). Memanggil `mount --make-rslave`
/ `--make-rprivate` di dalam mount namespace tetap mempropagasi mount kembali ke
tabel mount Android. Diuji dengan beberapa kombinasi, semuanya bocor:

| percobaan | hasil |
|---|---|
| `unshare -m --fork` | LEAKED |
| `unshare -m` + `--make-rslave /` | LEAKED |
| `unshare -m` + `--make-rprivate /` | LEAKED (propagation jadi `private`, tetap bocor) |

Efek samping yang pernah terjadi: `mount -t proc proc /proc` di dalam PID
namespace **menimpa `/proc` host** sampai kosong, dan semua eksekusi binary gagal
(`unable to stat either "/proc/self/exe"`). Pemulihannya hanya lewat
`adb reboot` — `su`, `mount`, `ls` semuanya tidak bisa jalan karena butuh `/proc`.
Script sekarang selalu me-mount `/proc` ke **target chroot** (`$K/proc`), tidak
pernah ke `/`, untuk menghindari ini.

### Konsekuensi bubblewrap / podman

`bwrap` gagal dengan:

```
bwrap: Failed to make / slave: Invalid argument
```

Ini kegagalan **propagasi mount**, bukan kegagalan config namespace. Sudah diuji
dengan `--unshare-uts`, `--unshare-uts --unshare-ipc`,
`--unshare-uts --unshare-pid --proc /proc` — semuanya gagal di titik yang sama.

Artinya `kali-native --check` yang menulis "proot/podman capable" itu benar soal
**syarat namespace**, tapi untuk bwrap/podman secara praktis masih terhalang
propagasi mount.

### Langkah berikutnya (kalau mau)

Patch `fs/pnode.c` / `fs/namespace.c` supaya `MS_SLAVE` propagation berperilaku
benar di namespace anak, lalu build ulang dengan flow yang sama (§4.3).
Build makan waktu ~15 menit di mesin ini.

---

## 8. Ringkasan perubahan pada sistem

| Lokasi | Isi |
|---|---|
| `/data/adb/modules/skyla-tools/system/bin/` | `nh`, `dox`, `kali-native`, `kali-ok` |
| `/data/adb/service.d/nh-boot.sh` | mount chroot setelah boot, matikan doze |
| `/data/local/tmp/boot-current-BP4A.251205.006.img` | backup boot image lama |
| `/data/local/nhsystem/kali-arm64` | rootfs Kali (tidak diubah oleh kernel) |

Verifikasi runtime: `kali-ok` → `RESULT: all good` (10/10 cek).

---

## 9. Catatan lain

- Wi-Fi internal `sweet` (QCA6391) **tidak mendukung monitor mode**. Fitur
  wireless butuh adapter USB OTG (ath9k/rtl8187/rt2800 sudah jadi module).
- `/system/bin` bersifat read-only; karena itu semua script ditaruh di Magisk
  module, bukan disalin ke `/system`.
- Toybox di ROM ini **tidak punya applet `reboot`**, sehingga
  `/system/bin/reboot` gagal dengan "No such file or directory". Pakai:
  ```bash
  adb reboot
  # atau
  adb shell su -c '/system/xbin/busybox_nh reboot -f'
  ```
- Artifact build (tidak masuk repo, karena besar):
  - `~/Downloads/nethunter-sweet/boot-ns.img` (128 MB, AVB valid)
  - `~/Downloads/nethunter-sweet/kbuild/out-kernel4/` (Image.gz + 18 module)
