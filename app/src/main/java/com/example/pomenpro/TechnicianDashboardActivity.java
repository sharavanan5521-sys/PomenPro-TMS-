package com.example.pomenpro;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class TechnicianDashboardActivity extends AppCompatActivity {

    private CardView cvHome, cvPerformance, cvTask, cvSetting;
    private ImageView btnPower;

    private FirebaseAuth auth;
    private DatabaseReference db;

    private ValueEventListener profileListener;
    private long lastClickAt = 0L;
    private boolean greeted = false; // ✅ added flag to avoid repeating welcome toast

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_technician_dashboard2);

        // Firebase
        auth  = FirebaseAuth.getInstance();
        db    = FirebaseDatabase.getInstance().getReference();

        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { goLogin(); return; }

        // Bind
        cvHome        = findViewById(R.id.cvHome);
        cvPerformance = findViewById(R.id.cvPerformance);
        cvTask        = findViewById(R.id.cvTask);
        cvSetting     = findViewById(R.id.cvSetting);
        btnPower      = findViewById(R.id.imageView3);

        makeClickableButtonLike(btnPower);

        // Clicks
        cvHome.setOnClickListener(v -> safeOpen(ProfileActivity.class));
        cvPerformance.setOnClickListener(v -> safeOpen(PerformanceActivity.class));
        cvTask.setOnClickListener(v -> safeOpen(TaskActivity.class));
        cvSetting.setOnClickListener(v -> safeOpen(SettingsActivity.class));
        btnPower.setOnClickListener(v -> confirmSignOut());

        // Load minimal profile
        attachProfileMini(me.getUid());

        // Show active task count
        showMyOpenTaskCount(me.getUid());
    }

    private void makeClickableButtonLike(ImageView v) {
        v.setClickable(true);
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setSoundEffectsEnabled(true);

        TypedValue outValue = new TypedValue();
        boolean ok = getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        if (ok && outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId);

        v.post(() -> {
            v.bringToFront();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                v.setTranslationZ(8f);
                v.setElevation(8f);
            }
            v.requestLayout();
            v.invalidate();
        });

        v.post(() -> expandTouchArea(v, dp(40)));

        View parent = (View) v.getParent();
        if (parent != null) {
            parent.setClickable(false);
            parent.setFocusable(false);
        }
    }

    private int dp(int dps) {
        return Math.round(dps * getResources().getDisplayMetrics().density);
    }

    private void expandTouchArea(View view, int extra) {
        final View parent = (View) view.getParent();
        if (parent == null) return;

        parent.post(() -> {
            Rect rect = new Rect();
            view.getHitRect(rect);
            rect.top    -= extra;
            rect.bottom += extra;
            rect.left   -= extra;
            rect.right  += extra;
            parent.setTouchDelegate(new TouchDelegate(rect, view));
        });
    }

    private void attachProfileMini(String uid) {
        DatabaseReference ref = db.child("users").child(uid);
        profileListener = ref.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) { toast("Profile missing. Contact admin."); return; }

                String role = snap.child("role").getValue(String.class);
                String name = snap.child("name").getValue(String.class);

                if (role != null && !"technician".equalsIgnoreCase(role)) {
                    toast("This account isn’t a technician.");
                    signOutAndFinish();
                    return;
                }

                // ✅ show welcome toast only once
                if (!greeted && name != null && !name.isEmpty()) {
                    toast("Welcome, " + name);
                    greeted = true;
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                toast("Profile load failed: " + error.getMessage());
            }
        });
    }

    private void showMyOpenTaskCount(String uid) {
        db.child("Jobs")
                .orderByChild("assignedTo").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        int open = 0;
                        for (DataSnapshot s : snap.getChildren()) {
                            String status = s.child("status").getValue(String.class);
                            if (status == null || "pending".equalsIgnoreCase(status)
                                    || "in_progress".equalsIgnoreCase(status)) {
                                open++;
                            }
                        }
                        toast("You have " + open + " active task(s).");
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { /* ignore */ }
                });
    }

    private void safeOpen(Class<?> target) {
        long now = System.currentTimeMillis();
        if (now - lastClickAt < 500) return;
        lastClickAt = now;

        Intent i = new Intent(this, target);
        FirebaseUser me = auth.getCurrentUser();
        if (me != null) i.putExtra("uid", me.getUid());
        startActivity(i);
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Clocking out already?")
                .setPositiveButton("Sign out", (d, w) -> signOutAndFinish())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void signOutAndFinish() {
        auth.signOut();
        goLogin();
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseUser me = auth.getCurrentUser();
        if (me != null && profileListener != null) {
            db.child("users").child(me.getUid()).removeEventListener(profileListener);
        }
    }
}
