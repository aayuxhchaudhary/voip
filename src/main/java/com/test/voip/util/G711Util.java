package com.test.voip.util;

/**
 * ITU-T G.711 u-law (PCMU) compander utility.
 * Converts linear PCM audio (8-bit or 16-bit) to 8-bit u-law samples and vice versa.
 */
public final class G711Util {

    private static final int BIAS = 0x84; // 132
    private static final int CLIP = 32635;

    private G711Util() {
    }

    /**
     * Converts a 16-bit signed linear PCM sample to an 8-bit u-law byte.
     */
    public static byte linearToMuLaw(short pcm) {
        int sample = pcm;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) {
            sample = -sample;
        }
        if (sample > CLIP) {
            sample = CLIP;
        }
        sample += BIAS;

        int exponent = 7;
        for (int expMask = 0x4000; (sample & expMask) == 0 && exponent > 0; expMask >>= 1) {
            exponent--;
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    /**
     * Converts unsigned 8-bit linear PCM (0..255, silence at 128) to 8-bit u-law.
     */
    public static byte[] unsigned8BitToMuLaw(byte[] unsignedPcm, int length) {
        byte[] ulaw = new byte[length];
        for (int i = 0; i < length; i++) {
            int u = unsignedPcm[i] & 0xFF;
            short s16 = (short) ((u - 128) << 8);
            ulaw[i] = linearToMuLaw(s16);
        }
        return ulaw;
    }

    /**
     * Converts 16-bit signed linear PCM (little-endian) to 8-bit u-law.
     */
    public static byte[] linear16ToMuLaw(byte[] pcm16, int length) {
        int sampleCount = length / 2;
        byte[] ulaw = new byte[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int low = pcm16[i * 2] & 0xFF;
            int high = pcm16[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            ulaw[i] = linearToMuLaw(sample);
        }
        return ulaw;
    }

    /**
     * Converts an 8-bit u-law byte back to a 16-bit signed linear PCM sample.
     */
    public static short muLawToLinear(byte ulawByte) {
        int ulaw = ~ulawByte & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int sample = ((mantissa << 3) + BIAS) << exponent;
        sample -= BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }

    /**
     * Converts an array of 8-bit u-law bytes back to unsigned 8-bit linear PCM (0..255, silence 128).
     */
    public static byte[] muLawToUnsigned8Bit(byte[] ulawBytes, int length) {
        byte[] pcm = new byte[length];
        for (int i = 0; i < length; i++) {
            short sample16 = muLawToLinear(ulawBytes[i]);
            int u = (sample16 >> 8) + 128;
            if (u < 0) u = 0;
            if (u > 255) u = 255;
            pcm[i] = (byte) u;
        }
        return pcm;
    }
}
