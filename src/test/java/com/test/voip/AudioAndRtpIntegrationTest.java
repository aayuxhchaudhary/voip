package com.test.voip;

import com.test.voip.entity.BaseAudioRecord;
import com.test.voip.entity.CallDetail;
import com.test.voip.entity.CalleeAudio;
import com.test.voip.entity.FullCallAudio;
import com.test.voip.repository.CalleeAudioRepository;
import com.test.voip.repository.CallDetailRepository;
import com.test.voip.repository.CountryRepository;
import com.test.voip.repository.FullCallAudioRepository;
import com.test.voip.service.AudioService;
import com.test.voip.service.CallDetailService;
import com.test.voip.util.G711Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AudioAndRtpIntegrationTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private AudioService audioService;

    @Autowired
    private FullCallAudioRepository fullCallAudioRepository;

    @Autowired
    private CalleeAudioRepository calleeAudioRepository;

    @Autowired
    private CallDetailService callDetailService;

    @Autowired
    private CallDetailRepository callDetailRepository;

    @Test
    void testCountryDialCodeResolution_PrefixMatching() {
        assertEquals("+91", countryRepository.resolveDialCode("+919876543210"));
        assertEquals("+1", countryRepository.resolveDialCode("+14155552671"));
        assertEquals("+44", countryRepository.resolveDialCode("+447911123456"));
        assertEquals("+971", countryRepository.resolveDialCode("+971501234567"));

        assertEquals("+91", countryRepository.resolveDialCode("+999999999999"));
        assertEquals("+91", countryRepository.resolveDialCode(null));
        assertEquals("+91", countryRepository.resolveDialCode(""));

        assertEquals("+1", countryRepository.resolveDialCode("13864181000", "46573947a455"));
        assertEquals("+1", countryRepository.resolveDialCode("0014155552671"));
        assertEquals("+44", countryRepository.resolveDialCode("00447911123456"));
        assertEquals("+44", countryRepository.resolveDialCode("userA", "+447911123456"));
        assertEquals("+91", countryRepository.resolveDialCode("userA", "userB"));
    }

    @Test
    void testG711MuLawEncoding() {
        byte silence = G711Util.linearToMuLaw((short) 0);
        assertEquals((byte) 0xFF, silence);

        byte[] unsignedSilence = new byte[]{(byte) 128};
        byte[] ulaw = G711Util.unsigned8BitToMuLaw(unsignedSilence, 1);
        assertEquals((byte) 0xFF, ulaw[0]);

        byte[] bufferWithHeader = new byte[]{(byte) 0, (byte) 0, (byte) 0xFF, (byte) 0xFF};
        byte[] decoded = G711Util.muLawToUnsigned8Bit(bufferWithHeader, 2, 2);
        assertEquals(2, decoded.length);
        assertEquals((byte) 128, decoded[0]);
        assertEquals((byte) 128, decoded[1]);
    }

    @Test
    void testAudioRecordingLifecycleAndDatabasePersistence() {
        String testCallId = "call-unit-test-" + System.currentTimeMillis();

        audioService.startRecording(testCallId, "FULL_CALL", 8000, 1, 8, false, false);

        byte[] dummyAudio = new byte[320];
        for (int i = 0; i < dummyAudio.length; i++) {
            dummyAudio[i] = (byte) 128;
        }
        audioService.appendAudio(testCallId, "FULL_CALL", dummyAudio);

        BaseAudioRecord saved = audioService.stopRecording(testCallId, "FULL_CALL");
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals(testCallId, saved.getCallId());
        assertInstanceOf(FullCallAudio.class, saved);
        assertNotNull(saved.getFilePath());
        assertTrue(saved.getFileSizeBytes() > 0);

        List<FullCallAudio> dbRecords = fullCallAudioRepository.findByCallId(testCallId);
        assertFalse(dbRecords.isEmpty());
        assertEquals(saved.getId(), dbRecords.get(0).getId());

        byte[] readBytes = audioService.getAudioBytesById(saved.getId());
        assertNotNull(readBytes);
        assertTrue(readBytes.length > 0);
    }

    @Test
    void testDualRecording_FullCallAndCalleeSeparately() {
        String testCallId = "call-dual-test-" + System.currentTimeMillis();

        audioService.startRecording(testCallId, "FULL_CALL", 8000, 1, 8, false, false);
        audioService.startRecording(testCallId, "CALLEE", 8000, 1, 8, false, false);

        byte[] callerAudio = new byte[]{(byte) 140, (byte) 140};
        byte[] calleeAudio = new byte[]{(byte) 120, (byte) 120};

        audioService.appendAudio(testCallId, "CALLER", callerAudio);
        audioService.appendAudio(testCallId, "CALLEE", calleeAudio);

        BaseAudioRecord savedFull = audioService.stopRecording(testCallId, "FULL_CALL");
        BaseAudioRecord savedCallee = audioService.stopRecording(testCallId, "CALLEE");

        assertNotNull(savedFull);
        assertInstanceOf(FullCallAudio.class, savedFull);

        assertNotNull(savedCallee);
        assertInstanceOf(CalleeAudio.class, savedCallee);

        List<FullCallAudio> fullRecords = fullCallAudioRepository.findByCallId(testCallId);
        assertEquals(1, fullRecords.size());

        List<CalleeAudio> calleeRecords = calleeAudioRepository.findByCallId(testCallId);
        assertEquals(1, calleeRecords.size());

        List<BaseAudioRecord> allRecords = audioService.getAudioByCallId(testCallId);
        assertEquals(2, allRecords.size());

        BaseAudioRecord fetchedFull = audioService.getAudioByIdAndType(savedFull.getId(), "FULL_CALL");
        assertNotNull(fetchedFull);
        assertEquals(savedFull.getId(), fetchedFull.getId());

        BaseAudioRecord fetchedCallee = audioService.getAudioByIdAndType(savedCallee.getId(), "CALLEE");
        assertNotNull(fetchedCallee);
        assertEquals(savedCallee.getId(), fetchedCallee.getId());

        byte[] directBytes = audioService.getAudioBytes(savedFull);
        assertNotNull(directBytes);
        assertTrue(directBytes.length > 0);
    }

    @Test
    void testCallDetailLifecycleAndPersistence() throws Exception {
        String testCallId = "call-cdr-" + System.currentTimeMillis();
        String caller = "13864181000";
        String callee = "userB";

        CallDetail initial = callDetailService.recordDial(testCallId, caller, callee);
        assertNotNull(initial);
        assertEquals(testCallId, initial.getCallId());
        assertEquals(caller, initial.getSrcNumber());
        assertEquals(callee, initial.getDstNumber());
        assertEquals("CALLING", initial.getStatus());
        assertNotNull(initial.getDialTime());

        Thread.sleep(10);
        callDetailService.recordRinging(testCallId, 180, "Ringing");
        CallDetail ringing = callDetailService.getCallDetailByCallId(testCallId);
        assertEquals("RINGING", ringing.getStatus());
        assertNotNull(ringing.getPddMs());
        assertTrue(ringing.getPddMs() >= 0);

        Thread.sleep(10);
        callDetailService.recordAnswer(testCallId);
        CallDetail answered = callDetailService.getCallDetailByCallId(testCallId);
        assertEquals("ANSWERED", answered.getStatus());
        assertNotNull(answered.getAnswerTime());

        Thread.sleep(10);
        callDetailService.recordHangup(testCallId, "BYE");
        CallDetail ended = callDetailService.getCallDetailByCallId(testCallId);
        assertNotNull(ended.getEndTime());
        assertNotNull(ended.getDurationSeconds());
        assertTrue(ended.getDurationSeconds() >= 0.0);
        assertEquals("BYE", ended.getSipStatus());

        String fakeFullAudioPath = "/recordings/full-call/" + testCallId + ".wav";
        String fakeCalleeAudioPath = "/recordings/callee/" + testCallId + ".wav";
        callDetailService.recordMediaPaths(testCallId, fakeFullAudioPath, fakeCalleeAudioPath);

        CallDetail persisted = callDetailRepository.findByCallId(testCallId).orElseThrow();
        assertEquals(fakeFullAudioPath, persisted.getFullCallAudioPath());
        assertEquals(fakeCalleeAudioPath, persisted.getCalleeAudioPath());
        assertEquals("ANSWERED", persisted.getStatus());
        assertEquals("BYE", persisted.getSipStatus());
    }
}
