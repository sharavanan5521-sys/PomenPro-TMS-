package com.example.pomenpro;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pomenpro.adapters.JobAdapter;
import com.example.pomenpro.models.Job;
import com.example.pomenpro.models.JobSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.*;

public class TaskActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference root;

    private RecyclerView rv;
    private JobAdapter adapter;
    private ImageView btnQr;

    private String myUid;
    private ValueEventListener jobsListener;
    private Query jobsQuery;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    toast("Scan cancelled.");
                    return;
                }
                // Debug lines are fine while testing:
                System.out.println("DEBUG_MY_UID: [" + myUid + "]");
                System.out.println("DEBUG_SCANNED: [" + result.getContents() + "]");

                if (!myUid.equals(result.getContents())) {
                    toast("QR does not match your ID.");
                    return;
                }
                Job selected = adapter.getSelected();
                if (selected == null) {
                    toast("Select a task first.");
                    return;
                }
                toggleTimer(selected);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            toast("Not logged in.");
            finish();
            return;
        }
        myUid = auth.getCurrentUser().getUid();
        root = FirebaseDatabase.getInstance().getReference();

        rv = findViewById(R.id.taskRecycleView);
        btnQr = findViewById(R.id.btnQr);

        adapter = new JobAdapter(this, (j, p) -> {});
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        subscribeJobs();

        btnQr.setOnClickListener(v -> {
            if (adapter.getSelected() == null) {
                toast("Select a task first.");
                return;
            }
            ScanOptions opts = new ScanOptions()
                    .setPrompt("Scan your technician QR ID")
                    .setBeepEnabled(true)
                    .setOrientationLocked(true);
            qrLauncher.launch(opts);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your top bar color
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your bottom bar color
        }
    }

    private void subscribeJobs() {
        jobsQuery = root.child("Jobs").orderByChild("assignedTo").equalTo(myUid);
        jobsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                List<Job> list = new ArrayList<>();
                for (DataSnapshot d : snap.getChildren()) {
                    Job j = d.getValue(Job.class);
                    if (j != null) j.id = d.getKey();
                    list.add(j);
                }
                adapter.setItems(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                toast("Failed to load jobs: " + error.getMessage());
            }
        };
        jobsQuery.addValueEventListener(jobsListener);
    }

    // After scan, navigate to TimerActivity. Create session if none exists.
    private void toggleTimer(Job job) {
        DatabaseReference runPtr = root.child("runningJobSessions").child(job.id).child(myUid);
        runPtr.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot ds) {
                if (ds.exists()) {
                    // Already running: open timer to manage it
                    String sessionId = String.valueOf(ds.getValue());
                    openTimer(job.id, sessionId, job.displayId, job.serviceType, job.vehicleNo);
                } else {
                    // Not running: create a new session and go
                    startSession(job, runPtr);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                toast("Toggle failed: " + error.getMessage());
            }
        });
    }

    // Create session with extra fields and navigate to TimerActivity
    private void startSession(Job job, DatabaseReference runPtr) {
        long now = System.currentTimeMillis();
        DatabaseReference sessionRef = root.child("jobSessions").child(job.id).push();

        Map<String, Object> session = new HashMap<>();
        session.put("technicianId", myUid);
        session.put("startTime", now);
        session.put("lastStart", now);        // current run start
        session.put("accumulatedMs", 0L);     // accumulated time
        session.put("state", "running");      // running|paused|done

        sessionRef.setValue(session).addOnSuccessListener(a -> {
            runPtr.setValue(sessionRef.getKey());
            root.child("Jobs").child(job.id).child("status").setValue("in_progress");
            toast("Timer started.");
            openTimer(job.id, sessionRef.getKey(), job.displayId, job.serviceType, job.vehicleNo);
        }).addOnFailureListener(e -> toast("Failed to start: " + e.getMessage()));
    }

    // Optional legacy stop; TimerActivity handles completion now.
    private void stopSession(Job job, String sessionId, DatabaseReference runPtr) {
        DatabaseReference sessionRef = root.child("jobSessions").child(job.id).child(sessionId);
        sessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot ds) {
                JobSession s = ds.getValue(JobSession.class);
                if (s == null || s.startTime == 0L) {
                    toast("Invalid session.");
                    return;
                }
                long now = System.currentTimeMillis();
                long duration = Math.max(0, now - s.startTime);

                Map<String, Object> upd = new HashMap<>();
                upd.put("endTime", now);
                upd.put("durationMs", duration);

                sessionRef.updateChildren(upd).addOnSuccessListener(a -> {
                    runPtr.removeValue();
                    root.child("Jobs").child(job.id).child("status").setValue("completed");
                    bumpPerformance(duration);
                    toast("Timer stopped: " + format(duration));
                }).addOnFailureListener(e -> toast("Failed to stop: " + e.getMessage()));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                toast("Read failed: " + error.getMessage());
            }
        });
    }

    // NEW: helper to launch TimerActivity with context
    private void openTimer(String jobId, String sessionId, String displayId, String serviceType, String vehicleNo) {
        Intent i = new Intent(this, TimerActivity.class);
        i.putExtra("jobId", jobId);
        i.putExtra("sessionId", sessionId);
        if (displayId != null)   i.putExtra("displayId", displayId);
        if (serviceType != null) i.putExtra("serviceType", serviceType);
        if (vehicleNo != null)   i.putExtra("vehicleNo", vehicleNo);
        startActivity(i);
    }

    private void bumpPerformance(long durationMs) {
        DatabaseReference stats = root.child("technicianStats").child(myUid);
        stats.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData cur) {
                Long jobs = cur.child("jobsCompleted").getValue(Long.class);
                Long total = cur.child("totalDurationMs").getValue(Long.class);
                long j = jobs == null ? 0 : jobs;
                long t = total == null ? 0 : total;
                cur.child("jobsCompleted").setValue(j + 1);
                cur.child("totalDurationMs").setValue(t + durationMs);
                cur.child("avgDurationMs").setValue((t + durationMs) / (j + 1));
                return Transaction.success(cur);
            }

            @Override
            public void onComplete(DatabaseError e, boolean committed, DataSnapshot s) { }
        });
    }

    private String format(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (jobsQuery != null && jobsListener != null)
            jobsQuery.removeEventListener(jobsListener);
    }
}
