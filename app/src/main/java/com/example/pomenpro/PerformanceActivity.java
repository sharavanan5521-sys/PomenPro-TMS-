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
import java.util.Locale;
import java.util.TimeZone;

public class PerformanceActivity extends AppCompatActivity {

    private TextView tvPerProductivity, tvPerEfficiency, tvPerProficiency;
    private ImageView btnHome;
    private ImageView ivBadges;

    private FirebaseAuth auth;
    private DatabaseReference rootRef;

    private long startMs, endMs;

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

                final int[] touchedToday = {0};
                final int[] completedToday = {0};
                final int[] firstTryCompleted = {0};

                final long[] sumEstMinCompleted = {0};
                final long[] sumActualMinTodayCompleted = {0};
                final int[]  effJobCount = {0};

                for (DataSnapshot job : jobsSnap.getChildren()) {
                    String jobId = job.getKey();
                    final String status = asString(job.child("status").getValue());
                    final boolean statusCompleted = isCompletedWord(status);

                    // IMPORTANT: now supports your key `estimatedDurationMinutes`
                    final Long estMinutesRaw = parseEstimatedMinutes(job);

                    rootRef.child("jobSessions").child(jobId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot sessionsSnap) {
                                    boolean anySessionTodayByTech = false;
                                    boolean endedTodayByThisTech = false;

                                    long totalDurMinAllForJobByTech = 0;
                                    long totalDurMinTodayForJobByTech = 0;
                                    int sessionsEverByThisTech = 0;

                                    for (DataSnapshot session : sessionsSnap.getChildren()) {
                                        String sessionTech = asString(session.child("technicianId").getValue());
                                        if (!uid.equals(sessionTech)) continue;

                                        sessionsEverByThisTech++;

                                        Long rawDuration = getLong(session.child("durationMs").getValue());
                                        Long rawStart    = getLong(session.child("startTime").getValue());
                                        Long rawEnd      = getLong(session.child("endTime").getValue());

                                        long[] bounds = deriveBounds(rawDuration, rawStart, rawEnd);
                                        long s = bounds[0], e = bounds[1];

                                        long durMinAll = (e > s) ? Math.max(1, (e - s) / 60000L) : 0;
                                        totalDurMinAllForJobByTech += Math.max(0, durMinAll);

                                        long overlapMs = overlapMillis(s, e, startMs, endMs);
                                        if (overlapMs > 0) {
                                            anySessionTodayByTech = true;
                                            totalDurMinTodayForJobByTech += Math.max(1, overlapMs / 60000L);
                                        }

                                        boolean endedToday = (e >= startMs && e < endMs);
                                        if (endedToday) endedTodayByThisTech = true;
                                    }

                                    if (anySessionTodayByTech) touchedToday[0]++;

                                    if (statusCompleted && endedTodayByThisTech) {
                                        completedToday[0]++;

                                        // use today's minutes; if zero, fall back to lifetime minutes on finish day
                                        long actualForEff = (totalDurMinTodayForJobByTech > 0)
                                                ? totalDurMinTodayForJobByTech
                                                : totalDurMinAllForJobByTech;

                                        if (estMinutesRaw != null && estMinutesRaw > 0 && actualForEff > 0) {
                                            sumEstMinCompleted[0]        += estMinutesRaw;
                                            sumActualMinTodayCompleted[0] += actualForEff;
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

                                        int efficiency = (effJobCount[0] > 0 && sumActualMinTodayCompleted[0] > 0)
                                                ? clamp((int) Math.round(
                                                (sumEstMinCompleted[0] * 100.0) / sumActualMinTodayCompleted[0]))
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

    // ---------- Helpers ----------

    private static Long normalizeEpochMs(Long t) {
        if (t == null) return null;
        return (t < 1_000_000_000_000L) ? t * 1000L : t;
    }

    private static Long normalizeDurationMs(Long d) {
        if (d == null) return null;
        if (d < 600_000L) return d * 60_000L; // small numbers likely minutes → ms
        return d;
    }

    private long[] deriveBounds(Long rawDurationMs, Long rawStart, Long rawEnd) {
        Long s = normalizeEpochMs(rawStart);
        Long e = normalizeEpochMs(rawEnd);
        Long d = normalizeDurationMs(rawDurationMs);

        if (s != null && e != null && e >= s) return new long[]{s, e};
        if (e != null && d != null && d > 0)  return new long[]{Math.max(0, e - d), e};
        if (s != null && d != null && d > 0)  return new long[]{s, s + d};
        return new long[]{0, 0};
    }

    private long overlapMillis(long aStart, long aEnd, long bStart, long bEnd) {
        long start = Math.max(aStart, bStart);
        long end   = Math.min(aEnd, bEnd);
        return Math.max(0, end - start);
    }

    private void setPercentages(int prod, int eff, int prof) {
        tvPerProductivity.setText(prod + "%");
        tvPerEfficiency.setText(eff + "%");
        tvPerProficiency.setText(prof + "%");

        int overallWeighted = clamp(Math.round(
                0.30f * prod +
                        0.40f * eff +
                        0.30f * prof
        ));

        if (prod == 0 && eff == 0 && prof == 0) {
            updateBadgeResource("iron");
            return;
        }

        int minMetric = Math.min(prod, Math.min(eff, prof));
        if (minMetric < 40) {
            overallWeighted = Math.min(overallWeighted, T_BRONZE - 1);
        } else if (minMetric < 65) {
            overallWeighted = Math.min(overallWeighted, T_SILVER - 1);
        } else if (minMetric < 80) {
            overallWeighted = Math.min(overallWeighted, T_GOLD - 1);
        }

        updateBadge(overallWeighted);
    }

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
        s = s.trim().toLowerCase(Locale.US).replaceAll("[^a-z]", "");
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

    // Reads minutes from your DB without renaming anything.
    private static Long parseEstimatedMinutes(DataSnapshot jobSnap) {
        // Include your key: estimatedDurationMinutes
        Object v = firstNonNull(
                jobSnap.child("estMinutes").getValue(),
                jobSnap.child("estimatedMinutes").getValue(),
                jobSnap.child("estimatedDurationMinutes").getValue(),  // <- your field
                jobSnap.child("estMin").getValue(),
                jobSnap.child("estDurationMin").getValue(),
                jobSnap.child("estimateMin").getValue(),
                jobSnap.child("est_time_min").getValue(),
                // common “suggested timer” style keys if present
                jobSnap.child("suggestedDurationMinutes").getValue(),
                jobSnap.child("suggestedMinutes").getValue(),
                jobSnap.child("suggestedTimer").getValue()
        );

        Long fromPlain = tryParsePlainMinutes(v);
        if (fromPlain != null && fromPlain > 0) return fromPlain;

        Object vHr = firstNonNull(
                jobSnap.child("estHours").getValue(),
                jobSnap.child("estimatedHours").getValue(),
                jobSnap.child("suggestedHours").getValue()
        );
        Long hrsPlain = tryParsePlainMinutes(vHr);
        if (hrsPlain != null && hrsPlain > 0) return hrsPlain * 60;

        String s = String.valueOf(v == null ? "" : v).trim().toLowerCase(Locale.US);
        if (s.isEmpty()) return null;

        try {
            long totalMin = 0;
            if (s.contains("h")) {
                String[] parts = s.split("h", 2);
                double h = Double.parseDouble(parts[0].trim());
                totalMin += Math.round(h * 60.0);
                String after = parts.length > 1 ? parts[1].trim() : "";
                if (after.endsWith("m")) after = after.substring(0, after.length() - 1).trim();
                if (!after.isEmpty()) totalMin += Math.round(Double.parseDouble(after));
                return totalMin > 0 ? totalMin : null;
            }
            if (s.endsWith("m") || s.contains("min")) {
                s = s.replace("min", "").replace("m", "").trim();
                long m = Math.round(Double.parseDouble(s));
                return m > 0 ? m : null;
            }
            long m = Math.round(Double.parseDouble(s));
            return m > 0 ? m : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Object firstNonNull(Object... vals) {
        for (Object o : vals) if (o != null) return o;
        return null;
    }

    private static Long tryParsePlainMinutes(Object v) {
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double) return Math.round((Double) v);
        try {
            String s = String.valueOf(v).trim();
            if (s.matches("^\\d+(\\.\\d+)?$")) {
                double d = Double.parseDouble(s);
                return Math.round(d); // assume minutes
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static int clamp(int x) { return x < 0 ? 0 : Math.min(100, x); }
}
