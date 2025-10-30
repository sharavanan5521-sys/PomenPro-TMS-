package com.example.pomenpro.models;

public class Job {
    public String id;           // filled from Firebase key
    public String assignedTo;
    public long   createdAt;
    public String createdBy;
    public String displayId;
    public String notes;
    public String serviceType;
    public String status;       // "pending" | "in_progress" | "completed"
    public String vehicleNo;

    public Job() { } // required by Firebase
}
