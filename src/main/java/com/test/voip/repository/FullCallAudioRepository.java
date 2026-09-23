package com.test.voip.repository;

import com.test.voip.entity.FullCallAudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FullCallAudioRepository extends JpaRepository<FullCallAudio, Long> {
    List<FullCallAudio> findByCallId(String callId);
}
