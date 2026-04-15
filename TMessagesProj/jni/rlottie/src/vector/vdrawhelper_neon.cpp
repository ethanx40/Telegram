#if defined(__ARM_NEON__) || defined(__ARM64_NEON__)

#include "vdrawhelper.h"

#ifdef PIXMAN_NEON_NO_ASM
/*
 * Pure-C fallbacks for macOS host builds where the .S pixman assembly files
 * are not compiled.  These are only used when cross-compiling on macOS; the
 * real NEON assembly implementations in pixman-arm-neon-asm.S /
 * pixman-arma64-neon-asm.S are used on Linux CI and on-device.
 */
extern "C" void pixman_composite_src_n_8888_asm_neon(int32_t w, int32_t h,
                                                     uint32_t *dst,
                                                     int32_t   dst_stride,
                                                     uint32_t  src)
{
    for (int32_t y = 0; y < h; y++) {
        for (int32_t x = 0; x < w; x++)
            dst[x] = src;
        dst += dst_stride;
    }
}

extern "C" void pixman_composite_over_n_8888_asm_neon(int32_t w, int32_t h,
                                                      uint32_t *dst,
                                                      int32_t   dst_stride,
                                                      uint32_t  src)
{
    uint32_t sa = src >> 24;
    uint32_t ia = 255 - sa;
    for (int32_t y = 0; y < h; y++) {
        for (int32_t x = 0; x < w; x++) {
            uint32_t d = dst[x];
            /* porter-duff over: result = src + dst*(1-src_alpha) */
            uint32_t rb = ((src & 0x00FF00FFu) +
                           (((d & 0x00FF00FFu) * ia + 0x00800080u) >> 8 & 0x00FF00FFu));
            uint32_t ag = (((src >> 8) & 0x00FF00FFu) +
                           ((((d >> 8) & 0x00FF00FFu) * ia + 0x00800080u) >> 8 & 0x00FF00FFu));
            dst[x] = (rb & 0x00FF00FFu) | ((ag << 8) & 0xFF00FF00u);
        }
        dst += dst_stride;
    }
}
#else
extern "C" void pixman_composite_src_n_8888_asm_neon(int32_t w, int32_t h,
                                                     uint32_t *dst,
                                                     int32_t   dst_stride,
                                                     uint32_t  src);

extern "C" void pixman_composite_over_n_8888_asm_neon(int32_t w, int32_t h,
                                                      uint32_t *dst,
                                                      int32_t   dst_stride,
                                                      uint32_t  src);
#endif /* PIXMAN_NEON_NO_ASM */

void memfill32(uint32_t *dest, uint32_t value, int length)
{
    pixman_composite_src_n_8888_asm_neon(length, 1, dest, length, value);
}

void comp_func_solid_SourceOver_neon(uint32_t *dest, int length, uint32_t color,
                                     uint32_t const_alpha)
{
    if (const_alpha != 255) color = BYTE_MUL(color, const_alpha);

    pixman_composite_over_n_8888_asm_neon(length, 1, dest, length, color);
}
#endif
