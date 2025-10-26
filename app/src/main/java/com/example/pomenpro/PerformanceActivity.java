package com.example.pomenpro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class PerformanceActivity extends AppCompatActivity {

    private TextView tvPerProductivity, tvPerEfficiency, tvPerProficiency;
    private ImageView btnHome;
    private ImageView ivBadges; // ← badge target

    private FirebaseAuth auth;
    private DatabaseReference rootRef;

    private long startMs, endMs;

    // -------- Badge thresholds (edit these if your ego demands different numbers) --------
    // score < 60  -> iron
    // 60..74      -> bronze
    // 75..89      -> silver
    // >= 90       -> gold
    private static final int T_BRONZE = 60;
    private static final int T_SILVER = 75;
    private static final int T_GOLD   = 90;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_performance);

        tvPerProductivity = findViewById(R.id.tvPerProductivity);
        tvPerEfficiency   = findViewById(R.id.tvPerEfficiency);
        tvPerProficiency  = findViewById(R.id.tvPerProficiency);
        btnHome           = findViewById(R.id.btnhome);
        ivBadges          = findViewById(R.id.ivbadges);

        auth = FirebaseAuth.getInstance();
        rootRef = FirebaseDatabase.getInstance().getReference();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, TechnicianDashboardActivity.class));
            finish();
        });

        computeTodayWindow();
        loadTodayPerformance();
    }

    private void computeTodayWindow() {
        TimeZone tz = TimeZone.getDefault();
        Calendar calStart = Calendar.getInstance(tz);
        calStart.set(Calendar.HOUR_OF_DAY, 0);
        calStart.set(Calendar.MINUTE, 0);
        calStart.set(Calendar.SECOND, 0);
        calStart.set(Calendar.MILLISECOND, 0);
        startMs = calStart.getTimeInMillis();

        Calendar calEnd = (Calendar) calStart.clone();
        calEnd.add(Calendar.DAY_OF_YEAR, 1);
        endMs = calEnd.getTimeInMillis();
    }

    private void loadTodayPerformance() {
        final String uid = auth.getCurrentUser().getUid();

        Query jobsQuery = rootRef.child("Jobs")
                .orderByChild("assignedTo")
                .equalTo(uid);

        jobsQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot jobsSnap) {
                if (!jobsSnap.exists()) {
                    setPercentages(0, 0, 0);
                    return;
                }

                final int[] assignedToday = {0};
                final int[] completedToday = {0};
                final int[] firstTryCompleted = {0};
                final long[] sumEstMinCompleted = {0};
                final long[] sumActualMinCompleted = {0};

                final int totalJobs = (int) jobsSnap.getChildrenCount();
                final int[] processedJobs = {0};

                for (DataSnapshot job : jobsSnap.getChildren()) {
                    String jobId = job.getKey();
                    Long createdAt = getLong(job.child("createdAt").getValue());
                    if (createdAt != null && createdAt >= startMs && createdAt < endMs) {
                        assignedToday[0]++;
                    }

                    String status = asString(job.child("status").getValue());
                    boolean statusCompleted = isCompletedWord(status);

                    Long estMinutes = getLong(job.child("estMinutes").getValue());

                    rootRef.child("jobSessions").child(jobId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot sessionsSnap) {
                                    boolean jobCompletedToday = false;
                                    long totalDurMinTodayForJob = 0;
                                    int totalSessionsEver = (int) sessionsSnap.getChildrenCount();

                                    Set<String> sessionIds = new HashSet<>();
                                    for (DataSnapshot session : sessionsSnap.getChildren()) {
                                        sessionIds.add(session.getKey());
                                        String sessionTech = asString(session.child("technicianId").getValue());
                                        if (!uid.equals(sessionTech)) continue;

                                        Long endTime = getLong(session.child("endTime").getValue());
                                        Long durationMs = getLong(session.child("durationMs").getValue());

                                        if (endTime != null && endTime >= startMs && endTime < endMs) {
                                            jobCompletedToday = true;
                                            long durMin = durationMs != null ? Math.max(1, durationMs / 60000L) : 0;
                                            totalDurMinTodayForJob += durMin;
                                        }
                                    }

                                    if (jobCompletedToday && statusCompleted) {
                                        completedToday[0]++;

                                        if (estMinutes != null) {
                                            sumEstMinCompleted[0] += Math.max(0, estMinutes);
                                            sumActualMinCompleted[0] += Math.max(1, totalDurMinTodayForJob);
                                        }

                                        if (totalSessionsEver == 1) {
                                            firstTryCompleted[0]++;
                                        }
                                    }

                                    processedJobs[0]++;
                                    if (processedJobs[0] == totalJobs) {
                                        int productivity = assignedToday[0] > 0
                                                ? clamp((int) Math.round(completedToday[0] * 100.0 / assignedToday[0]))
                                                : 0;

                                        int efficiency = sumActualMinCompleted[0] > 0
                                                ? clamp((int) Math.round(sumEstMinCompleted[0] * 100.0 / sumActualMinCompleted[0]))
                                                : 0;

                                        int proficiency = completedToday[0] > 0
                                                ? clamp((int) Math.round(firstTryCompleted[0] * 100.0 / completedToday[0]))
                                                : 0;

                                        setPercentages(productivity, efficiency, proficiency);
                                    }
                                }

                                @Override public void onCancelled(@NonNull DatabaseError error) {
                                    processedJobs[0]++;
                                    if (processedJobs[0] == totalJobs) {
                                        setPercentages(0, 0, 0);
                                    }
                                }
                            });
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PerformanceActivity.this, "DB error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                setPercentages(0, 0, 0);
            }
        });
    }

    private void setPercentages(int prod, int eff, int prof) {
        tvPerProductivity.setText(prod + "%");
        tvPerEfficiency.setText(eff + "%");
        tvPerProficiency.setText(prof + "%");

        // Compute overall score and update badge
        int overall = Math.round((prod + eff + prof) / 3.0f);
        updateBadge(overall);
    }

    // ---------- Badge logic ----------
    private void updateBadge(int score) {
        if (ivBadges == null) return; // XML went rogue? Fine, we bail.

        int resId;
        if (score >= T_GOLD) {
            resId = R.drawable.badges_gold;
        } else if (score >= T_SILVER) {
            resId = R.drawable.badges_silver;
        } else if (score >= T_BRONZE) {
            resId = R.drawable.badges_bronze;
        } else {
            resId = R.drawable.badges_iron;
        }
        ivBadges.setImageResource(resId);
        ivBadges.setContentDescription("Badge score " + score);
    }

    private static boolean isCompletedWord(String s) {
        if (s == null) return false;
        s = s.trim().toLowerCase();
        return s.equals("completed") || s.equals("done");
    }

    private static String asString(Object v) { return v == null ? "" : String.valueOf(v); }

    private static Long getLong(Object v) {
        if (v instanceof Long) return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double) return Math.round((Double) v);
        if (v != null) {
            try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static int clamp(int x) { return x < 0 ? 0 : Math.min(100, x); }
}
