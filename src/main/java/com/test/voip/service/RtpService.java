package com.test.voip.service;

import com.test.voip.repository.CountryRepository;
import com.test.voip.util.G711Util;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RtpService {

    private static final Logger log = LoggerFactory.getLogger(RtpService.class);

    private static final int RTP_HEADER_SIZE = 12;
    private static final int PAYLOAD_SIZE = 160;
    private static final int RTP_PAYLOAD_TYPE_PCMU = 0;
    private static final int PTIME_MS = 20;

    private static final byte[] SILENCE_ULAW;
    private static final byte[] SILENCE_PCM;

    static {
        SILENCE_ULAW = new byte[PAYLOAD_SIZE];
        Arrays.fill(SILENCE_ULAW, (byte) 0xFF);
        SILENCE_PCM = new byte[PAYLOAD_SIZE];
        Arrays.fill(SILENCE_PCM, (byte) 128);
    }

    private final ResourceService resourceService;
    private final CountryRepository countryRepository;
    private final AudioService audioService;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
    private final Map<String, AtomicBoolean> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, DatagramSocket> activeReceivers = new ConcurrentHashMap<>();

    public RtpService(ResourceService resourceService, CountryRepository countryRepository, AudioService audioService) {
        this.resourceService = resourceService;
        this.countryRepository = countryRepository;
        this.audioService = audioService;
    }

    public String startRtpStream(String callId, String dialCode, String destIp, int destPort) {
        String sessionKey = (callId != null && !callId.isBlank()) ? callId : UUID.randomUUID().toString();
        stopRtpStreamOnly(sessionKey);

        AtomicBoolean running = new AtomicBoolean(true);
        activeStreams.put(sessionKey, running);

        String resolved = countryRepository.resolveDialCode(dialCode);
        log.info("RTP stream started [{}] -> {}:{} ({})", sessionKey, destIp, destPort, resolved);

        executor.submit(() -> streamAudio(sessionKey, dialCode, resolved, destIp, destPort, running));
        return sessionKey;
    }

    public void startRtpReceiver(String callId, int localPort) {
        if (callId == null || callId.isBlank()) return;
        stopReceiver(callId);

        activeReceivers.values().forEach(s -> {
            try { if (!s.isClosed()) s.close(); } catch (Exception ignored) { }
        });
        activeReceivers.clear();

        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket(localPort)) {
                activeReceivers.put(callId, socket);
                log.info("RTP receiver listening on port {} [{}]", localPort, callId);

                byte[] buf = new byte[1500];
                int packetCount = 0;

                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    if (packet.getLength() <= RTP_HEADER_SIZE) continue;

                    int offset = packet.getOffset();
                    if ((buf[offset] & 0xC0) != 0x80) continue;

                    int payloadLen = packet.getLength() - RTP_HEADER_SIZE;
                    byte[] ulawPayload = Arrays.copyOfRange(buf, offset + RTP_HEADER_SIZE, offset + packet.getLength());
                    audioService.appendAudio(callId, "CALLEE", G711Util.muLawToUnsigned8Bit(ulawPayload, payloadLen));

                    packetCount++;
                    if (packetCount == 1 || packetCount % 50 == 0) {
                        log.info("Captured {} callee packets [{}]", packetCount, callId);
                    }
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || !msg.toLowerCase().contains("socket closed")) {
                    log.warn("RTP receiver stopped on port {}: {}", localPort, msg);
                }
            } finally {
                activeReceivers.remove(callId);
            }
        });
    }

    public boolean stopRtp(String sessionKey) {
        if (sessionKey == null) return false;
        return stopRtpStreamOnly(sessionKey) | stopReceiver(sessionKey);
    }

    public boolean isStreaming(String sessionKey) {
        AtomicBoolean running = activeStreams.get(sessionKey);
        return running != null && running.get();
    }

    public void stopAll() {
        activeStreams.forEach((id, running) -> running.set(false));
        activeStreams.clear();
        activeReceivers.forEach((id, socket) -> {
            try { socket.close(); } catch (Exception ignored) { }
        });
        activeReceivers.clear();
    }

    @PreDestroy
    public void shutdown() {
        stopAll();
        executor.shutdownNow();
    }

    private boolean stopRtpStreamOnly(String sessionKey) {
        AtomicBoolean running = activeStreams.remove(sessionKey);
        if (running != null) {
            running.set(false);
            log.info("Stopped RTP stream [{}]", sessionKey);
            return true;
        }
        return false;
    }

    private boolean stopReceiver(String sessionKey) {
        DatagramSocket socket = activeReceivers.remove(sessionKey);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                log.info("Closed RTP receiver [{}]", sessionKey);
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void streamAudio(String sessionKey, String dialCode, String resolved,
                             String destIp, int destPort, AtomicBoolean running) {
        try (InputStream songStream = resourceService.getAudioStream(dialCode)) {
            if (songStream == null) {
                log.warn("No audio asset for dial code: {}", dialCode);
                activeStreams.remove(sessionKey);
                return;
            }

            try (BufferedInputStream bufferedIn = new BufferedInputStream(songStream);
                 AudioInputStream audioIn = AudioSystem.getAudioInputStream(bufferedIn);
                 DatagramSocket socket = new DatagramSocket()) {

                AudioFormat format = audioIn.getFormat();
                boolean is16Bit = format.getSampleSizeInBits() == 16;
                InetAddress destAddress = InetAddress.getByName(destIp);

                byte[] allRaw = audioIn.readAllBytes();
                int minBytes = is16Bit ? PAYLOAD_SIZE * 2 : PAYLOAD_SIZE;

                int ssrc = ThreadLocalRandom.current().nextInt();
                int seq = ThreadLocalRandom.current().nextInt(0, 32768);
                long ts = ThreadLocalRandom.current().nextLong(0, 1_000_000);

                if (allRaw.length < minBytes) {
                    while (running.get()) {
                        audioService.appendAudio(sessionKey, "CALLER", SILENCE_PCM);
                        sendRtpPacket(socket, destAddress, destPort, SILENCE_ULAW, seq, ts, ssrc);
                        seq = (seq + 1) & 0xFFFF;
                        ts += SILENCE_ULAW.length;
                        Thread.sleep(PTIME_MS);
                    }
                    return;
                }

                byte[] fullUlaw = is16Bit
                        ? G711Util.linear16ToMuLaw(allRaw, allRaw.length)
                        : G711Util.unsigned8BitToMuLaw(allRaw, allRaw.length);

                byte[] fullPcm = is16Bit
                        ? G711Util.muLawToUnsigned8Bit(fullUlaw, fullUlaw.length)
                        : allRaw;

                int offset = 0;
                while (running.get()) {
                    if (offset + PAYLOAD_SIZE > fullUlaw.length) {
                        offset = 0;
                    }

                    byte[] ulawPayload = Arrays.copyOfRange(fullUlaw, offset, offset + PAYLOAD_SIZE);
                    byte[] recordBytes = Arrays.copyOfRange(fullPcm, offset, offset + PAYLOAD_SIZE);

                    audioService.appendAudio(sessionKey, "CALLER", recordBytes);
                    sendRtpPacket(socket, destAddress, destPort, ulawPayload, seq, ts, ssrc);

                    seq = (seq + 1) & 0xFFFF;
                    ts += PAYLOAD_SIZE;
                    offset += PAYLOAD_SIZE;

                    Thread.sleep(PTIME_MS);
                }

                log.info("RTP stream completed [{}] ({})", sessionKey, resolved);
            }
        } catch (Exception e) {
            log.error("RTP stream error [{}]: {}", sessionKey, e.getMessage());
        } finally {
            activeStreams.remove(sessionKey);
        }
    }

    private void sendRtpPacket(DatagramSocket socket, InetAddress dest, int port,
                               byte[] payload, int seq, long ts, int ssrc) throws java.io.IOException {
        byte[] packet = buildRtpPacket(payload, payload.length, seq, ts, ssrc);
        socket.send(new DatagramPacket(packet, packet.length, dest, port));
    }

    private byte[] buildRtpPacket(byte[] payload, int payloadLength, int seqNum, long timestamp, int ssrc) {
        byte[] packet = new byte[RTP_HEADER_SIZE + payloadLength];
        packet[0] = (byte) 0x80;
        packet[1] = (byte) RTP_PAYLOAD_TYPE_PCMU;
        packet[2] = (byte) (seqNum >> 8);
        packet[3] = (byte) seqNum;
        packet[4] = (byte) (timestamp >> 24);
        packet[5] = (byte) (timestamp >> 16);
        packet[6] = (byte) (timestamp >> 8);
        packet[7] = (byte) timestamp;
        packet[8] = (byte) (ssrc >> 24);
        packet[9] = (byte) (ssrc >> 16);
        packet[10] = (byte) (ssrc >> 8);
        packet[11] = (byte) ssrc;
        System.arraycopy(payload, 0, packet, RTP_HEADER_SIZE, payloadLength);
        return packet;
    }
}
