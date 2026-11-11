package dev.reforge.ui

/** A one-input tool: {t} in the command is replaced by the target. */
data class QuickTool(
    val name: String,
    val command: String,
    val targetHint: String,
)

data class ToolCategory(val id: String, val title: String, val tools: List<QuickTool>)

/** Catalog powering the category runner screens. */
val toolCategories = listOf(
    ToolCategory(
        "recon", "Recon / Scan",
        listOf(
            QuickTool("nmap — service/version", "nmap -sV -T4 {t}", "host or IP"),
            QuickTool("nmap — top 1000", "nmap -sV -T4 --top-ports 1000 {t}", "host or IP"),
            QuickTool("nmap — vuln scripts", "nmap -sV --script vuln {t}", "host or IP"),
            QuickTool("rustscan", "rustscan -a {t} --ulimit 5000 --no-config -- -sV", "host or IP"),
            QuickTool("whatweb", "whatweb {t}", "url or host"),
            QuickTool("wafw00f", "wafw00f {t}", "url"),
        ),
    ),
    ToolCategory(
        "web", "Web",
        listOf(
            QuickTool("nikto", "nikto -h {t} -maxtime 120s", "url or host"),
            QuickTool("ffuf — dirs", "ffuf -u http://{t}/FUZZ -w /usr/share/wordlists/dirb/common.txt -mc 200,204,301,302,307,401,403 -s", "host"),
            QuickTool("feroxbuster", "feroxbuster -u http://{t} -w /usr/share/wordlists/dirb/common.txt -q -n", "host"),
            QuickTool("sqlmap", "sqlmap -u {t} --batch --crawl=1 --level=1", "url with params"),
            QuickTool("wpscan", "wpscan --url {t} --no-banner --format cli-no-color", "url"),
        ),
    ),
    ToolCategory(
        "osint", "OSINT — People",
        listOf(
            QuickTool(
                "Email → accounts",
                "holehe {t}",
                "email",
            ),
            QuickTool(
                "Username → 3000+ sites",
                "maigret {t} --timeout 10 --no-progressbar --no-color",
                "username",
            ),
            QuickTool(
                "Username → sites (fast)",
                "sherlock {t} --timeout 10 --print-found --no-color",
                "username",
            ),
            QuickTool(
                "Phone number",
                "phoneinfoga scan -n {t}",
                "+6281234567890",
            ),
            QuickTool(
                "Photo metadata (GPS)",
                "exiftool {t}",
                "/sdcard/DCIM/Camera/photo.jpg",
            ),
            QuickTool(
                "Social search links",
                "echo 'facebook:'; echo 'https://www.facebook.com/search/top?q={t}'; echo 'instagram:'; echo 'https://www.instagram.com/{t}'; echo 'github:'; echo 'https://github.com/{t}'",
                "name or username",
            ),
        ),
    ),
    ToolCategory(
        "osintdom", "OSINT — Domain",
        listOf(
            QuickTool("theHarvester", "theHarvester -d {t} -b duckduckgo,bing,crtsh,otx", "domain"),
            QuickTool("subfinder", "subfinder -d {t} -silent", "domain"),
            QuickTool("dnsrecon", "dnsrecon -d {t}", "domain"),
            QuickTool("whois", "whois {t}", "domain or IP"),
            QuickTool("dig — all records", "dig {t} ANY +noall +answer", "domain"),
            QuickTool("IP → geo/whois", "whois {t} | grep -iE 'orgname|country|netname|descr|inetnum' | head -8", "IP"),
        ),
    ),
    ToolCategory(
        "exploits", "Exploits",
        listOf(
            QuickTool("searchsploit", "searchsploit {t}", "product / keyword"),
            QuickTool("searchsploit — exact title", "searchsploit -t {t}", "title term"),
            QuickTool("searchsploit — CVE", "searchsploit --cve {t}", "CVE id"),
            QuickTool("searchsploit — nmap XML", "searchsploit --nmap {t}", "path to nmap -oX file"),
        ),
    ),
)

fun categoryById(id: String): ToolCategory? = toolCategories.firstOrNull { it.id == id }
