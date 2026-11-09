#define _GNU_SOURCE 1
/* Auto-generado por generador_V33.lua */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>
#include <sys/types.h>
#include <dirent.h>
#include <sys/uio.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <sys/prctl.h>
extern int clearenv(void);

#ifdef DEBUG
#define DBG(...) printf(__VA_ARGS__)
#else
#define DBG(...) ((void)0)
#endif

static const uint8_t g_pkg_key[] = {0x4b,0x9d,0x27,0xe3,0x6a,0xb1,0x58,0x0f,0xc4,0x73,0x8e,0x15,0xd2,0x49,0xa6,0x3c};
static const uint8_t g_pkg_enc[18] = {0xb6,0xce,0x90,0xb5,0x18,0x71,0x79,0xd1,0x2c,0x2d,0x21,0x18,0xb2,0x84,0x96,0xb9,0x41,0xe9};
static char g_paquete[256];
static void decodificar_paquete(void) {
    size_t klen = sizeof(g_pkg_key);
    size_t len  = sizeof(g_pkg_enc);
    for (size_t i = 0; i < len; i++) {
        uint8_t pos_mask = (uint8_t)(((i + 1) * 0x9E) & 0xFF);
        g_paquete[i] = (char)(g_pkg_enc[i] ^ g_pkg_key[i % klen] ^ pos_mask);
    }
    g_paquete[len] = '\0';
}
static const uint8_t MAGIC_SEED[4]  = {0x4e,0x52,0xa1,0x08};
static const uint8_t PREFIX_SEED[2] = {0x7c,0x3a};
typedef struct { const uint8_t* buscar; const uint8_t* escribir; int len; } Par;
typedef struct { const char* nombre; const Par* pares; int n_pares; const uint8_t* ancla; int ancla_len; } Mod;
#define ARR_LEN(a) ((int)(sizeof(a)/sizeof((a)[0])))
/* ═══ TRADICIONALES ═══ */
static const uint8_t G1_V1_S01[4] = {0xc1,0x36,0x41,0x06};
static const uint8_t G1_V1_W01[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S02[4] = {0x18,0x7b,0x82,0x06};
static const uint8_t G1_V1_W02[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S03[4] = {0xa8,0xe5,0xac,0x00};
static const uint8_t G1_V1_W03[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S04[4] = {0x7d,0xac,0x91,0x01};
static const uint8_t G1_V1_W04[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S05[4] = {0xea,0x10,0x66,0x02};
static const uint8_t G1_V1_W05[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S06[4] = {0xf8,0x9c,0x66,0x02};
static const uint8_t G1_V1_W06[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S07[4] = {0xb6,0xbb,0x07,0x05};
static const uint8_t G1_V1_W07[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S08[4] = {0x28,0x99,0x92,0x05};
static const uint8_t G1_V1_W08[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S09[4] = {0x7f,0xcd,0x9b,0x05};
static const uint8_t G1_V1_W09[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S10[4] = {0xdb,0x17,0x87,0x06};
static const uint8_t G1_V1_W10[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S11[4] = {0x95,0x6a,0xa5,0x0b};
static const uint8_t G1_V1_W11[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S12[4] = {0x3c,0x93,0x82,0x0d};
static const uint8_t G1_V1_W12[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S13[4] = {0xa4,0x7d,0xd8,0x00};
static const uint8_t G1_V1_W13[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S14[4] = {0xae,0xe0,0x13,0x01};
static const uint8_t G1_V1_W14[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S15[4] = {0xc8,0x5e,0x31,0x05};
static const uint8_t G1_V1_W15[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S16[4] = {0x2d,0x6c,0xbc,0x0a};
static const uint8_t G1_V1_W16[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S17[4] = {0x38,0xf8,0x96,0x03};
static const uint8_t G1_V1_W17[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S18[4] = {0xeb,0xf7,0xea,0x06};
static const uint8_t G1_V1_W18[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S19[4] = {0xf9,0x8c,0x27,0x07};
static const uint8_t G1_V1_W19[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S20[4] = {0x7a,0x2b,0x6f,0x07};
static const uint8_t G1_V1_W20[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S21[4] = {0x59,0xd7,0xcf,0x07};
static const uint8_t G1_V1_W21[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S22[4] = {0x1a,0x5b,0x15,0x08};
static const uint8_t G1_V1_W22[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S23[4] = {0xdb,0x54,0xcc,0x09};
static const uint8_t G1_V1_W23[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S24[4] = {0xc8,0xfd,0x01,0x0d};
static const uint8_t G1_V1_W24[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S25[4] = {0xe6,0xc2,0x7e,0x0d};
static const uint8_t G1_V1_W25[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S26[4] = {0xc4,0xb7,0x7f,0x0d};
static const uint8_t G1_V1_W26[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S27[4] = {0xfd,0x08,0x31,0x00};
static const uint8_t G1_V1_W27[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S28[4] = {0x73,0x81,0x8e,0x04};
static const uint8_t G1_V1_W28[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S29[4] = {0x99,0x71,0xe9,0x06};
static const uint8_t G1_V1_W29[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S30[4] = {0x29,0xa4,0x3e,0x07};
static const uint8_t G1_V1_W30[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S31[4] = {0x1c,0x32,0x08,0x08};
static const uint8_t G1_V1_W31[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S32[4] = {0x80,0x8e,0xf8,0x0b};
static const uint8_t G1_V1_W32[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V1_S33[4] = {0xf5,0x7c,0xd3,0x01};
static const uint8_t G1_V1_W33[4] = {0xb9,0x46,0x5c,0x02};
static const uint8_t G1_V2_S01[4] = {0x81,0xe9,0x9d,0x27};
static const uint8_t G1_V2_W01[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S02[4] = {0xe4,0x98,0xc6,0x04};
static const uint8_t G1_V2_W02[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S03[4] = {0x53,0x39,0xbf,0x01};
static const uint8_t G1_V2_W03[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S04[4] = {0x65,0xdd,0x8d,0x02};
static const uint8_t G1_V2_W04[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S05[4] = {0x40,0x6c,0xa7,0x02};
static const uint8_t G1_V2_W05[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S06[4] = {0xbc,0xf8,0x51,0x04};
static const uint8_t G1_V2_W06[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S07[4] = {0x98,0xbb,0xe1,0x04};
static const uint8_t G1_V2_W07[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S08[4] = {0xf9,0x89,0x66,0x07};
static const uint8_t G1_V2_W08[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S09[4] = {0xce,0xca,0xdb,0x0d};
static const uint8_t G1_V2_W09[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S10[4] = {0x46,0xb1,0x3d,0x0e};
static const uint8_t G1_V2_W10[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S11[4] = {0x01,0x06,0x85,0x00};
static const uint8_t G1_V2_W11[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S12[4] = {0x58,0x78,0x96,0x05};
static const uint8_t G1_V2_W12[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S13[4] = {0xbd,0xc1,0x17,0x0a};
static const uint8_t G1_V2_W13[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S14[4] = {0x64,0x41,0x93,0x0c};
static const uint8_t G1_V2_W14[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S15[4] = {0x57,0x81,0xe5,0x01};
static const uint8_t G1_V2_W15[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S16[4] = {0xdc,0x03,0xa4,0x07};
static const uint8_t G1_V2_W16[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S17[4] = {0x0d,0x59,0x1b,0x0d};
static const uint8_t G1_V2_W17[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S18[4] = {0xb5,0x53,0x3b,0x0e};
static const uint8_t G1_V2_W18[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S19[4] = {0xe1,0x04,0x50,0x00};
static const uint8_t G1_V2_W19[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S20[4] = {0xf7,0xdb,0x22,0x03};
static const uint8_t G1_V2_W20[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S21[4] = {0x18,0x9d,0x29,0x03};
static const uint8_t G1_V2_W21[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S22[4] = {0x06,0x9f,0xfb,0x04};
static const uint8_t G1_V2_W22[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S23[4] = {0xc6,0x1a,0x09,0x0a};
static const uint8_t G1_V2_W23[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S24[4] = {0x7d,0x9f,0x0a,0x0a};
static const uint8_t G1_V2_W24[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S25[4] = {0x72,0x4e,0x6d,0x0b};
static const uint8_t G1_V2_W25[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S26[4] = {0xa7,0xd2,0x73,0x01};
static const uint8_t G1_V2_W26[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S27[4] = {0xdb,0x42,0x25,0x08};
static const uint8_t G1_V2_W27[4] = {0x40,0x2b,0x2c,0x0a};
static const uint8_t G1_V2_S28[4] = {0xe9,0x9d,0x27,0x00};
static const uint8_t G1_V2_W28[4] = {0x40,0x2b,0x2c,0x0a};
static const Par G1_PARES[] = {
    {G1_V1_S01, G1_V1_W01, 4},
    {G1_V1_S02, G1_V1_W02, 4},
    {G1_V1_S03, G1_V1_W03, 4},
    {G1_V1_S04, G1_V1_W04, 4},
    {G1_V1_S05, G1_V1_W05, 4},
    {G1_V1_S06, G1_V1_W06, 4},
    {G1_V1_S07, G1_V1_W07, 4},
    {G1_V1_S08, G1_V1_W08, 4},
    {G1_V1_S09, G1_V1_W09, 4},
    {G1_V1_S10, G1_V1_W10, 4},
    {G1_V1_S11, G1_V1_W11, 4},
    {G1_V1_S12, G1_V1_W12, 4},
    {G1_V1_S13, G1_V1_W13, 4},
    {G1_V1_S14, G1_V1_W14, 4},
    {G1_V1_S15, G1_V1_W15, 4},
    {G1_V1_S16, G1_V1_W16, 4},
    {G1_V1_S17, G1_V1_W17, 4},
    {G1_V1_S18, G1_V1_W18, 4},
    {G1_V1_S19, G1_V1_W19, 4},
    {G1_V1_S20, G1_V1_W20, 4},
    {G1_V1_S21, G1_V1_W21, 4},
    {G1_V1_S22, G1_V1_W22, 4},
    {G1_V1_S23, G1_V1_W23, 4},
    {G1_V1_S24, G1_V1_W24, 4},
    {G1_V1_S25, G1_V1_W25, 4},
    {G1_V1_S26, G1_V1_W26, 4},
    {G1_V1_S27, G1_V1_W27, 4},
    {G1_V1_S28, G1_V1_W28, 4},
    {G1_V1_S29, G1_V1_W29, 4},
    {G1_V1_S30, G1_V1_W30, 4},
    {G1_V1_S31, G1_V1_W31, 4},
    {G1_V1_S32, G1_V1_W32, 4},
    {G1_V1_S33, G1_V1_W33, 4},
    {G1_V2_S01, G1_V2_W01, 4},
    {G1_V2_S02, G1_V2_W02, 4},
    {G1_V2_S03, G1_V2_W03, 4},
    {G1_V2_S04, G1_V2_W04, 4},
    {G1_V2_S05, G1_V2_W05, 4},
    {G1_V2_S06, G1_V2_W06, 4},
    {G1_V2_S07, G1_V2_W07, 4},
    {G1_V2_S08, G1_V2_W08, 4},
    {G1_V2_S09, G1_V2_W09, 4},
    {G1_V2_S10, G1_V2_W10, 4},
    {G1_V2_S11, G1_V2_W11, 4},
    {G1_V2_S12, G1_V2_W12, 4},
    {G1_V2_S13, G1_V2_W13, 4},
    {G1_V2_S14, G1_V2_W14, 4},
    {G1_V2_S15, G1_V2_W15, 4},
    {G1_V2_S16, G1_V2_W16, 4},
    {G1_V2_S17, G1_V2_W17, 4},
    {G1_V2_S18, G1_V2_W18, 4},
    {G1_V2_S19, G1_V2_W19, 4},
    {G1_V2_S20, G1_V2_W20, 4},
    {G1_V2_S21, G1_V2_W21, 4},
    {G1_V2_S22, G1_V2_W22, 4},
    {G1_V2_S23, G1_V2_W23, 4},
    {G1_V2_S24, G1_V2_W24, 4},
    {G1_V2_S25, G1_V2_W25, 4},
    {G1_V2_S26, G1_V2_W26, 4},
    {G1_V2_S27, G1_V2_W27, 4},
    {G1_V2_S28, G1_V2_W28, 4},
};
/* ═══ COLORES ═══ */
static const uint8_t G2_V1_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V1_W01[4] = {0x3a,0x7f,0x57,0x0a};
static const Par G2_V1_PARES[] = {
    {G2_V1_S01, G2_V1_W01, 4},
};
static const uint8_t G2_V2_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V2_W01[4] = {0xba,0xa7,0x99,0x0b};
static const Par G2_V2_PARES[] = {
    {G2_V2_S01, G2_V2_W01, 4},
};
static const uint8_t G2_V3_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V3_W01[4] = {0x63,0xb0,0x3a,0x0e};
static const uint8_t G2_V3_S02[4] = {0xcc,0x80,0x12,0x01};
static const uint8_t G2_V3_W02[4] = {0x86,0x05,0x41,0x04};
static const Par G2_V3_PARES[] = {
    {G2_V3_S01, G2_V3_W01, 4},
    {G2_V3_S02, G2_V3_W02, 4},
};
static const uint8_t G2_V4_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V4_W01[4] = {0x8c,0xcf,0xa4,0x02};
static const uint8_t G2_V4_S02[4] = {0xcc,0x80,0x12,0x01};
static const uint8_t G2_V4_W02[4] = {0xf1,0x46,0x5e,0x0a};
static const Par G2_V4_PARES[] = {
    {G2_V4_S01, G2_V4_W01, 4},
    {G2_V4_S02, G2_V4_W02, 4},
};
static const uint8_t G2_V5_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V5_W01[4] = {0x63,0xb0,0x3a,0x0e};
static const uint8_t G2_V5_S02[4] = {0xcc,0x80,0x12,0x01};
static const uint8_t G2_V5_W02[4] = {0xc8,0x64,0xc8,0x0a};
static const Par G2_V5_PARES[] = {
    {G2_V5_S01, G2_V5_W01, 4},
    {G2_V5_S02, G2_V5_W02, 4},
};
static const uint8_t G2_V6_S01[4] = {0xbd,0x76,0xca,0x01};
static const uint8_t G2_V6_W01[4] = {0x09,0xaa,0xbf,0x0d};
static const uint8_t G2_V6_S02[4] = {0xcc,0x80,0x12,0x01};
static const uint8_t G2_V6_W02[4] = {0xf1,0x46,0x5e,0x0a};
static const Par G2_V6_PARES[] = {
    {G2_V6_S01, G2_V6_W01, 4},
    {G2_V6_S02, G2_V6_W02, 4},
};
static const uint8_t G2_V7_S01[4] = {0x96,0x8d,0x66,0x02};
static const uint8_t G2_V7_W01[4] = {0xcc,0x2d,0xa6,0x0b};
static const Par G2_V7_PARES[] = {
    {G2_V7_S01, G2_V7_W01, 4},
};
static const uint8_t G2_V8_S01[16] = {0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x3f,0x00,0x00,0x00,0x00};
static const uint8_t G2_V8_W01[16] = {0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xbf,0x00,0x00,0x00,0x00};
static const Par G2_V8_PARES[] = {
    {G2_V8_S01, G2_V8_W01, 16},
};
static const Mod MODS[] = {
    {"TRADICIONALES", G1_PARES, ARR_LEN(G1_PARES), NULL, 0},
    {"COLORES v1", G2_V1_PARES, ARR_LEN(G2_V1_PARES), NULL, 0},
    {"COLORES v2", G2_V2_PARES, ARR_LEN(G2_V2_PARES), NULL, 0},
    {"COLORES v3", G2_V3_PARES, ARR_LEN(G2_V3_PARES), NULL, 0},
    {"COLORES v4", G2_V4_PARES, ARR_LEN(G2_V4_PARES), NULL, 0},
    {"COLORES v5", G2_V5_PARES, ARR_LEN(G2_V5_PARES), NULL, 0},
    {"COLORES v6", G2_V6_PARES, ARR_LEN(G2_V6_PARES), NULL, 0},
    {"COLORES v7", G2_V7_PARES, ARR_LEN(G2_V7_PARES), NULL, 0},
    {"COLORES v8", G2_V8_PARES, ARR_LEN(G2_V8_PARES), NULL, 0},
};
#define N_MODS ARR_LEN(MODS)

static const uint8_t g_admin_key[] = {0xa7,0x3f,0x91,0xc4,0x12,0x88,0x5e,0xd3,0x6b,0x24,0xfa,0x07,0x3d,0x9c,0x41,0xb8,0x55,0xe2,0x0a,0x76,0xcd,0x19,0x8f,0x30,0xb1,0x4e,0x6d,0xf7,0x02,0x5b,0xa0,0x3c};
static const uint8_t g_admin_enc[] = {0x08,0x31,0x79,0x84,0x37,0x0c};
static const uint8_t g_url_key[] = {0x11,0x22,0x33,0x44,0x55,0x66,0x77,0x88,0x99,0xaa,0xbb,0xcc,0xdd,0xee,0xff,0x00,0x1a,0x2b,0x3c,0x4d,0x5e,0x6f,0x7a,0x8b,0x9c,0xad,0xbe,0xcf,0xd0,0xe1,0xf2,0x03};
static const uint8_t g_url_enc[] = {0xe7,0x6a,0x9d,0x4c,0x30,0xe8,0x0a,0x57,0x64,0xe5,0x03,0xcd,0xab,0x3e,0x93,0x87,0x0b,0x58,0xe1,0x79,0xcd,0xd5,0x2b,0x34,0x9f,0x8e,0x79,0xe6,0x55,0x17,0xbf,0xb0,0x60,0xad,0x86,0x3d,0xc8,0x74,0x1c,0x5b,0xb5,0x31,0x59,0x8d,0x5e,0xd5,0xaf,0x96,0x12,0xc0,0x36,0x02,0x90,0x6f,0xc4,0x44,0xfb,0x35,0xa5,0xbd,0x01,0xd0,0x6a,0xc8,0x5b,0xa7,0x10,0xd2,0xa7,0x17,0xc6,0xb4,0xa7,0x74,0xae,0x70,0x31,0x95,0x7e,0x51,0x95,0xe0,0x63,0xf7,0x47,0x22,0xa3,0xbf,0x39,0x73,0xc0,0x52,0xd3,0xaa,0x37,0x7a,0x95,0x32,0x47,0xb2,0x46,0xdc,0xd1,0xc2,0x10,0x97,0xf8,0x25,0xec,0x25,0x18,0x58,0xc1,0x14};

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
