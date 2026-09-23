package com.test.voip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "callee")
public class CalleeAudio extends BaseAudioRecord {
    public CalleeAudio() { }
}
