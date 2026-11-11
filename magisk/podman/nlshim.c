/* nlshim.c — neutralise setsockopt(NETLINK_GET_STRICT_CHK) on pre-5.2 kernels.
 *
 * netavark opens its route-netlink socket and unconditionally enables
 * NETLINK_GET_STRICT_CHK (socket option 12). That option only exists since
 * Linux 5.2; on the 4.14 NetHunter kernel the setsockopt returns ENOPROTOOPT
 * and netavark aborts immediately with:
 *
 *     netavark: host netlink socket: IO error: Protocol not available (os error 92)
 *
 * The option merely asks the kernel for stricter validation of dump requests.
 * Treating its absence as success lets netavark proceed with the kernel's older
 * (lenient) validation, which is functionally fine. We call the real setsockopt
 * first and only swallow this one specific ENOPROTOOPT, so every other option
 * still behaves exactly as the kernel dictates.
 *
 * Preload it ahead of netavark (or anything else that trips over the option):
 *     LD_PRELOAD=/opt/skyla/nlshim.so netavark ...
 *
 * Build:  cc -shared -fPIC -O2 -o nlshim.so nlshim.c -ldl
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <sys/socket.h>
#include <errno.h>

#ifndef SOL_NETLINK
#define SOL_NETLINK 270
#endif
#ifndef NETLINK_GET_STRICT_CHK
#define NETLINK_GET_STRICT_CHK 12
#endif

static int (*real_setsockopt)(int, int, int, const void *, socklen_t);

int setsockopt(int fd, int level, int opt, const void *val, socklen_t len)
{
	if (!real_setsockopt)
		real_setsockopt = dlsym(RTLD_NEXT, "setsockopt");
	int r = real_setsockopt(fd, level, opt, val, len);
	if (r == -1 && level == SOL_NETLINK &&
	    opt == NETLINK_GET_STRICT_CHK && errno == ENOPROTOOPT)
		return 0; /* pre-5.2 kernel: proceed with lenient dump checks */
	return r;
}
