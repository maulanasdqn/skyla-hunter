# Rencana Patch `fs/pnode.c` — mount namespace untuk bwrap/podman

Status: **rencana**, belum dikerjakan.

Dokumen ini mencatat investigasi sebelum build ulang, **termasuk temuan bahwa
patch `fs/pnode.c` kemungkinan besar bukan yang dibutuhkan.**

Ditulis 11 Nov 2026, setelah kernel namespace (`KERNEL-NATIVE.md`) terpasang dan
berjalan di device.

---

## 1. Ringkasan

Alasan awal mencurigai `fs/pnode.c`: `bwrap` gagal dengan

```
bwrap: Failed to make / slave: Invalid argument
```

Setelah ditelusuri, **`fs/pnode.c` tidak pernah terbukti salah**. Rantai penyebab
sebenarnya, semuanya terukur di device:

| # | Penyebab | Bukti |
|---|---|---|
| 1 | `mount` **toybox** berhenti di `/etc/fstab` sebelum sampai ke syscall | `mount: bad /etc/fstab: No such file or directory` |
| 2 | Di dalam chroot, `bash` memanggil `/system/bin/unshare` (binari Android) | `cannot execute: required file not found` |
| 3 | `unshare` **util-linux 2.42.4** mengembalikan `EPERM` untuk `CLONE_NEWUSER` di kernel 4.14 ini | `unshare: unshare failed: Operation not permitted` |
| 4 | User namespace tanpa map root mencabut `CAP_SYS_ADMIN` | `CapEff: 0000000000000000` |

Konsekuensinya, hasil pengukuran bergantung pada **tool mana yang dipakai**, bukan
pada kode kernel:

| tool | hasil |
|---|---|
| `mount` toybox `--make-rprivate` | gagal di fstab, mount tidak pernah terjadi |
| `busybox mount --make-rprivate` | **berhasil** |
| `unshare` toybox `-U -r -m` + busybox mount rprivate | **ISOLATED**, `CapEff=ffffffffff` |
| `unshare` util-linux `-U` / `-m` | `EPERM` / `EINVAL` |
| `bwrap` | gagal (memakai jalur util-linux) |

**Isolasi mount sudah terbukti bisa**, tanpa menyentuh `fs/pnode.c`:

```
A: tanpa ubah propagation         -> LEAKED
B: busybox mount --make-rprivate / -> ISOLATED
C: busybox mount --make-rslave /   -> ISOLATED
D: unshare -U -r -m + rprivate     -> ISOLATED, uid=0, CapEff=ffffffffff
```

Catatan: D memakai **toybox** `unshare`. `unshare` util-linux 2.42.4 mengembalikan
`EPERM` di kernel ini (§3.3), dan itulah jalur yang dipakai bwrap — sehingga
bwrap belum bisa dipakai sebelum masalah util-linux ini diselesaikan.

---

## 2. Kode kernel yang dianalisis

Kalau patch tetap diperlukan, ini titik yang sudah ditelusuri.

### 2.1 `fs/pnode.c: do_make_slave()`

```c
} else {
    struct mount *m;
    for (m = master = next_peer(mnt); m != mnt; m = next_peer(m)) {
        if (m->mnt.mnt_root == mnt->mnt.mnt_root) {
            master = m;
            break;
        }
    }
    list_del_init(&mnt->mnt_share);
    mnt->mnt_group_id = 0;
    CLEAR_MNT_SHARED(mnt);
}
list_for_each_entry(slave_mnt, &mnt->mnt_slave_list, mnt_slave)
    slave_mnt->mnt_master = master;
list_move(&mnt->mnt_slave, &master->mnt_slave_list);
```

Dugaan awal (terbantah): bila `next_peer()` kembali ke `mnt` sendiri, `master`
tetap `mnt` dan `list_move()` memindahkan list ke dirinya sendiri.

Kode ini **identik dengan upstream 4.14** dan tidak pernah terpicu dalam
pengujian. `busybox mount --make-rprivate` dan `--make-rslave` berhasil
melewatinya tanpa masalah.

### 2.2 `fs/namespace.c: copy_mnt_ns()`

