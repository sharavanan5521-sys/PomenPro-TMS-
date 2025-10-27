package com.example.pomenpro;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class PerformanceActivity extends AppCompatActivity {

    private TextView tvPerProductivity, tvPerEfficiency, tvPerProficiency;
    private ImageView btnHome;
    private ImageView ivBadges; // badge target

    private FirebaseAuth auth;
    private DatabaseReference rootRef;

    private long startMs, endMs;

    // Badge thresholds
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your top bar color
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your bottom bar color
        }
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

                final int totalJobs = (int) jobsSnap.getChildrenCount();
                final int[] processedJobs = {0};

                // Counters
                final int[] touchedToday = {0};           // jobs with any session today by this tech
                final int[] completedToday = {0};         // jobs finished today (status + session)
                final int[] firstTryCompleted = {0};      // finished with exactly 1 session ever
                final long[] sumEstMinCompleted = {0};    // planned minutes for completed jobs
                final long[] sumActualMinCompleted = {0}; // actual minutes today for completed jobs

                for (DataSnapshot job : jobsSnap.getChildren()) {
                    String jobId = job.getKey();
                    String status = asString(job.child("status").getValue());
                    boolean statusCompleted = isCompletedWord(status);
                    Long estMinutes = getLong(job.child("estMinutes").getValue());

                    rootRef.child("jobSessions").child(jobId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot sessionsSnap) {
                                    boolean anySessionTodayByTech = false;
                                    boolean completedTodayBySession = false;
                                    long totalDurMinTodayForJob = 0;
                                    int totalSessionsEver = 0;

                                    for (DataSnapshot session : sessionsSnap.getChildren()) {
                                        totalSessionsEver++;
                                        String sessionTech = asString(session.child("technicianId").getValue());
                                        if (!uid.equals(sessionTech)) continue;

                                        Long endTime = getLong(session.child("endTime").getValue());
                                        Long durationMs = getLong(session.child("durationMs").getValue());
                                        Long startTime = getLong(session.child("startTime").getValue());

                                        if (endTime != null && endTime >= startMs && endTime < endMs) {
                                            anySessionTodayByTech = true;
                                            completedTodayBySession = true; // session ended today
                                            long durMin = computeDurationMin(durationMs, startTime, endTime);
                                            totalDurMinTodayForJob += durMin;
                                        } else if (startTime != null && startTime >= startMs && startTime < endMs) {
                                            anySessionTodayByTech = true; // started today but maybe not ended
                                            long durMin = computeDurationMin(durationMs, startTime, endTime);
                                            totalDurMinTodayForJob += durMin;
                                        }
                                    }

                                    if (anySessionTodayByTech) {
                                        touchedToday[0]++;
                                    }

                                    if (completedTodayBySession && statusCompleted) {
                                        completedToday[0]++;
                                        if (estMinutes != null) {
                                            sumEstMinCompleted[0] += Math.max(0, estMinutes);
                                        }
                                        sumActualMinCompleted[0] += Math.max(1, totalDurMinTodayForJob);
                                        if (totalSessionsEver == 1) {
                                            firstTryCompleted[0]++;
                                        }
                                    }

                                    processedJobs[0]++;
                                    if (processedJobs[0] == totalJobs) {
                                        int productivity = touchedToday[0] > 0
                                                ? clamp((int) Math.round(completedToday[0] * 100.0 / touchedToday[0]))
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

    private static long computeDurationMin(Long durationMs, Long startTime, Long endTime) {
        if (durationMs != null) {
            return Math.max(1, durationMs / 60000L);
        }
        if (startTime != null && endTime != null && endTime >= startTime) {
            return Math.max(1, (endTime - startTime) / 60000L);
        }
        return 0;
    }

    private void setPercentages(int prod, int eff, int prof) {
        tvPerProductivity.setText(prod + "%");
        tvPerEfficiency.setText(eff + "%");
        tvPerProficiency.setText(prof + "%");

        int overall = Math.round((prod + eff + prof) / 3.0f);
        updateBadge(overall);
    }

    private void updateBadge(int score) {
        if (ivBadges == null) return;
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
