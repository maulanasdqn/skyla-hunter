package dev.reforge.core

data class KaliService(
    val id: String,
    val name: String,
    val detail: String,
    val proc: String,
    val startCmd: String,
    val stopCmd: String,
)

object KaliServices {
    val all = listOf(
        KaliService("ssh", "SSH", "Remote shell (openssh-server)", "sshd",
            "service ssh start", "service ssh stop"),
        KaliService("apache2", "Apache", "Web server :80", "apache2",
            "service apache2 start", "service apache2 stop"),
        KaliService("postgresql", "PostgreSQL", "Metasploit database", "postgres",
            "service postgresql start", "service postgresql stop"),
        KaliService("mariadb", "MariaDB", "MySQL-compatible DB", "mariadbd",
            "service mariadb start", "service mariadb stop"),
    )

    fun isRunning(s: KaliService): Boolean =
        RootShell.chroot("pgrep -x ${s.proc} >/dev/null 2>&1 && echo up").trim() == "up"

    fun toggle(s: KaliService): Boolean {
        if (isRunning(s)) {
            RootShell.chroot(s.stopCmd)
            return false
        }
        RootShell.chroot(s.startCmd)
        return isRunning(s)
    }
}
