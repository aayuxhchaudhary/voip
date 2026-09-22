package com.test.voip.service;

import com.test.voip.entity.CallAudio;
import com.test.voip.repository.CallAudioRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioServiceImpl implements AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioServiceImpl.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Set<String> VALID_AUDIO_TYPES = Set.of("CALLEE", "CALLER", "FULL_CALL", "RBT");
    private static final int STALE_THRESHOLD_MINUTES = 30;

    private final CallAudioRepository callAudioRepository;

    @Value("${recording.storage-path:./recordings}")
    private String storageLocationPath;

    @Value("${audio.error.save-failed:Failed to save uploaded audio}")
    private String saveFailed;

    @Value("${audio.error.finalize-failed:Failed to finalize recording}")
    private String finalizeFailed;

    @Value("${audio.error.not-found:Audio record not found}")
    private String notFound;

    @Value("${audio.error.file-missing:Audio file missing on disk}")
    private String fileMissing;

    @Value("${audio.error.read-failed:Failed to read audio file}")
    private String readFailed;

    @Value("${audio.error.invalid-type:Invalid audioType, must be: CALLEE, CALLER, FULL_CALL, or RBT}")
    private String invalidType;

    private Path storageLocation;
    private final Map<String, RecordingSession> activeRecordings = new ConcurrentHashMap<>();

    public AudioServiceImpl(CallAudioRepository callAudioRepository) {
        this.callAudioRepository = callAudioRepository;
    }

    @PostConstruct
    private void initStoragePath() {
        this.storageLocation = Paths.get(storageLocationPath).toAbsolutePath().normalize();
    }

    @Override
    public CallAudio saveUploadedRecording(
            String callId, String audioType, byte[] audioData,
            float sampleRate, int channels, int sampleSizeInBits,
            boolean signed, boolean bigEndian,
            LocalDateTime startTime, LocalDateTime endTime) {

        validateAudioType(audioType);

        try {
            Path filePath = resolveFilePath(callId, audioType);
            Files.write(filePath, audioData);

            CallAudio record = buildCallAudio(callId, audioType, filePath, startTime);
            record.setFileSizeBytes((long) audioData.length);
            record.setEndTime(endTime);

            if (startTime != null && endTime != null) {
                record.setDurationSeconds(Duration.between(startTime, endTime).toMillis() / 1000.0);
            }

            return callAudioRepository.save(record);
        } catch (IOException e) {
            log.error("{}: {}", saveFailed, e.getMessage(), e);
            throw new RuntimeException(saveFailed, e);
        }
    }

    @Override
    public void startRecording(
            String callId, String audioType,
            float sampleRate, int channels, int sampleSizeInBits,
            boolean signed, boolean bigEndian) {

        cleanupStaleRecordings();
        validateAudioType(audioType);

        String key = createKey(callId, audioType);
        if (activeRecordings.containsKey(key)) {
            log.warn("Recording already active for {}", key);
            return;
        }

        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        activeRecordings.put(key, new RecordingSession(format));
        log.info("Recording started: {}", key);
    }

    @Override
    public void appendAudio(String callId, String audioType, byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;

        String specificKey = createKey(callId, audioType);
        RecordingSession session = activeRecordings.get(specificKey);
        if (session != null) {
            session.appendCaller(audioData);
            return;
        }

        RecordingSession fullSession = activeRecordings.get(createKey(callId, "FULL_CALL"));
        if (fullSession != null) {
            if ("CALLEE".equalsIgnoreCase(audioType)) {
                fullSession.appendCallee(audioData);
            } else {
                fullSession.appendCaller(audioData);
            }
        }
    }

    @Override
    public CallAudio stopRecording(String callId, String audioType) {
        String key = createKey(callId, audioType);
        RecordingSession session = activeRecordings.remove(key);
        if (session == null) return null;

        try {
            byte[] rawAudio = session.getMixedAudio();
            if (rawAudio.length == 0) {
                log.warn("No audio captured for {}", key);
                return null;
            }

            Path wavPath = resolveFilePath(callId, audioType);
            AudioFormat format = session.getFormat();
            int frameSize = Math.max(1, format.getFrameSize());
            long totalFrames = rawAudio.length / frameSize;

            try (ByteArrayInputStream input = new ByteArrayInputStream(rawAudio);
                 AudioInputStream audioStream = new AudioInputStream(input, format, totalFrames)) {
                AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, wavPath.toFile());
            }

            double durationSeconds = totalFrames / (double) Math.max(1, (int) format.getSampleRate());

            CallAudio record = buildCallAudio(callId, audioType, wavPath, session.getStartTime());
            record.setFileSizeBytes(Files.size(wavPath));
            record.setDurationSeconds(durationSeconds);
            record.setEndTime(LocalDateTime.now());

            CallAudio saved = callAudioRepository.save(record);
            log.info("Recording saved: {} | {} | ID: {}", key, wavPath, saved.getId());
            return saved;

        } catch (IOException e) {
            log.error("{} {}: {}", finalizeFailed, key, e.getMessage(), e);
            throw new RuntimeException(finalizeFailed + ": " + key, e);
        }
    }

    @Override
    public List<CallAudio> getAudioByCallId(String callId) {
        return callAudioRepository.findByCallId(callId);
    }

    @Override
    public CallAudio getAudioById(Long id) {
        return callAudioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(notFound + ": " + id));
    }

    @Override
    public byte[] getAudioBytesById(Long id) {
        CallAudio audio = getAudioById(id);
        try {
            Path path = Paths.get(audio.getFilePath());
            if (!Files.exists(path)) {
                throw new RuntimeException(fileMissing + ": " + audio.getFilePath());
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(readFailed + ": " + audio.getFilePath(), e);
        }
    }

    private Path resolveFilePath(String callId, String audioType) throws IOException {
        String folderName = getFolderName(audioType);
        Path folder = storageLocation.resolve(folderName);
        Files.createDirectories(folder);
        String fileName = callId + "_" + audioType.toLowerCase() + "_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".wav";
        return folder.resolve(fileName);
    }

    private CallAudio buildCallAudio(String callId, String audioType, Path filePath, LocalDateTime startTime) {
        CallAudio record = new CallAudio();
        record.setCallId(callId);
        record.setAudioType(audioType.toUpperCase());
        record.setFileName(filePath.getFileName().toString());
        record.setFilePath(filePath.toString());
        record.setStartTime(startTime);
        return record;
    }

    private String createKey(String callId, String audioType) {
        return callId + "_" + audioType.toUpperCase();
    }

    private void validateAudioType(String audioType) {
        if (!VALID_AUDIO_TYPES.contains(audioType.toUpperCase())) {
            throw new IllegalArgumentException(invalidType);
        }
    }

    private String getFolderName(String audioType) {
        return switch (audioType.toUpperCase()) {
            case "CALLEE" -> "callee";
            case "CALLER" -> "caller";
            case "FULL_CALL" -> "full-call";
            case "RBT" -> "rbt";
            default -> "other";
        };
    }

    private void cleanupStaleRecordings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        activeRecordings.entrySet().removeIf(e -> e.getValue().getStartTime().isBefore(cutoff));
    }

    private static class RecordingSession {
        private final AudioFormat format;
        private final LocalDateTime startTime;
        private final ByteArrayOutputStream callerBuffer = new ByteArrayOutputStream();
        private final ByteArrayOutputStream calleeBuffer = new ByteArrayOutputStream();

        RecordingSession(AudioFormat format) {
            this.format = format;
            this.startTime = LocalDateTime.now();
        }

        synchronized void appendCaller(byte[] data) {
            callerBuffer.write(data, 0, data.length);
        }

        synchronized void appendCallee(byte[] data) {
            calleeBuffer.write(data, 0, data.length);
        }

        synchronized byte[] getMixedAudio() {
            byte[] c1 = callerBuffer.toByteArray();
            byte[] c2 = calleeBuffer.toByteArray();

            if (c1.length == 0) return c2;
            if (c2.length == 0) return c1;

            int len = Math.max(c1.length, c2.length);
            byte[] mixed = new byte[len];
            for (int i = 0; i < len; i++) {
                int s1 = (i < c1.length ? (c1[i] & 0xFF) : 128) - 128;
                int s2 = (i < c2.length ? (c2[i] & 0xFF) : 128) - 128;
                mixed[i] = (byte) (Math.max(-128, Math.min(127, s1 + s2)) + 128);
            }
            return mixed;
        }

        AudioFormat getFormat() { return format; }
        LocalDateTime getStartTime() { return startTime; }
    }
}
