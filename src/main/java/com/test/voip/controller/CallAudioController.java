package com.test.voip.controller;

import com.test.voip.entity.BaseAudioRecord;
import com.test.voip.service.AudioService;
import com.test.voip.service.MetaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audio")
public class CallAudioController {

    private final AudioService audioService;
    private final MetaService metaService;

    public CallAudioController(AudioService audioService, MetaService metaService) {
        this.audioService = audioService;
        this.metaService = metaService;
    }

    @GetMapping("/call/{callId}")
    public ResponseEntity<List<BaseAudioRecord>> getAudioByCallId(@PathVariable String callId) {
        return ResponseEntity.ok(audioService.getAudioByCallId(callId));
    }

    @GetMapping("/play/{id}")
    public ResponseEntity<byte[]> playAudio(@PathVariable Long id, @RequestParam(required = false) String type) {
        BaseAudioRecord audio = (type != null && !type.isBlank())
                ? audioService.getAudioByIdAndType(id, type)
                : audioService.getAudioById(id);
        byte[] audioBytes = audioService.getAudioBytes(audio);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + audio.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioBytes);
    }

    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> getSongMeta(@RequestParam String countryCode) {
        return ResponseEntity.ok(metaService.getAudioMeta(countryCode));
    }
}
