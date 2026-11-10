#include "patcher.h"
#include "patterns.h"
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <unistd.h>

static int buscar_interno(const uint8_t* pat, size_t plen, uintptr_t* out_addr, int* out_perms) {
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return 0;
    char line[512];
    int found = 0;

    while (fgets(line, sizeof(line), f)) {
        uintptr_t start, end;
        char perms[8] = {0};
        unsigned long off_file, inode = 0;
        unsigned int dmaj = 0, dmin = 0;
        char pathname[256] = "";
        
        int fields = sscanf(line, "%lx-%lx %4s %lx %x:%x %lu %255[^\n]",
                            &start, &end, perms, &off_file, &dmaj, &dmin, &inode, pathname);
        if (fields < 7) continue;
        
        // Solo regiones anónimas y rw
        if (perms[0] != 'r' || perms[1] != 'w') continue;
        if (inode != 0 || dmaj != 0 || dmin != 0) continue;
        if (strstr(pathname, "[stack") || strstr(pathname, "[vdso]") || strstr(pathname, "[vvar]")) continue;

        const uint8_t* base = (const uint8_t*)start;
        size_t sz = end - start;
        if (sz < plen) continue;

        for (size_t i = 0; i + plen <= sz; i++) {
            if (memcmp(base + i, pat, plen) == 0) {
                *out_addr = start + i;
                // Guardar permisos originales
                if (perms[1] == 'w' && perms[2] == 'x') *out_perms = PROT_READ | PROT_WRITE | PROT_EXEC;
                else if (perms[1] == 'w') *out_perms = PROT_READ | PROT_WRITE;
                else if (perms[2] == 'x') *out_perms = PROT_READ | PROT_EXEC;
                else *out_perms = PROT_READ;
                
                found = 1;
                break;
            }
        }
        if (found) break;
    }
    fclose(f);
    return found;
}

static int escribir_interno(uintptr_t addr, const uint8_t* data, size_t len, int original_perms) {
    long pagesz = sysconf(_SC_PAGESIZE);
    uintptr_t pg = addr & ~((uintptr_t)pagesz - 1);
    size_t span = (addr - pg) + len;
    size_t npages = (span + pagesz - 1) / pagesz * pagesz;

    // Hacer escribible
    if (mprotect((void*)pg, npages, PROT_READ | PROT_WRITE) != 0) return 0;
    
    // Escribir
    memcpy((void*)addr, data, len);
    
    // Restaurar permisos originales
    mprotect((void*)pg, npages, original_perms);
    
    // Limpiar caché de instrucciones (crucial en ARM)
    __builtin___clear_cache((char*)addr, (char*)(addr + len));
    return 1;
}

int aplicar_mod(int idx) {
    if (idx < 0 || idx >= N_MODS) return 0;
    const Mod* mod = &MODS[idx];
    if (mod->n_pares == 0) return 0;

    // Verificar ancla de versión (si existe)
    if (mod->ancla && mod->ancla_len > 0) {
        uintptr_t a; int p;
        if (!buscar_interno(mod->ancla, mod->ancla_len, &a, &p)) return 2; // Incompatible
    }

    int aplicados = 0;
    for (int p = 0; p < mod->n_pares; p++) {
        uintptr_t addr; int perms;
        if (buscar_interno(mod->pares[p].buscar, mod->pares[p].len, &addr, &perms)) {
            if (escribir_interno(addr, mod->pares[p].editar, mod->pares[p].len, perms)) {
                aplicados++;
            }
        }
    }
    return aplicados > 0 ? 1 : 0;
}