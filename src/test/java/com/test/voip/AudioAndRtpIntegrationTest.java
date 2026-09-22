package com.test.voip;

import com.test.voip.entity.CallAudio;
import com.test.voip.repository.CallAudioRepository;
import com.test.voip.repository.CountryRepository;
import com.test.voip.service.AudioService;
import com.test.voip.util.G711Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AudioAndRtpIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private AudioService audioService;

    @Autowired
    private CallAudioRepository callAudioRepository;

    @Test
    void testCountryDialCodeResolution_PrefixMatching() {
        // Full international phone numbers should resolve to country dial codes
        assertEquals("+91", countryRepository.resolveDialCode("+919876543210"));
        assertEquals("+1", countryRepository.resolveDialCode("+14155552671"));
        assertEquals("+44", countryRepository.resolveDialCode("+447911123456"));
        assertEquals("+971", countryRepository.resolveDialCode("+971501234567"));

        // Unknown numbers fallback to default (+91)
        assertEquals("+91", countryRepository.resolveDialCode("+999999999999"));
        assertEquals("+91", countryRepository.resolveDialCode(null));
        assertEquals("+91", countryRepository.resolveDialCode(""));
    }

    @Test
    void testG711MuLawEncoding() {
        // In G.711 u-law, 0 linear PCM sample maps to 0xFF (silence)
        byte silence = G711Util.linearToMuLaw((short) 0);
        assertEquals((byte) 0xFF, silence);

        // 8-bit unsigned PCM silence is 128, which should also map to 0xFF in u-law
        byte[] unsignedSilence = new byte[]{(byte) 128};
        byte[] ulaw = G711Util.unsigned8BitToMuLaw(unsignedSilence, 1);
        assertEquals((byte) 0xFF, ulaw[0]);
    }

    @Test
    void testAudioRecordingLifecycleAndDatabasePersistence() {
        String testCallId = "call-unit-test-" + System.currentTimeMillis();

        // 1. Start recording
        audioService.startRecording(testCallId, "FULL_CALL", 8000, 1, 8, false, false);

        // 2. Append simulated audio packets
        byte[] dummyAudio = new byte[320];
        for (int i = 0; i < dummyAudio.length; i++) {
            dummyAudio[i] = (byte) 128; // PCM silence
        }
        audioService.appendAudio(testCallId, "FULL_CALL", dummyAudio);

        // 3. Stop recording -> writes WAV to disk and metadata to DB
        CallAudio saved = audioService.stopRecording(testCallId, "FULL_CALL");
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals(testCallId, saved.getCallId());
        assertEquals("FULL_CALL", saved.getAudioType());
        assertNotNull(saved.getFilePath());
        assertTrue(saved.getFileSizeBytes() > 0);

        // 4. Verify in DB
        List<CallAudio> dbRecords = callAudioRepository.findByCallId(testCallId);
        assertFalse(dbRecords.isEmpty());
        assertEquals(saved.getId(), dbRecords.get(0).getId());

        // 5. Verify audio bytes can be read back from disk
        byte[] readBytes = audioService.getAudioBytesById(saved.getId());
        assertNotNull(readBytes);
        assertTrue(readBytes.length > 0);
    }
}