```c
copy_flags = CL_COPY_UNBINDABLE | CL_EXPIRE;
if (user_ns != ns->user_ns)
    copy_flags |= CL_SHARED_TO_SLAVE | CL_UNPRIVILEGED;
```

Jalur ini relevan: saat masuk user namespace baru, root mount di-clone sebagai
slave + `CL_UNPRIVILEGED`, yang men-set `MNT_LOCKED` pada sub-tree (line ~1142).
Inilah sebabnya proses tanpa map root tidak bisa mengubah propagation.

### 2.3 `fs/namespace.c: may_mount()`

```c
static inline bool may_mount(void)
{
    return ns_capable(current->nsproxy->mnt_ns->user_ns, CAP_SYS_ADMIN);
}
```

Sumber `EPERM` pada percobaan di §3.2. Bukan bug — `mnt_ns->user_ns` belum
memetakan caller sebagai root.

---

## 3. Eksperimen yang sudah dijalankan

### 3.1 Propagation di dalam mount namespace

```
A: tanpa ubah propagation         -> LEAKED
B: busybox mount --make-rprivate -> ISOLATED
C: busybox mount --make-rslave   -> ISOLATED
D: busybox mount tanpa propagate -> LEAKED
```

Isolasi berfungsi asalkan propagation root diubah lebih dulu. Ini yang selama
ini gagal karena `mount` toybox tidak pernah sampai ke syscall (berhenti di
fstab).

### 3.2 Pengaruh user namespace

```
-U -m   (userns + mntns)          -> EPERM   (CapEff=0)
-U -r -m (map root, lalu mntns)   -> rc=0, CapEff=ffffffffff, ISOLATED
-m      (mntns saja)              -> rc=0
```

User ns tanpa map root mencabut `CAP_SYS_ADMIN`. Dengan `-r` semua berjalan.

### 3.3 Perilaku `unshare` dari berbagai sumber

```
toybox unshare -U            -> rc=0
util-linux unshare -U        -> EPERM  (di dalam maupun di luar chroot)
util-linux unshare -r        -> EPERM
util-linux unshare -m        -> EINVAL ("cannot change root filesystem propagation")
```

Selisih ini yang membuat hasil pengujian sebelumnya tidak konsisten: launcher
memakai toybox, bwrap memakai jalur util-linux.

`unshare` util-linux 2.42.4 gagal `EPERM` bahkan saat dipanggil dari luar
chroot dengan PATH Kali murni, jadi kegagalan ini bukan soal PATH dan bukan soal
chroot — melainkan cara util-linux memakai `CLONE_NEWUSER` (kemungkinan
kombinasi `setgroups`/`gid_map`) yang tidak diterima kernel 4.14.

**Konsekuensi penting:** bwrap ditulis untuk jalur util-linux, sehingga
kemungkinan besar tidak akan berjalan di kernel 4.14 ini tanpa penyesuaian.
Toybox `unshare` tetap jadi jalur yang terbukti.

### 3.4 bwrap

| percobaan | hasil |
|---|---|
| di dalam `kali-native` (sudah di user ns) | `Failed to make / slave: Invalid argument` |
| chroot, `--unshare-user` | `No permissions to create a new namespace` |
| chroot, tanpa `--unshare-user` | `Failed to make / slave: Invalid argument` |
| chroot, PATH bocor ke `/system/bin` | `cannot execute: required file not found` |

Tidak ada satu pun yang berakhir di `do_make_slave()` dengan kegagalan logika.

---

## 4. Rencana perbaikan — tanpa rebuild kernel

Semua perubahan pada launcher.

### Langkah 1 — jangan pakai `mount` toybox untuk propagation

Toybox `mount` selalu membaca `/etc/fstab` dan gagal total bila file tidak ada,
termasuk untuk operasi `--make-rprivate`. Di root Magisk, `/etc` read-only dan
file itu tidak ada.

Pilihan:
- gunakan `/system/xbin/busybox_nh mount` (terbukti berhasil), atau
- sediakan `/etc/fstab` kosong lewat Magisk module overlay.

Verifikasi: `mount --make-rprivate /` harus `rc=0`.

### Langkah 2 — jangan biarkan PATH chroot menunjuk `/system/bin`

Binari Android (`/system/bin/unshare`, `mount`, `readlink`) tidak bisa dieksekusi
di dalam chroot karena linker-nya berbeda:

