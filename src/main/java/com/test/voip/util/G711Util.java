package com.test.voip.util;

public final class G711Util {

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    private G711Util() { }

    public static byte linearToMuLaw(short pcm) {
        int sample = pcm;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;

        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; mask >>= 1) {
            exponent--;
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    public static byte[] unsigned8BitToMuLaw(byte[] unsignedPcm, int length) {
        byte[] ulaw = new byte[length];
        for (int i = 0; i < length; i++) {
            short s16 = (short) (((unsignedPcm[i] & 0xFF) - 128) << 8);
            ulaw[i] = linearToMuLaw(s16);
        }
        return ulaw;
    }

    public static byte[] linear16ToMuLaw(byte[] pcm16, int length) {
        int sampleCount = length / 2;
        byte[] ulaw = new byte[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int low = pcm16[i * 2] & 0xFF;
            int high = pcm16[i * 2 + 1];
            ulaw[i] = linearToMuLaw((short) ((high << 8) | low));
        }
        return ulaw;
    }

    public static short muLawToLinear(byte ulawByte) {
        int ulaw = ~ulawByte & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int sample = ((mantissa << 3) + BIAS) << exponent;
        sample -= BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }

    public static byte[] muLawToUnsigned8Bit(byte[] ulawBytes, int length) {
        byte[] pcm = new byte[length];
        for (int i = 0; i < length; i++) {
            int u = (muLawToLinear(ulawBytes[i]) >> 8) + 128;
            pcm[i] = (byte) Math.max(0, Math.min(255, u));
        }
        return pcm;
    }
}
