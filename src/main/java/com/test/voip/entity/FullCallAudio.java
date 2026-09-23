package com.test.voip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "full_call")
public class FullCallAudio extends BaseAudioRecord {
    public FullCallAudio() { }
}
