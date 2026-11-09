--[[
  generador_V33.lua
  Mejoras finales:
  - clearenv() agregado para borrar variables de entorno.
  - Sin fork/setsid (para que Java lea el stdout).
--]]

local INPUT_FILE      = (arg and arg[1]) or "CODIGOS.txt"
local OUTPUT_FILE     = (arg and arg[2]) or "patcher_gen.c"
local PAQUETE_DEFAULT = "com.dts.freefireth"

local ADMIN_PASSWORD = "122830"
local ADMIN_XOR_KEY  = {
    0xA7, 0x3F, 0x91, 0xC4, 0x12, 0x88, 0x5E, 0xD3,
    0x6B, 0x24, 0xFA, 0x07, 0x3D, 0x9C, 0x41, 0xB8,
    0x55, 0xE2, 0x0A, 0x76, 0xCD, 0x19, 0x8F, 0x30,
    0xB1, 0x4E, 0x6D, 0xF7, 0x02, 0x5B, 0xA0, 0x3C
}

local GAS_URL = "https://script.google.com/macros/s/AKfycbwhiE_R667pWxTL_ITqzwuzKT9yndEcL0r_Tj_C1qWeboYkdKRTUeOg9ZlnNEN4zGQIAw/exec"
local URL_XOR_KEY = {
    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
    0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00,
    0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F, 0x7A, 0x8B,
    0x9C, 0xAD, 0xBE, 0xCF, 0xD0, 0xE1, 0xF2, 0x03
}

local PKG_XOR_KEY = {
    0x4B, 0x9D, 0x27, 0xE3, 0x6A, 0xB1, 0x58, 0x0F,
    0xC4, 0x73, 0x8E, 0x15, 0xD2, 0x49, 0xA6, 0x3C
}

local MAGIC_SEED  = { 0x4E, 0x52, 0xA1, 0x08 }
local PREFIX_SEED = { 0x7C, 0x3A }

local function trim(s) return s:match("^%s*(.-)%s*$") end
local function parse_hex(s)
    s = trim(s)
    if s == "" then error("cadena hexadecimal vacía", 2) end
    if s:find("[^%x%s]") then error("carácter no hexadecimal en: " .. s, 2) end
    local compact = s:gsub("%s+", "")
    if #compact == 0 or (#compact % 2) ~= 0 then error("cantidad impar de dígitos hexadecimales en: " .. s, 2) end
    local bytes = {}
    for i = 1, #compact, 2 do bytes[#bytes + 1] = tonumber(compact:sub(i, i + 1), 16) end
    return bytes
