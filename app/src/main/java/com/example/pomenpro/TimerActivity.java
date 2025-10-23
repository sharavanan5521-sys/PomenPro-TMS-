package com.example.pomenpro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TimerActivity extends AppCompatActivity {

    private TextView tvTimer;                 // timerCount in your XML
    private Button btnStart, btnPause, btnHold, btnComplete;

    private String myUid;
    private String jobId, sessionId;

    private DatabaseReference root, sessionRef, jobRef, runPtrRef;
    private ValueEventListener sessionListener;

    // Session state
    private String state = "paused";          // running | paused | done
    private long startTime = 0L;              // first ever start
    private Long lastStart = null;            // current run start
    private long accumulatedMs = 0L;          // sum of prior runs
    private Long endTime = null;              // when completed

    // UI ticker
    private final Handler tick = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long elapsed = accumulatedMs;
            if ("running".equals(state) && lastStart != null) {
                elapsed += Math.max(0, System.currentTimeMillis() - lastStart);
            }
            tvTimer.setText(format(elapsed));
            tick.postDelayed(this, 1000);
        }
    };

    // QR launcher to confirm completion
    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    toast("Scan cancelled.");
                    return;
                }
                if (!myUid.equals(result.getContents())) {
                    toast("QR does not match your ID.");
                    return;
                }
                finalizeCompletion();  // only after successful QR match
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_timer);

        tvTimer      = findViewById(R.id.timerCount);
        btnStart     = findViewById(R.id.btnStart);
        btnPause     = findViewById(R.id.btnPause);
        btnHold      = findViewById(R.id.btnOnHold);
        btnComplete  = findViewById(R.id.btnJobCompleted);

        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) { toast("Not logged in."); finish(); return; }

        Intent i = getIntent();
        jobId     = i.getStringExtra("jobId");
        sessionId = i.getStringExtra("sessionId");
        if (jobId == null || sessionId == null) { toast("Missing session context."); finish(); return; }

        root       = FirebaseDatabase.getInstance().getReference();
        sessionRef = root.child("jobSessions").child(jobId).child(sessionId);
        jobRef     = root.child("Jobs").child(jobId);
        runPtrRef  = root.child("runningJobSessions").child(jobId).child(myUid);

        // Live session listener
        sessionListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot ds) {
                // tolerate older sessions that might not have new fields
                String s = ds.child("state").getValue(String.class);
                state = s == null ? "running" : s;

                Long st = ds.child("startTime").getValue(Long.class);
                startTime = st == null ? 0L : st;

                Long acc = ds.child("accumulatedMs").getValue(Long.class);
                accumulatedMs = acc == null ? 0L : acc;

                lastStart = ds.hasChild("lastStart")
                        ? ds.child("lastStart").getValue(Long.class)
                        : null;

                endTime = ds.hasChild("endTime")
                        ? ds.child("endTime").getValue(Long.class)
                        : null;

                if (endTime != null) {
                    Long dur = ds.child("durationMs").getValue(Long.class);
                    tvTimer.setText(format(dur == null ? accumulatedMs : dur));
                    updateButtonsForState();   // disable everything on completion
                    toast("Session completed.");
                    finish();
                    return;
                }

                updateButtonsForState();
            }
            @Override public void onCancelled(DatabaseError error) {
                toast("Session load failed: " + error.getMessage());
            }
        };
        sessionRef.addValueEventListener(sessionListener);

        // Buttons
        btnStart.setOnClickListener(v -> resumeRun());
        btnPause.setOnClickListener(v -> pauseRun(false));
        btnHold.setOnClickListener(v -> pauseRun(true));
        btnComplete.setOnClickListener(v -> launchCompleteQr());
    }

    @Override protected void onResume() { super.onResume(); tick.post(ticker); }
    @Override protected void onPause()  { super.onPause();  tick.removeCallbacks(ticker); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionListener != null) sessionRef.removeEventListener(sessionListener);
        tick.removeCallbacks(ticker);
    }

    private void updateButtonsForState() {
        boolean running = "running".equals(state);
        boolean done    = "done".equals(state) || endTime != null;

        btnStart.setEnabled(!running && !done);
        btnPause.setEnabled(running && !done);
        btnHold.setEnabled(running && !done);
        btnComplete.setEnabled(!done);   // allow complete from paused or running
    }

    // Resume/start button
    private void resumeRun() {
        if ("running".equals(state)) { toast("Already running."); return; }
        long now = System.currentTimeMillis();
        Map<String,Object> upd = new HashMap<>();
        upd.put("state", "running");
        upd.put("lastStart", now);
        if (startTime == 0L) upd.put("startTime", now); // safety for legacy sessions

        sessionRef.updateChildren(upd).addOnSuccessListener(a -> {
            jobRef.child("status").setValue("in_progress");
            runPtrRef.setValue(sessionId);
        }).addOnFailureListener(e -> toast("Resume failed: " + e.getMessage()));
    }

    // Pause or Hold
    private void pauseRun(boolean markHold) {
        if (!"running".equals(state) || lastStart == null) { toast("Not running."); return; }
        long now = System.currentTimeMillis();
        long add = Math.max(0, now - lastStart);

        Map<String,Object> upd = new HashMap<>();
        upd.put("state", "paused");
        upd.put("accumulatedMs", accumulatedMs + add);
        upd.put("lastStart", null);

        final boolean hold = markHold; // capture for lambda

        sessionRef.updateChildren(upd).addOnSuccessListener(a -> {
            if (hold) jobRef.child("status").setValue("on_hold");
            toast(hold ? "On hold." : "Paused.");
        }).addOnFailureListener(e -> toast("Pause failed: " + e.getMessage()));
    }

    // Complete: first require QR confirmation
    private void launchCompleteQr() {
        ScanOptions opts = new ScanOptions()
                .setPrompt("Scan your technician QR ID to complete")
                .setBeepEnabled(true)
                .setOrientationLocked(true);
        qrLauncher.launch(opts);
    }

    // After QR confirms, finalize session
    private void finalizeCompletion() {
        long now = System.currentTimeMillis();

        long extra = 0L;
        if ("running".equals(state) && lastStart != null) {
            extra = Math.max(0, now - lastStart);
        }
        final long totalFinal = accumulatedMs + extra;  // <= effectively final

        Map<String,Object> upd = new HashMap<>();
        upd.put("endTime", now);
        upd.put("durationMs", totalFinal);
        upd.put("state", "done");
        upd.put("lastStart", null);
        upd.put("accumulatedMs", totalFinal);

        sessionRef.updateChildren(upd).addOnSuccessListener(a -> {
            jobRef.child("status").setValue("completed");
            runPtrRef.removeValue();
            toast("Job completed: " + format(totalFinal));
            finish();
        }).addOnFailureListener(e -> toast("Complete failed: " + e.getMessage()));
    }


    private String format(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, sec);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
