package com.test.voip.service;

import com.test.voip.entity.BaseAudioRecord;
import com.test.voip.entity.CalleeAudio;
import com.test.voip.entity.FullCallAudio;
import com.test.voip.repository.CalleeAudioRepository;
import com.test.voip.repository.FullCallAudioRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
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

    private final FullCallAudioRepository fullCallAudioRepository;
    private final CalleeAudioRepository calleeAudioRepository;

    @Value("${recording.storage-path:./recordings}")
    private String storageLocationPath;

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

    public AudioServiceImpl(
            FullCallAudioRepository fullCallAudioRepository,
            CalleeAudioRepository calleeAudioRepository) {
        this.fullCallAudioRepository = fullCallAudioRepository;
        this.calleeAudioRepository = calleeAudioRepository;
    }

    @PostConstruct
    private void initStoragePath() {
        this.storageLocation = Paths.get(storageLocationPath).toAbsolutePath().normalize();
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

        boolean isCallee = "CALLEE".equalsIgnoreCase(audioType);

        if (!"FULL_CALL".equalsIgnoreCase(audioType)) {
            String specificKey = createKey(callId, audioType);
            RecordingSession specificSession = activeRecordings.get(specificKey);
            if (specificSession != null) {
                if (isCallee) {
                    specificSession.appendCallee(audioData);
                } else {
                    specificSession.appendCaller(audioData);
                }
            }
        }

        RecordingSession fullSession = activeRecordings.get(createKey(callId, "FULL_CALL"));
        if (fullSession != null) {
            if (isCallee) {
                fullSession.appendCallee(audioData);
            } else {
                fullSession.appendCaller(audioData);
            }
        }
    }

    @Override
    public BaseAudioRecord stopRecording(String callId, String audioType) {
        String key = createKey(callId, audioType);
        RecordingSession session = activeRecordings.remove(key);
        if (session == null) return null;

        try {
            byte[] rawAudio = session.getMixedAudio();
            if (rawAudio.length == 0) {
                long elapsedMillis = Math.max(20, Duration.between(session.getStartTime(), LocalDateTime.now()).toMillis());
                int sampleRate = (int) Math.max(1, session.getFormat().getSampleRate());
                int silenceBytes = Math.max(160, (int) ((elapsedMillis * sampleRate) / 1000));
                rawAudio = new byte[silenceBytes];
                Arrays.fill(rawAudio, (byte) 128);
                log.info("No audio captured for {}, padded with {} bytes silence", key, silenceBytes);
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

            BaseAudioRecord record = createEntityForType(audioType);
            populateRecord(record, callId, wavPath, session.getStartTime());
            record.setFileSizeBytes(Files.size(wavPath));
            record.setDurationSeconds(durationSeconds);
            record.setEndTime(LocalDateTime.now());

            BaseAudioRecord saved = saveRecord(record, audioType);
            log.info("Recording saved: {} | {} | ID: {}", key, wavPath, saved.getId());
            return saved;

        } catch (IOException e) {
            log.error("{} {}: {}", finalizeFailed, key, e.getMessage(), e);
            throw new RuntimeException(finalizeFailed + ": " + key, e);
        }
    }

    @Override
    public List<BaseAudioRecord> getAudioByCallId(String callId) {
        List<BaseAudioRecord> list = new ArrayList<>();
        list.addAll(fullCallAudioRepository.findByCallId(callId));
        list.addAll(calleeAudioRepository.findByCallId(callId));
        return list;
    }

    @Override
    public BaseAudioRecord getAudioById(Long id) {
        return fullCallAudioRepository.findById(id)
                .map(BaseAudioRecord.class::cast)
                .or(() -> calleeAudioRepository.findById(id).map(BaseAudioRecord.class::cast))
                .orElseThrow(() -> new RuntimeException(notFound + ": " + id));
    }

    @Override
    public BaseAudioRecord getAudioByIdAndType(Long id, String type) {
        if ("CALLEE".equalsIgnoreCase(type)) {
            return calleeAudioRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(notFound + ": " + id));
        }
        return fullCallAudioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(notFound + ": " + id));
    }

    @Override
    public byte[] getAudioBytesById(Long id) {
        return getAudioBytes(getAudioById(id));
    }

    @Override
    public byte[] getAudioBytes(BaseAudioRecord audio) {
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
        String safeCallId = callId.replaceAll("[^a-zA-Z0-9.-]", "_");
        String fileName = safeCallId + "_" + audioType.toLowerCase() + "_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".wav";
        return folder.resolve(fileName);
    }

    private BaseAudioRecord createEntityForType(String audioType) {
        if ("CALLEE".equalsIgnoreCase(audioType)) {
            return new CalleeAudio();
        }
        return new FullCallAudio();
    }

    private void populateRecord(BaseAudioRecord record, String callId, Path filePath, LocalDateTime startTime) {
        record.setCallId(callId);
        record.setFileName(filePath.getFileName().toString());
        record.setFilePath(filePath.toString());
        record.setStartTime(startTime);
    }

    private BaseAudioRecord saveRecord(BaseAudioRecord record, String audioType) {
        if ("CALLEE".equalsIgnoreCase(audioType)) {
            return calleeAudioRepository.save((CalleeAudio) record);
        }
        return fullCallAudioRepository.save((FullCallAudio) record);
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
        return "CALLEE".equalsIgnoreCase(audioType) ? "callee" : "full-call";
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
