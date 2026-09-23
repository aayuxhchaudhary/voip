package com.test.voip.repository;

import com.test.voip.entity.CalleeAudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalleeAudioRepository extends JpaRepository<CalleeAudio, Long> {
    List<CalleeAudio> findByCallId(String callId);
}
