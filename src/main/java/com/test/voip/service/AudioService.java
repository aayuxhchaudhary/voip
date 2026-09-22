package com.test.voip.service;

import com.test.voip.entity.CallAudio;

import java.time.LocalDateTime;
import java.util.List;

public interface AudioService {

    CallAudio saveUploadedRecording(
            String callId,
            String audioType,
            byte[] audioData,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    void startRecording(
            String callId,
            String audioType,
            float sampleRate,
            int channels,
            int sampleSizeInBits,
            boolean signed,
            boolean bigEndian
    );

    void appendAudio(
            String callId,
            String audioType,
            byte[] audioData
    );

    CallAudio stopRecording(
            String callId,
            String audioType
    );

    List<CallAudio> getAudioByCallId(String callId);

    CallAudio getAudioById(Long id);

    byte[] getAudioBytesById(Long id);
}
