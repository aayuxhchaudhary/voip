package com.test.voip.service;

import com.test.voip.entity.BaseAudioRecord;

import java.util.List;

public interface AudioService {

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

    BaseAudioRecord stopRecording(
            String callId,
            String audioType
    );

    List<BaseAudioRecord> getAudioByCallId(String callId);

    BaseAudioRecord getAudioById(Long id);

    BaseAudioRecord getAudioByIdAndType(Long id, String type);

    byte[] getAudioBytesById(Long id);

    byte[] getAudioBytes(BaseAudioRecord audio);
}
