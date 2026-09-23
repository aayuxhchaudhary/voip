package com.test.voip.service;

import com.test.voip.repository.CountryRepository;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class MetaService {

    private final ResourceService resourceService;
    private final CountryRepository countryRepository;

    public MetaService(ResourceService resourceService, CountryRepository countryRepository) {
        this.resourceService = resourceService;
        this.countryRepository = countryRepository;
    }

    public Map<String, Object> getAudioMeta(String dialCode) {
        String resolved = countryRepository.resolveDialCode(dialCode);
        String path = countryRepository.getSongPath(resolved);

        Map<String, Object> meta = new HashMap<>();
        try (InputStream in = resourceService.getAudioStream(dialCode)) {
            if (in == null) return meta;

            try (BufferedInputStream bufferedIn = new BufferedInputStream(in);
                 AudioInputStream audioIn = AudioSystem.getAudioInputStream(bufferedIn)) {
                AudioFormat format = audioIn.getFormat();
                long frames = audioIn.getFrameLength();
                double durationSeconds = frames / (double) format.getFrameRate();

                meta.put("dialCode", resolved);
                meta.put("songFile", path);
                meta.put("sampleRate", format.getSampleRate());
                meta.put("channels", format.getChannels());
                meta.put("bitDepth", format.getSampleSizeInBits());
                meta.put("durationSeconds", durationSeconds);
            }
        } catch (Exception e) {
            meta.put("error", e.getMessage());
        }
        return meta;
    }
}
