package cn.linmumua.buildingwand.compat;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PreviewTextureTest {

    private static BufferedImage decode(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** 配置的透明度必须真正落在 1px 边框上，否则改 border-opacity 没有任何效果。 */
    @Test
    void 边框透明度等于配置值且中间全透明() throws Exception {
        BufferedImage img = decode(PreviewTexture.png(128));
        int size = PreviewTexture.SIZE;
        assertEquals(size, img.getWidth());
        assertEquals(size, img.getHeight());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = img.getRGB(x, y);
                int alpha = argb >>> 24;
                boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                if (edge) {
                    assertEquals(128, alpha, "边框像素 (" + x + "," + y + ") 透明度不对");
                    assertEquals(0xFFFFFF, argb & 0xFFFFFF, "边框应为白色");
                } else {
                    assertEquals(0, alpha, "内部像素 (" + x + "," + y + ") 应全透明");
                }
            }
        }
    }

    /**
     * 安装器按「字节不一致才写」判断改动：同一透明度的编码结果若不稳定，
     * 每次 /bw reload 都会重写贴图并提示重载 CE。
     */
    @Test
    void 同一透明度输出逐字节一致() {
        assertArrayEquals(PreviewTexture.png(200), PreviewTexture.png(200));
    }

    /** 不同透明度必须产生不同文件，否则安装器认为没变化、不会替换 CE 里的旧贴图。 */
    @Test
    void 透明度变化会产生不同贴图() {
        assertFalse(java.util.Arrays.equals(PreviewTexture.png(255), PreviewTexture.png(64)));
    }

    @Test
    void 越界的透明度被夹到0到255() throws Exception {
        assertEquals(255, decode(PreviewTexture.png(999)).getRGB(0, 0) >>> 24);
        assertEquals(0, decode(PreviewTexture.png(-5)).getRGB(0, 0) >>> 24);
    }
}
