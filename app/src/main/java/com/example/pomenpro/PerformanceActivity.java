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
import java.util.TimeZone;

public class PerformanceActivity extends AppCompatActivity {

    private TextView tvPerProductivity, tvPerEfficiency, tvPerProficiency;
    private ImageView btnHome;
    private ImageView ivBadges;

    private FirebaseAuth auth;
    private DatabaseReference rootRef;

    private long startMs, endMs;

    // Visible thresholds remain the same; tier logic is enforced with gates in updateBadgeWeighted.
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
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_oren));
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_oren));
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
                final int[] touchedToday = {0};
                final int[] completedToday = {0};
                final int[] firstTryCompleted = {0};

                // Efficiency sums (valid estimate only)
                final long[] sumEstMinCompleted = {0};
                final long[] sumActualMinCompleted = {0};
                final int[]  effJobCount = {0};

                for (DataSnapshot job : jobsSnap.getChildren()) {
                    String jobId = job.getKey();
                    final String status = asString(job.child("status").getValue());
                    final boolean statusCompleted = isCompletedWord(status);
                    final Long estMinutesRaw = getLong(job.child("estMinutes").getValue());

                    rootRef.child("jobSessions").child(jobId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot sessionsSnap) {
                                    boolean anySessionTodayByTech = false;
                                    boolean endedTodayByThisTech = false;

                                    long totalDurMinAllForJobByTech = 0; // all time, this tech
                                    int sessionsEverByThisTech = 0;

                                    for (DataSnapshot session : sessionsSnap.getChildren()) {
                                        String sessionTech = asString(session.child("technicianId").getValue());
                                        if (!uid.equals(sessionTech)) continue;

                                        sessionsEverByThisTech++;

                                        Long endTime = getLong(session.child("endTime").getValue());
                                        Long durationMs = getLong(session.child("durationMs").getValue());
                                        Long startTime = getLong(session.child("startTime").getValue());

                                        long durMin = computeDurationMin(durationMs, startTime, endTime);
                                        totalDurMinAllForJobByTech += Math.max(0, durMin);

                                        boolean endedToday = (endTime != null && endTime >= startMs && endTime < endMs);
                                        boolean startedToday = (startTime != null && startTime >= startMs && startTime < endMs);

                                        if (endedToday || startedToday) anySessionTodayByTech = true;
                                        if (endedToday) endedTodayByThisTech = true;
                                    }

                                    if (anySessionTodayByTech) touchedToday[0]++;

                                    if (statusCompleted && endedTodayByThisTech) {
                                        completedToday[0]++;

                                        if (estMinutesRaw != null && estMinutesRaw > 0 && totalDurMinAllForJobByTech > 0) {
                                            sumEstMinCompleted[0] += estMinutesRaw;
                                            sumActualMinCompleted[0] += totalDurMinAllForJobByTech;
                                            effJobCount[0]++;
                                        }

                                        if (sessionsEverByThisTech == 1) {
                                            firstTryCompleted[0]++;
                                        }
                                    }

                                    processedJobs[0]++;
                                    if (processedJobs[0] == totalJobs) {
                                        int productivity = touchedToday[0] > 0
                                                ? clamp((int) Math.round(completedToday[0] * 100.0 / touchedToday[0]))
                                                : 0;

                                        int efficiency = (effJobCount[0] > 0 && sumActualMinCompleted[0] > 0)
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

        // New: weighted score
        int overallWeighted = clamp(Math.round(
                0.30f * prod +
                        0.40f * eff +
                        0.30f * prof
        ));

        // If literally nothing happened, iron.
        if (prod == 0 && eff == 0 && prof == 0) {
            updateBadgeResource("iron");
            return;
        }

        // Enforce floor gates per tier using caps so a weak metric blocks higher tiers.
        int minMetric = Math.min(prod, Math.min(eff, prof));
        if (minMetric < 40) {
            // cannot reach bronze
            overallWeighted = Math.min(overallWeighted, T_BRONZE - 1);
        } else if (minMetric < 65) {
            // cannot reach silver
            overallWeighted = Math.min(overallWeighted, T_SILVER - 1);
        } else if (minMetric < 80) {
            // cannot reach gold
            overallWeighted = Math.min(overallWeighted, T_GOLD - 1);
        }

        updateBadge(overallWeighted);
    }

    // Keeps the public updateBadge(int) so your layouts/resources stay the same.
    private void updateBadge(int score) {
        if (ivBadges == null) return;
        if (score >= T_GOLD) {
            updateBadgeResource("gold");
        } else if (score >= T_SILVER) {
            updateBadgeResource("silver");
        } else if (score >= T_BRONZE) {
            updateBadgeResource("bronze");
        } else {
            updateBadgeResource("iron");
        }
    }

    private void updateBadgeResource(String tier) {
        if (ivBadges == null) return;
        int resId;
        switch (tier) {
            case "gold":   resId = R.drawable.badges_gold; break;
            case "silver": resId = R.drawable.badges_silver; break;
            case "bronze": resId = R.drawable.badges_bronze; break;
            default:       resId = R.drawable.badges_iron;  break;
        }
        ivBadges.setImageResource(resId);
        ivBadges.setContentDescription("Badge " + tier);
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
