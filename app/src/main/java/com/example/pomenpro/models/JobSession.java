package com.example.pomenpro.models;

public class JobSession {
    public String technicianId;
    public long startTime;   // epoch ms
    public Long endTime;     // null while running
    public Long durationMs;  // set when stopped

    public JobSession() { }

    public JobSession(String uid, long startTime) {
        this.technicianId = uid;
        this.startTime = startTime;
    }
}
