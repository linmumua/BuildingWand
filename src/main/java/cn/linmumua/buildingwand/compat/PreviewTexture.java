package cn.linmumua.buildingwand.compat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 预览方块贴图：64x64，四条边各 1px 白色，透明度可配，中间全透明。
 *
 * <p>自己编码 PNG 而不用 ImageIO：安装器按「字节不一致才写」判断是否需要重写，
 * 编码结果必须对同一个透明度永远逐字节相同，否则每次 reload 都会被当成改动。</p>
 */
public final class PreviewTexture {

    /** 贴图边长，与 tools/generate_ce_pack.py 的 PREVIEW_TEXTURE_SIZE 一致。 */
    public static final int SIZE = 64;

    private PreviewTexture() {
    }

    /** @param alpha 边框透明度，0 = 完全透明，255 = 完全不透明 */
    public static byte[] png(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        // 每行：1 字节过滤类型（0 = 无）+ SIZE 个 RGBA 像素
        byte[] raw = new byte[SIZE * (1 + SIZE * 4)];
        int p = 0;
        for (int y = 0; y < SIZE; y++) {
            raw[p++] = 0;
            for (int x = 0; x < SIZE; x++) {
                boolean edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
                byte v = edge ? (byte) 255 : 0;
                raw[p++] = v;
                raw[p++] = v;
                raw[p++] = v;
                raw[p++] = edge ? (byte) a : 0;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, SIZE);
        putInt(ihdr, 4, SIZE);
        ihdr[8] = 8;   // 位深
        ihdr[9] = 6;   // RGBA
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", deflate(raw));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                out.write(buf, 0, deflater.deflate(buf));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        putInt(len, 0, data.length);
        out.writeBytes(len);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] c = new byte[4];
        putInt(c, 0, (int) crc.getValue());
        out.writeBytes(c);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
