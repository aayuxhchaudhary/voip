package com.test.voip.repository;

import com.test.voip.entity.CallAudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallAudioRepository extends JpaRepository<CallAudio, Long> {
    List<CallAudio> findByCallId(String callId);
}