```
/bin/bash: /system/bin/unshare: cannot execute: required file not found
```

Langkah ini masih perlu, tetapi **tidak cukup** — lihat §3.3: `unshare`
util-linux tetap gagal `EPERM` walau PATH sudah bersih.

Launcher harus memakai `env -i` dengan PATH Kali murni
(`/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin`), tanpa `/system/bin`, agar
`unshare`/`mount` Android tidak ikut terpanggil:

```
/bin/bash: /system/bin/unshare: cannot execute: required file not found
```

Setelah itu, pakai **toybox `unshare`** untuk membuat namespace, bukan
util-linux.

### Langkah 3 — buat mount ns di dalam user ns yang memetakan root

Urutan yang terbukti berhasil:

```sh
unshare -U -r -m --fork sh -c '
  /system/xbin/busybox_nh mount --make-rprivate /
  <mounts>
'
```

`-r` wajib. Tanpa itu `CapEff=0` dan semua `mount` gagal `EPERM`.

### Langkah 4 — ganti `trap cleanup` dengan mount namespace sungguhan

Karena isolasi terbukti bisa, `kali-native` tidak perlu lagi membersihkan mount
secara manual:

```
kali-native --isolated  ->  unshare -U -r -m -p -u --fork ...
                              busybox mount --make-rprivate /
                              <setup chroot mounts>
```

Dengan begitu mounts hilang otomatis saat proses keluar, dan insiden `/proc`
host tertimpa (`KERNEL-NATIVE.md` §7) tidak bisa terulang.

### Langkah 5 — bwrap

Setelah langkah 1–4, uji dari launcher:

```bash
bwrap --ro-bind /usr /usr --ro-bind /lib /lib --ro-bind /bin /bin \
      --ro-bind /etc /etc --proc /proc --dev /dev \
      --unshare-user --unshare-pid --unshare-uts --hostname sandbox \
      bash -c 'echo ok'
```

Kalau masih gagal, tangani sesuai error:

| error | tindakan |
|---|---|
| `No permissions to create a new namespace` | jangan nesting user ns — jalankan bwrap **tanpa** outer `-U`, atau beri `--unshare-user` dengan uid 0 terpetakan |
| `Failed to make / slave` | pastikan langkah 3 benar (busybox mount + `-r`) |
| `overflowuid` tidak ada | mount `/proc` penuh di chroot, bukan subset |
| `required file not found` | PATH chroot masih bocor ke `/system/bin` |

Alternatif yang lebih mudah: pakai `proot` di dalam namespace kernel yang sudah
ada, karena proot tidak membuat namespace sendiri.

### Langkah 6 — podman/distrobox

`CONFIG_OVERLAY_FS=y` sudah aktif di kernel hasil rebuild, jadi overlay native
bisa dipakai. Uji:

```bash
podman --storage-driver=vfs run --rm alpine echo ok
podman --storage-driver=overlay run --rm alpine echo ok
```

---

## 5. Kapan patch `fs/pnode.c` benar-benar diperlukan

Hanya kalau langkah 1–5 selesai **dan** masih ada kegagalan yang jejaknya
berakhir di `do_make_slave()` / `propagate_mnt()`.

Cara memastikan: reproduksi tanpa bwrap, memakai `busybox mount` langsung
(§3.1). Kalau `busybox mount --make-rprivate` berhasil tetapi bwrap gagal,
masalahnya di bwrap, bukan kernel — dan patch kernel tidak akan menolong.

Kalau ternyata kernel, kandidat patch defensif:

```c
    /* defensive: never slave a mount to itself */
    if (master == mnt) {
        list_del_init(&mnt->mnt_share);
        mnt->mnt_group_id = 0;
        CLEAR_MNT_SHARED(mnt);
        master = NULL;
    }
```

**Belum dipakai**, karena belum terbukti terpicu. Jangan menambah patch tanpa
reproduksi yang gagal tanpanya.

---

## 6. Perintah build & flash (kalau patch dikerjakan)

Flow yang sama dengan `KERNEL-NATIVE.md` §4.3:

