package com.community.ai.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * 本地文本向量：字符 bigram 哈希到固定维度，序列化为 BLOB；余弦检索。
 * 无第三方依赖，兼容后续替换为真实 embedding 模型。
 */
public final class LocalTextEmbedder {

    public static final int DIM = 256;

    private LocalTextEmbedder() {}

    public static float[] embed(String text) {
        float[] v = new float[DIM];
        if (text == null || text.isBlank()) return v;
        String s = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (s.length() < 2) {
            v[Math.floorMod(s.hashCode(), DIM)] += 1f;
            return l2norm(v);
        }
        for (int i = 0; i < s.length() - 1; i++) {
            String bg = s.substring(i, i + 2);
            int h = Math.floorMod(bg.hashCode(), DIM);
            v[h] += 1f;
            if (i + 2 < s.length()) {
                String tg = s.substring(i, i + 3);
                int h2 = Math.floorMod(tg.hashCode() * 31, DIM);
                v[h2] += 0.5f;
            }
        }
        return l2norm(v);
    }

    public static byte[] toBlob(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) buf.putFloat(f);
        return buf.array();
    }

    public static float[] fromBlob(byte[] blob) {
        if (blob == null || blob.length < 4) return new float[DIM];
        int n = blob.length / 4;
        float[] v = new float[n];
        ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) v[i] = buf.getFloat();
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0;
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na <= 1e-12 || nb <= 1e-12) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static float[] l2norm(float[] v) {
        double s = 0;
        for (float f : v) s += f * f;
        if (s <= 1e-12) return v;
        float inv = (float) (1.0 / Math.sqrt(s));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
        return v;
    }
}