end
local function to_c(bytes)
    local t = {}
    for _, b in ipairs(bytes) do t[#t+1] = ("0x%02x"):format(b) end
    return "{" .. table.concat(t, ",") .. "}"
end

local function cifrar_cadena(s, key)
    local enc = {}
    for i = 1, #s do
        local pos_mask = (i * 0x9E) & 0xFF
        enc[i] = string.byte(s, i) ~ key[((i - 1) % #key) + 1] ~ pos_mask
    end
    return enc
end

local function to_arr(bytes, name)
    local parts = {}
    for i, b in ipairs(bytes) do parts[i] = ("0x%02x"):format(b) end
    return ("static const uint8_t %s[] = {%s};"):format(name, table.concat(parts, ","))
end

local function generar_arrays_admin()
    local enc = cifrar_cadena(ADMIN_PASSWORD, ADMIN_XOR_KEY)
    local url_enc = cifrar_cadena(GAS_URL, URL_XOR_KEY)
    return to_arr(ADMIN_XOR_KEY, "g_admin_key") .. "\n" ..
           to_arr(enc,           "g_admin_enc") .. "\n" ..
           to_arr(URL_XOR_KEY,   "g_url_key")   .. "\n" ..
           to_arr(url_enc,       "g_url_enc")
end

local function generar_paquete_cifrado(pkg)
    local enc = cifrar_cadena(pkg, PKG_XOR_KEY)
    local pkg_key_arr = {}
    for _, b in ipairs(PKG_XOR_KEY) do pkg_key_arr[#pkg_key_arr+1] = ("0x%02x"):format(b) end
    local pkg_enc_arr = {}
    for _, b in ipairs(enc)         do pkg_enc_arr[#pkg_enc_arr+1] = ("0x%02x"):format(b) end
    local lines = {}
    lines[#lines+1] = ("static const uint8_t g_pkg_key[] = {%s};"):format(table.concat(pkg_key_arr, ","))
    lines[#lines+1] = ("static const uint8_t g_pkg_enc[%d] = {%s};"):format(#enc, table.concat(pkg_enc_arr, ","))
    lines[#lines+1] = "static char g_paquete[256];"
    lines[#lines+1] = "static void decodificar_paquete(void) {"
    lines[#lines+1] = "    size_t klen = sizeof(g_pkg_key);"
    lines[#lines+1] = "    size_t len  = sizeof(g_pkg_enc);"
    lines[#lines+1] = "    for (size_t i = 0; i < len; i++) {"
    lines[#lines+1] = "        uint8_t pos_mask = (uint8_t)(((i + 1) * 0x9E) & 0xFF);"
    lines[#lines+1] = "        g_paquete[i] = (char)(g_pkg_enc[i] ^ g_pkg_key[i % klen] ^ pos_mask);"
    lines[#lines+1] = "    }"
    lines[#lines+1] = "    g_paquete[len] = '\\0';"
    lines[#lines+1] = "}"
    local ms = ("static const uint8_t MAGIC_SEED[4]  = {0x%02x,0x%02x,0x%02x,0x%02x};"):format(
        MAGIC_SEED[1], MAGIC_SEED[2], MAGIC_SEED[3], MAGIC_SEED[4])
    local ps = ("static const uint8_t PREFIX_SEED[2] = {0x%02x,0x%02x};"):format(
        PREFIX_SEED[1], PREFIX_SEED[2])
    lines[#lines+1] = ms
    lines[#lines+1] = ps
    return table.concat(lines, "\n")
end

local function parse_file(filename)
    local f = assert(io.open(filename, "r"), "ERROR: No se puede abrir '" .. filename .. "'")
    local mods, cur, vdata, pkg_found = {}, nil, {}, nil
    local mod_names = {}
    
    local function save()
        if cur then 
            if mod_names[cur.name] then
                f:close(); error("Mod duplicado: '" .. cur.name .. "'", 0)
            end
            mod_names[cur.name] = true
            cur.versions = vdata
            mods[#mods+1] = cur 
        end
        cur = nil; vdata = {}
    end
    
    local line_no = 0
    for line in f:lines() do
        line_no = line_no + 1
        if line:match("^%s*$") then
        elseif line:lower():match("juntos") and cur then cur.joint = true
        elseif line:match("^%s*%-%-") then
        else
            local pstr = line:match("^%s*PAQUETE%s*:%s*(.-)%s*$")
            if pstr and not line:match("local%s") and trim(pstr) ~= "" then
                local pkg = trim(pstr)
                if not pkg:match("^[%w_%.]+$") then
                    f:close(); error(("PAQUETE inválido en %s, línea %d: '%s'"):format(filename, line_no, pkg), 0)
                end
                pkg_found = pkg
            elseif not line:match("local%s") and not line:match("=") then
                local hdr = line:match("^%s*(.-)%s*:%s*$")
                if hdr and #trim(hdr) > 1 then save(); cur = { name=trim(hdr), joint=false } end
            else
                local vname, content = line:match('local%s+([%w_]+)%s*=%s*{(.-)}')
                if vname and content and cur then
                    local vn  = tonumber(vname:match("^v(%d+)_"))
                    local typ = vname:match("_(buscar)$") or vname:match("_(editar)$") or vname:match("_(ancla)$")
                    if vn and typ then
                        if not vdata[vn] then vdata[vn] = {buscar={}, editar={}, ancla={}} end
                        local found_item = false
                        for item in content:gmatch('"([^"]*)"') do
                            found_item = true
                            local ok, bytes = pcall(parse_hex, item)
                            if not ok then f:close(); error(("ERROR en %s, línea %d (%s): %s"):format(filename, line_no, vname, tostring(bytes)), 0) end
                            if typ == "ancla" then
                                vdata[vn]["ancla"] = bytes
                            else
                                vdata[vn][typ][#vdata[vn][typ] + 1] = bytes
                            end
                        end
                        if not found_item then f:close(); error(("ERROR en %s, línea %d: lista vacía"):format(filename, line_no), 0) end
                    end
                end
            end
        end
    end
    f:close(); save()
    return mods, pkg_found
end

local C_HELPERS = [=[

static int bidx[256][128];
static int bcnt[256];

static void obtener_nombre_kworker(char* out, size_t outlen) {
    strncpy(out, "kworker/u8:1", outlen - 1);
    out[outlen - 1] = '\0';

    DIR* d = opendir("/proc");
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != NULL) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        char path[64], comm[64] = {0};
        snprintf(path, sizeof(path), "/proc/%s/comm", e->d_name);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        if (fgets(comm, sizeof(comm), f)) {
            fclose(f);
            comm[strcspn(comm, "\n")] = '\0';
            if (strncmp(comm, "kworker/", 8) == 0 && strlen(comm) > 8) {
                strncpy(out, comm, outlen - 1);
                out[outlen - 1] = '\0';
                break;
            }
        } else {
            fclose(f);
        }
    }
    closedir(d);
}

static int escribir_mem(pid_t pid, uintptr_t addr, const uint8_t* data, int len) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    int fd = open(path, O_WRONLY);
    if (fd >= 0) {
        ssize_t wr = pwrite64(fd, data, (size_t)len, (off64_t)addr);
        close(fd);
        if (wr == (ssize_t)len) return 0;
    }
    struct iovec lv = { (void*)data, (size_t)len };
    struct iovec rv = { (void*)addr, (size_t)len };
    return (process_vm_writev(pid, &lv, 1, &rv, 1, 0) == (ssize_t)len) ? 0 : -1;
}

static ssize_t leer_mem(pid_t pid, uintptr_t addr, uint8_t* buf, size_t len) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        ssize_t rd = pread64(fd, buf, len, (off64_t)addr);
        close(fd);
        if (rd > 0) return rd;
    }
    struct iovec lv = { buf, len };
    struct iovec rv = { (void*)addr, len };
    return process_vm_readv(pid, &lv, 1, &rv, 1, 0);
}

static int buscar_patron(pid_t pid, const uint8_t* pat, int pat_len) {
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
    FILE* maps = fopen(maps_path, "r");
    if (!maps) return 0;

    const size_t CHUNK = 4 * 1024 * 1024;
    uint8_t* buf = (uint8_t*)malloc(CHUNK + pat_len);
    if (!buf) { fclose(maps); return 0; }

    size_t overlap = (size_t)(pat_len > 1 ? pat_len - 1 : 0);
    char line[512];
    int found = 0;

    while (fgets(line, sizeof(line), maps) && !found) {
        uintptr_t seg_start = 0, seg_end = 0; char perms[8] = {0};
        unsigned long off_file = 0; unsigned int dmaj = 0, dmin = 0;
        unsigned long inode = 0; char pathname[256] = "";
        int fields = sscanf(line, "%lx-%lx %4s %lx %x:%x %lu %255[^\n]",
            &seg_start, &seg_end, perms, &off_file, &dmaj, &dmin, &inode, pathname);
        if (fields < 7) continue;
        if (inode != 0 || dmaj != 0 || dmin != 0) continue;
        if (perms[0] != 'r') continue;
        char* pn = pathname; while (*pn == ' ') pn++;
        if (!strncmp(pn, "[stack", 6) || !strncmp(pn, "[heap", 5) || !strncmp(pn, "[vdso", 5) || !strncmp(pn, "[vvar", 5)) continue;

        size_t rsize = seg_end - seg_start;
        if (rsize == 0 || rsize > 512UL * 1024 * 1024) continue;

        size_t tail = 0; uintptr_t pos = 0;
        while (pos < rsize && !found) {
            size_t want = CHUNK; if (pos + want > rsize) want = rsize - pos;
            ssize_t nr = leer_mem(pid, seg_start + pos, buf + tail, want);
            if (nr <= 0) { tail = 0; pos += 1024*1024; continue; }
            ssize_t total_buf = (ssize_t)tail + nr;

            for (ssize_t i = 0; i < total_buf; i++) {
                if (i + (ssize_t)pat_len > total_buf) continue;
                if (memcmp(buf + i, pat, (size_t)pat_len) == 0) { found = 1; break; }
            }
            pos += (uintptr_t)nr;
            if (overlap > 0) { size_t avail = (size_t)total_buf; size_t keep = avail < overlap ? avail : overlap; memmove(buf, buf + avail - keep, keep); tail = keep; } else { tail = 0; }
        }
    }
    free(buf); fclose(maps);
    return found;
}

static int aplicar_mod(pid_t pid, const Mod* mod) {
    DBG("[*] Aplicando: %s  (%d patrones)\n", mod->nombre, mod->n_pares);
    if (mod->n_pares == 0) return 0;

    if (mod->ancla_len > 0) {
        if (!buscar_patron(pid, mod->ancla, mod->ancla_len)) {
            printf("[-] VERSION_INCOMPATIBLE\n");
            return -1;
        }
    }

    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
    FILE* maps = fopen(maps_path, "r");
    if (!maps) { DBG("[-] Error al abrir maps\n"); return -1; }
    int* done = (int*)calloc((size_t)mod->n_pares, sizeof(int));
    if (!done) { fclose(maps); return -1; }
    int remaining = mod->n_pares;
    int max_pat_len = 0;
    for (int pi = 0; pi < mod->n_pares; pi++)
        if (mod->pares[pi].len > max_pat_len) max_pat_len = mod->pares[pi].len;
    size_t overlap = (size_t)(max_pat_len > 1 ? max_pat_len - 1 : 0);
    
    memset(bcnt, 0, sizeof(bcnt));
    for (int pi = 0; pi < mod->n_pares; pi++) {
        uint8_t fb = (uint8_t)mod->pares[pi].buscar[0];
        if (bcnt[fb] < 128) bidx[fb][bcnt[fb]++] = pi;
    }
    const size_t CHUNK = 4 * 1024 * 1024;
    const size_t MAX_SKIP_ON_FAIL = 1024 * 1024;
    uint8_t* buf = (uint8_t*)malloc(CHUNK + overlap);
    if (!buf) { free(done); fclose(maps); return -1; }
    int total = 0;
    int fallos_seguidos = 0;
    char line[512];
    while (fgets(line, sizeof(line), maps) && remaining > 0) {
        uintptr_t seg_start = 0, seg_end = 0; char perms[8] = {0};
        unsigned long off_file = 0; unsigned int dmaj = 0, dmin = 0;
        unsigned long inode = 0; char pathname[256] = "";
        int fields = sscanf(line, "%lx-%lx %4s %lx %x:%x %lu %255[^\n]",
            &seg_start, &seg_end, perms, &off_file, &dmaj, &dmin, &inode, pathname);
        if (fields < 7) continue;
        if (inode != 0 || dmaj != 0 || dmin != 0) continue;
        if (perms[0] != 'r') continue;
        char* pn = pathname; while (*pn == ' ') pn++;
        if (!strncmp(pn, "[stack", 6) || !strncmp(pn, "[heap", 5) || !strncmp(pn, "[vdso", 5) || !strncmp(pn, "[vvar", 5)) continue;
        size_t rsize = seg_end - seg_start;
        if (rsize == 0 || rsize > 512UL * 1024 * 1024) continue;
        size_t tail = 0; uintptr_t pos = 0;
        while (pos < rsize && remaining > 0) {
            size_t want = CHUNK; if (pos + want > rsize) want = rsize - pos;
            ssize_t nr = leer_mem(pid, seg_start + pos, buf + tail, want);
            if (nr <= 0) { size_t skip = want > MAX_SKIP_ON_FAIL ? MAX_SKIP_ON_FAIL : want; tail = 0; pos += skip; continue; }
            ssize_t total_buf = (ssize_t)tail + nr; uintptr_t buf_base = seg_start + pos - (uintptr_t)tail;
            for (ssize_t i = 0; i < total_buf && remaining > 0; i++) {
                int cnt = bcnt[(uint8_t)buf[i]]; if (!cnt) continue;
                for (int k = 0; k < cnt; k++) {
                    int pi = bidx[(uint8_t)buf[i]][k]; if (done[pi]) continue;
                    const Par* par = &mod->pares[pi];
                    if (i + (ssize_t)par->len > total_buf) continue;
                    if (memcmp(buf + i, par->buscar, (size_t)par->len) != 0) continue;
                    uintptr_t addr = buf_base + (uintptr_t)i;
                    if (escribir_mem(pid, addr, par->escribir, par->len) == 0) {
                        printf("    [+] Activado\n");
                        total++; done[pi] = 1; remaining--;
                        fallos_seguidos = 0;
                    } else {
                        DBG("    [-] Error al escribir\n");
                        if (++fallos_seguidos >= 3) { DBG("[-] Demasiados fallos, abortando\n"); break; }
                    }
                }
            }
            pos += (uintptr_t)nr;
            if (overlap > 0) { size_t avail = (size_t)total_buf; size_t keep = avail < overlap ? avail : overlap; memmove(buf, buf + avail - keep, keep); tail = keep; } else { tail = 0; }
        }
    }
    free(buf); fclose(maps); free(done);
    DBG("[=] %s: %d parche(s)\n\n", mod->nombre, total);
    return total;
}

static pid_t encontrar_pid(const char* pkg) {
    DIR* d = opendir("/proc");
    if (!d) return -1;
    struct dirent* e;
    pid_t pid = -1;
    size_t pkg_len = strlen(pkg);
    while ((e = readdir(d))) {
        int p = atoi(e->d_name);
        if (p <= 0) continue;
        char path[64];
        snprintf(path, sizeof(path), "/proc/%d/cmdline", p);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        char buf[512];
        memset(buf, 0, sizeof(buf));
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        if (n < pkg_len || pkg_len == 0) continue;
        if (strncmp(buf, pkg, pkg_len) != 0) continue;
        if (buf[pkg_len] == '\0') { pid = p; break; }
    }
    closedir(d);
    return pid;
}

static int leer_urandom(void* buf, size_t len) {
    int fd = open("/dev/urandom", O_RDONLY);
    if (fd < 0) return -1;
    size_t total = 0;
    while (total < len) {
        ssize_t n = read(fd, (uint8_t*)buf + total, len - total);
        if (n <= 0) { close(fd); return -1; }
        total += (size_t)n;
    }
    close(fd);
    return 0;
}
static char random_char(const char* charset, size_t setlen) {
    uint8_t b; if (leer_urandom(&b, 1) < 0) return charset[0];
    return charset[b % setlen];
}
static void generar_id(char* out, size_t cap) {
    static const char CS[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    size_t n = (cap > 1) ? cap - 1 : 0;
    for (size_t i = 0; i < n; i++) out[i] = random_char(CS, sizeof(CS) - 1);
    out[n] = '\0';
}
static void generar_usuario(char* out, size_t cap) {
    if (cap < 5) { if (cap > 0) out[0] = '\0'; return; }
    static const char D[] = "0123456789";
    out[0] = 'V'; out[1] = 'I'; out[2] = 'P';
    for (size_t i = 3; i < cap - 1; i++) out[i] = random_char(D, sizeof(D) - 1);
    out[cap - 1] = '\0';
}
static void generar_password(char* out, size_t cap) {
    static const char CS[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    size_t n = (cap > 1) ? cap - 1 : 0;
    for (size_t i = 0; i < n; i++) out[i] = random_char(CS, sizeof(CS) - 1);
    out[n] = '\0';
}
static void generar_expiry(char* out, size_t cap, int dias) {
    time_t now = time(NULL); now += (time_t)dias * 86400;
    struct tm tm_buf;
    if (!localtime_r(&now, &tm_buf)) { snprintf(out, cap, "1970-01-01"); return; }
    strftime(out, cap, "%Y-%m-%d", &tm_buf);
}
static int verificar_pass_admin(const char* input) {
    if (!input) return 0;
    size_t len = sizeof(g_admin_enc);
    if (strlen(input) != len) return 0;
    for (size_t i = 0; i < len; i++) {
        uint8_t pos_mask = (uint8_t)(((i + 1) * 0x9E) & 0xFF);
        if ((uint8_t)(input[i] ^ g_admin_key[i % sizeof(g_admin_key)] ^ pos_mask) != g_admin_enc[i]) return 0;
    }
    return 1;
}

static int modo_admin(const char* pass, const char* expiry_arg) {
    if (!verificar_pass_admin(pass)) {
        printf("[-] ADMIN_FAIL\n[-] Contrasena incorrecta\n");
        return 1;
    }
    char id[9], usuario[9], password[9], expiry[16];
    generar_id(id,             sizeof(id));
    generar_usuario(usuario,   sizeof(usuario));
    generar_password(password, sizeof(password));
    
    int valid_date = 1;
    if (expiry_arg && strlen(expiry_arg) == 10 && expiry_arg[4] == '-' && expiry_arg[7] == '-') {
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            if (expiry_arg[i] < '0' || expiry_arg[i] > '9') { valid_date = 0; break; }
        }
        if (valid_date) {
            int mes = (expiry_arg[5]-'0')*10 + (expiry_arg[6]-'0');
            int dia = (expiry_arg[8]-'0')*10 + (expiry_arg[9]-'0');
            if (mes < 1 || mes > 12 || dia < 1 || dia > 31) valid_date = 0;
        }
    } else {
        valid_date = 0;
    }

    if (valid_date) {
        strncpy(expiry, expiry_arg, sizeof(expiry) - 1);
        expiry[sizeof(expiry) - 1] = '\0';
    } else {
        generar_expiry(expiry, sizeof(expiry), 30);
    }
    printf("[+] ADMIN_OK\n");
    printf("[+] ID=%s\n[+] USUARIO=%s\n[+] PASSWORD=%s\n[+] EXPIRY=%s\n", id, usuario, password, expiry);
    return 0;
}

static int modo_get_url(const char* sec_key) {
    if (!sec_key || strlen(sec_key) < 40 || strchr(sec_key, '_') == NULL) {
        printf("[-] Clave invalida\n");
        return 1;
    }
    size_t len = sizeof(g_url_enc);
    char url[512];
    for (size_t i = 0; i < len; i++) {
        uint8_t pos_mask = (uint8_t)(((i + 1) * 0x9E) & 0xFF);
        url[i] = (char)((uint8_t)g_url_enc[i] ^ g_url_key[i % sizeof(g_url_key)] ^ pos_mask);
    }
    url[len] = '\0';
    printf("[+] URL=%s\n", url);
    return 0;
}

static int verificar_sesion_activa(const char* provided_key) {
    if (!provided_key || strlen(provided_key) < 8) return 0;

    char sec_key[128] = {0};
    strncpy(sec_key, provided_key, sizeof(sec_key) - 1);
    size_t klen = strlen(sec_key);

    time_t t = time(NULL);
    struct tm *tm_s = localtime(&t);
    char hoy[16];
    strftime(hoy, sizeof(hoy), "%Y-%m-%d", tm_s);

    char expected_prefix[5];
    snprintf(expected_prefix, sizeof(expected_prefix), "%02x%02x",
        (uint8_t)((uint8_t)sec_key[0] ^ PREFIX_SEED[0]),
        (uint8_t)((uint8_t)sec_key[1] ^ PREFIX_SEED[1]));

    const char *rutas[] = {
        "/data/user/0/com.sysopt.displayservice/cache",
        "/data/data/com.sysopt.displayservice/cache",
        "/storage/emulated/0/Android/data/com.sysopt.displayservice/cache",
        "/storage/emulated/0/Android/obb/com.sysopt.displayservice"
    };

    for (int r = 0; r < 4; r++) {
        DIR *d = opendir(rutas[r]);
        if (!d) continue;
        struct dirent *e;
        while ((e = readdir(d))) {
            if (strncmp(e->d_name, expected_prefix, 4) != 0) continue;
            if (strstr(e->d_name, ".bin") == NULL) continue;
            char path[512];
            snprintf(path, sizeof(path), "%s/%s", rutas[r], e->d_name);
            FILE *f = fopen(path, "rb");
            if (!f) continue;
            fseek(f, 0, SEEK_END); long fsize = ftell(f); fseek(f, 0, SEEK_SET);
            if (fsize > 4) {
                uint8_t *buf = (uint8_t *)malloc(fsize + 1);
                if (buf) {
                    if (fread(buf, 1, fsize, f) == (size_t)fsize) {
                        buf[fsize] = '\0';
                        int ok_magic = 1;
                        for (int i = 0; i < 4; i++) {
                            if ((uint8_t)(buf[i] ^ (uint8_t)sec_key[i % klen]) != MAGIC_SEED[i]) { ok_magic = 0; break; }
                        }
                        if (ok_magic) {
                            for (long i = 4; i < fsize; i++) buf[i] ^= (uint8_t)sec_key[(i - 4) % klen];
                            char *data = (char *)(buf + 4);
                            char *p1 = strchr(data, '|');
                            if (p1) {
                                char *p2 = strchr(p1 + 1, '|');
                                if (p2) {
                                    char *p3 = strchr(p2 + 1, '|');
                                    if (p3) {
                                        char *expiry = p3 + 1;
                                        int fmt_ok = (strlen(expiry) >= 10);
                                        if (fmt_ok) {
                                            for (int i = 0; i < 10; i++) {
                                                if (i == 4 || i == 7) { if (expiry[i] != '-') { fmt_ok = 0; break; } }
                                                else if (expiry[i] < '0' || expiry[i] > '9') { fmt_ok = 0; break; }
                                            }
                                        }
                                        if (fmt_ok && strncmp(expiry, hoy, 10) >= 0) {
                                            free(buf); fclose(f); closedir(d); return 1;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    free(buf);
                }
            }
            fclose(f);
        }
        closedir(d);
    }
    return 0;
}

int main(int argc, char** argv) {
    // 1.8: Modos que no necesitan camuflaje ni fork
    if (argc >= 3 && strcmp(argv[1], "--admin") == 0) {
        return modo_admin(argv[2], argc >= 4 ? argv[3] : NULL);
    }
    if (argc >= 3 && strcmp(argv[1], "--get-url") == 0) {
        return modo_get_url(argv[2]);
    }

    // Modo parche: sin fork, pero con camuflaje
    char kname[64];
    obtener_nombre_kworker(kname, sizeof(kname));

    prctl(PR_SET_DUMPABLE, 0, 0, 0, 0);
    prctl(PR_SET_NAME, kname, 0, 0, 0);
    if (argc > 0 && argv[0]) {
        char fake[80];
        snprintf(fake, sizeof(fake), "[%s]", kname);
        size_t orig = strlen(argv[0]);
        if (orig > 0) {
            size_t flen = strlen(fake);
            size_t copy = flen < orig ? flen : orig - 1;
            memset(argv[0], 0, orig);
            memcpy(argv[0], fake, copy);
        }
    }

    // CORRECCIÓN 1.2: Limpiar variables de entorno
    clearenv();

    signal(SIGPIPE, SIG_IGN);
    // NO cerramos stdin/stdout para que Java pueda leer el resultado

    decodificar_paquete();

    const char* provided_key = (argc >= 3) ? argv[2] : NULL;
    if (!verificar_sesion_activa(provided_key)) {
        printf("[-] ACCESO DENEGADO: Sesion VIP expirada.\n");
        return 1;
    }

    pid_t pid = encontrar_pid(g_paquete);
    if (pid < 0) { printf("[-] Juego no encontrado.\n"); return 1; }
    DBG("[+] PID: %d\n\n", (int)pid);

    if (argc >= 2) {
        char* endptr; long sel = strtol(argv[1], &endptr, 10);
        if (endptr == argv[1] || *endptr != '\0') return 1;
        if (sel < 0 || sel >= N_MODS) return 1;
        aplicar_mod(pid, &MODS[(int)sel]);
        return 0;
    }

    printf("[-] Uso: %s <mod_index> <key> | --admin <pass> | --get-url <key>\n", argv[0]);
    return 1;
}
]=]

local function generate_c(mods, pkg)
    local out = {}
    local function w(s) out[#out+1] = s end
    w("#define _GNU_SOURCE 1")
    w("/* Auto-generado por generador_V33.lua */")
    w("#include <stdio.h>\n#include <stdlib.h>\n#include <string.h>\n#include <errno.h>\n#include <stdint.h>\n#include <sys/types.h>\n#include <dirent.h>\n#include <sys/uio.h>\n#include <fcntl.h>\n#include <unistd.h>\n#include <signal.h>\n#include <time.h>\n#include <sys/prctl.h>")
    w("extern int clearenv(void);\n") -- CORRECCIÓN PARA BIONIC
    w("#ifdef DEBUG\n#define DBG(...) printf(__VA_ARGS__)\n#else\n#define DBG(...) ((void)0)\n#endif\n")
    w(generar_paquete_cifrado(pkg))
    w("typedef struct { const uint8_t* buscar; const uint8_t* escribir; int len; } Par;")
    w("typedef struct { const char* nombre; const Par* pares; int n_pares; const uint8_t* ancla; int ancla_len; } Mod;")
    w("#define ARR_LEN(a) ((int)(sizeof(a)/sizeof((a)[0])))")
    local mod_entries = {}
    for mi, mod in ipairs(mods) do
        local mpfx = ("G%d"):format(mi)
        w(("/* ═══ %s ═══ */"):format(mod.name))
        local sorted = {}
        for vn, vd in pairs(mod.versions) do sorted[#sorted+1] = {n=vn, d=vd} end
        table.sort(sorted, function(a,b) return a.n < b.n end)
        local function emit_ver(vd, prefix)
            local entries = {}
            if #vd.buscar == 0 then error("La variante " .. prefix .. " no tiene patrones de búsqueda.", 0) end
            if #vd.editar == 0 then error("La variante " .. prefix .. " no tiene patrones de reemplazo.", 0) end
            
            local ancla_str = "NULL"
            local ancla_len = "0"
            if #vd.ancla > 0 then
                local ancla_sym = prefix .. "_A01"
                w(("static const uint8_t %s[%d] = %s;"):format(ancla_sym, #vd.ancla, to_c(vd.ancla)))
                ancla_str = ancla_sym
                ancla_len = tostring(#vd.ancla)
            end

            for bi, bpat in ipairs(vd.buscar) do
                local epat = (#vd.editar == 1) and vd.editar[1] or vd.editar[bi]
                if #bpat ~= #epat then error(("Longitudes distintas en %s patrón %d: búsqueda=%d, reemplazo=%d"):format(prefix, bi, #bpat, #epat), 0) end
                local ss = ("%s_S%02d"):format(prefix, bi)
                local ws = ("%s_W%02d"):format(prefix, bi)
                w(("static const uint8_t %s[%d] = %s;"):format(ss, #bpat, to_c(bpat)))
                w(("static const uint8_t %s[%d] = %s;"):format(ws, #epat, to_c(epat)))
                entries[#entries+1] = ("    {%s, %s, %d}"):format(ss, ws, #bpat)
            end
            return entries, ancla_str, ancla_len
        end
        if mod.joint then
            local all = {}
            local ancla_final = "NULL"
            local ancla_len_final = "0"
            for _, vi in ipairs(sorted) do
                local pe, a_sym, a_len = emit_ver(vi.d, ("%s_V%d"):format(mpfx, vi.n))
                for _, e in ipairs(pe) do all[#all+1] = e end
                if a_sym ~= "NULL" then ancla_final = a_sym; ancla_len_final = a_len end
            end
            local psym = mpfx .. "_PARES"
            w(("static const Par %s[] = {"):format(psym))
            for _, e in ipairs(all) do w(e .. ",") end
            w("};"); mod_entries[#mod_entries+1] = { psym, mod.name, ancla_final, ancla_len_final }
        else
            for _, vi in ipairs(sorted) do
                local pe, a_sym, a_len = emit_ver(vi.d, ("%s_V%d"):format(mpfx, vi.n))
                local psym = ("%s_V%d_PARES"):format(mpfx, vi.n)
                w(("static const Par %s[] = {"):format(psym))
                for _, e in ipairs(pe) do w(e .. ",") end
                w("};"); mod_entries[#mod_entries+1] = { psym, mod.name .. " v" .. vi.n, a_sym, a_len }
            end
        end
    end
    w("static const Mod MODS[] = {")
    for _, me in ipairs(mod_entries) do 
        w(('    {"%s", %s, ARR_LEN(%s), %s, %s},'):format(me[2], me[1], me[1], me[3], me[4])) 
    end
    w("};\n#define N_MODS ARR_LEN(MODS)\n")
    w(generar_arrays_admin())
    w(C_HELPERS)
    return table.concat(out, "\n")
end

local mods, pkg_in_file = parse_file(INPUT_FILE)
assert(#mods > 0, "No se detectaron mods en '" .. INPUT_FILE .. "'\nAsegurate de que el archivo tenga encabezados tipo 'NOMBRE:'")
local PAQUETE = pkg_in_file or PAQUETE_DEFAULT
local c_src = generate_c(mods, PAQUETE)
local fout = assert(io.open(OUTPUT_FILE, "w"), "ERROR: No se puede escribir '" .. OUTPUT_FILE .. "'")
fout:write(c_src)
fout:close()