```bash
cd ~/Downloads/nethunter-sweet/kbuild

git -C android_kernel_xiaomi_sm6150 status       # harus bersih dulu
# terapkan patch ke fs/pnode.c

docker run --rm -v "$PWD":/work debian:bookworm-slim bash /work/build4.sh

cd ~/Downloads/nethunter-sweet
./repack.sh
avbtool add_hash_footer --image boot-ns.img --partition_name boot \
  --partition_size 134217728 --hash_algorithm sha256 --algorithm NONE

adb push boot-ns.img /data/local/tmp/
adb shell su -c 'dd if=/data/local/tmp/boot-ns.img of=/dev/block/by-name/boot bs=1M'
adb shell su -c 'sync'
adb reboot
```

Rollback: `boot-current-BP4A.251205.006.img` (`KERNEL-NATIVE.md` §4.6).

---

## 7. Kesimpulan

`fs/pnode.c` **tidak perlu di-patch** untuk mencapai isolasi mount di device ini.

Empat penyebab sebenarnya semuanya di luar `fs/pnode.c`:

1. `mount` toybox mati di `/etc/fstab` sebelum sampai ke syscall
2. PATH chroot bocor ke `/system/bin` sehingga `unshare` Android ikut dipanggil
3. `unshare` util-linux 2.42.4 mengembalikan `EPERM` untuk `CLONE_NEWUSER` di
   kernel 4.14 ini, sementara toybox berhasil
4. user namespace tanpa map root mencabut `CAP_SYS_ADMIN`

Isolasi mount sudah terbukti berfungsi lewat jalur toybox + busybox mount.
Patch kernel hanya perlu dipertimbangkan bila ada bukti baru yang menunjuk ke
`do_make_slave()`.

**Status bwrap:** belum bisa dipakai, karena bwrap memakai jalur util-linux
yang gagal di (3). Bukan karena `fs/pnode.c`. Kalau bwrap dibutuhkan, opsi
realistisnya: pakai versi bwrap yang lebih lama, atau pakai `proot` (tidak
membuat namespace sendiri) di dalam namespace toybox yang sudah berjalan.

---

## 8. UPDATE (11 Nov 2026) — bwrap akhirnya berjalan

Ditulis setelah menguji proot, bwrap, dan podman di device. **Tetap tanpa patch
`fs/pnode.c`.**

- **proot** — ❌ tidak bisa. Guest selalu `exited with status 182` (ptrace/exec
  proot 5.4.0 tak kompatibel dengan kernel 4.14 OpenELA ini); bahkan `bash`/
  `true` tidak jalan. Saran lama memakai proot dengan demikian gugur.

- **bwrap** — ✅ **berjalan**, butuh dua perbaikan yang saling melengkapi:
  1. **`kali-native` masuk via `pivot_root`, bukan `chroot`.** Rootfs Kali hanya
     subtree direktori di partisi f2fs `/data`, bukan mount kelas-satu; `/` di
     dalam chroot karena itu bukan mount, dan `pivot_root` (yang diwajibkan
     bwrap) gagal `EINVAL`. `kali-native` kini `--rbind` rootfs ke dirinya
     sendiri lalu `pivot_root` ke sana, sehingga `/` jadi mount sejati. Ada
     fallback ke `chroot` kalau pivot gagal.
  2. **bwrap di-patch EINVAL-tolerant** pada dua panggilan propagasi (`make /
     slave` dan `make old root rprivate`) — kernel menolak perubahan propagasi
     pada mount `/data`-backed (hanya tmpfs yang menerima). Aman karena
     `kali-native` sudah membungkus dalam mount-ns private.

  Terverifikasi: `uid=0`, `hostname=sandbox`, `pid1=2` dengan `/proc` terisolasi
  sendiri, tanpa bocor ke host. Detail + build script: `magisk/bwrap/`.

- **podman** — belum. Butuh runtime (crun/runc) + cgroup delegation di atas
  bwrap; dukungan cgroup v2 di kernel 4.14 ini belum diverifikasi.

Catatan: temuan ini juga mengoreksi dugaan di §1 bahwa penyebabnya `do_make_slave`
— yang gagal justru `pivot_root` (butuh mount sejati) dan perubahan propagasi
pada mount `/data`, keduanya di luar `fs/pnode.c`.
