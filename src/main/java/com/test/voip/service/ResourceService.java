package com.test.voip.service;

import com.test.voip.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);
    private final CountryRepository countryRepository;

    // Track active audio clips per callId to support multiple concurrent calls safely
    private final Map<String, Clip> activeClips = new ConcurrentHashMap<>();

    public ResourceService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    public InputStream resourceMethod(String dialCode) {
        String path = countryRepository.getSongPath(dialCode);
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);

        if (in == null) {
            String defaultPath = countryRepository.getSongPath(countryRepository.getDefaultCountryCode());
            in = getClass().getClassLoader().getResourceAsStream(defaultPath);
        }
        return in;
    }

    public void playResourceSong(String callId, String dialCode) {
        stopSong(callId);

        String resolved = countryRepository.resolveDialCode(dialCode);
        log.info("Playing audio tone for dial code '{}' [callId={}]", resolved, callId);

        try {
            InputStream in = resourceMethod(dialCode);
            if (in == null) {
                log.warn("Audio file missing for dial code: {}", dialCode);
                return;
            }

            BufferedInputStream bufferedIn = new BufferedInputStream(in);
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(bufferedIn);

            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    activeClips.remove(callId);
                }
            });

            activeClips.put(callId, clip);
            clip.start();

        } catch (Exception e) {
            log.warn("Local speaker playback unavailable: {}", e.getMessage());
        }
    }

    public void stopSong(String callId) {
        if (callId == null) return;
        Clip clip = activeClips.remove(callId);
        if (clip != null) {
            try {
                clip.stop();
                clip.close();
                log.info("Stopped audio playback [callId={}]", callId);
            } catch (Exception e) {
                log.warn("Error stopping audio clip: {}", e.getMessage());
            }
        }
    }

    public void stopAll() {
        activeClips.forEach((id, clip) -> {
            try {
                clip.stop();
                clip.close();
            } catch (Exception ignored) {
            }
        });
        activeClips.clear();
    }
}
