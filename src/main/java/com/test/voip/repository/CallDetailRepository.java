package com.test.voip.repository;

import com.test.voip.entity.CallDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallDetailRepository extends JpaRepository<CallDetail, Long> {

    Optional<CallDetail> findByCallId(String callId);

    List<CallDetail> findAllByOrderByDialTimeDesc();
}
