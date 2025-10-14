package com.example.pomenpro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class ProfileActivity extends AppCompatActivity {

    private ImageView btnBack, ivAvatar;
    private TextView tvTitle, tvName, tvEmail;
    private Button btnLogout;

    private FirebaseAuth auth;
    private DatabaseReference db;

    private ValueEventListener profileListener;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // rename your XML to activity_profile.xml or match your actual name

        // Views from your XML
        btnBack  = findViewById(R.id.imageView11);
        tvTitle  = findViewById(R.id.textView8);
        ivAvatar = findViewById(R.id.imageView9);
        tvName   = findViewById(R.id.tvName);
        tvEmail  = findViewById(R.id.tvGmail);
        btnLogout= findViewById(R.id.btnLogout);

        // Firebase
        auth = FirebaseAuth.getInstance();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            Toast.makeText(this, "Not signed in. Try again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = me.getUid();
        db  = FirebaseDatabase.getInstance().getReference();

        // Back arrow: just close this page
        btnBack.setOnClickListener(v -> finish());

        // Logout: sign out and punt the user to login
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        // Show something immediately while we fetch
        primeWithAuthFallback(me);

        // Live fetch from Realtime Database: /users/{uid}
        // Expected structure:
        // users -> {uid} -> name, email, photoUrl, phone, role
        profileListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    // No DB record yet. Fine, keep auth fallback.
                    return;
                }

                UserProfile p = snap.getValue(UserProfile.class);
                if (p == null) return;

                // Name
                if (!TextUtils.isEmpty(p.name)) {
                    tvName.setText(p.name);
                }

                // Email
                if (!TextUtils.isEmpty(p.email)) {
                    tvEmail.setText(p.email);
                }

                // Avatar: using your static drawable already.
                // If later you store a URL, plug Glide/Picasso here.
                // Example (if you add Glide dependency):
                // Glide.with(ProfileActivity.this).load(p.photoUrl).placeholder(R.drawable.user).into(ivAvatar);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load profile: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        db.child("users").child(uid).addValueEventListener(profileListener);
    }

    private void primeWithAuthFallback(FirebaseUser me) {
        // Name priority: DB > Auth displayName > email local-part > "User"
        String display = null;
        if (me.getDisplayName() != null && !me.getDisplayName().trim().isEmpty()) {
            display = me.getDisplayName().trim();
        } else if (me.getEmail() != null && me.getEmail().contains("@")) {
            display = me.getEmail().substring(0, me.getEmail().indexOf('@'));
        } else {
            display = "User";
        }
        tvName.setText(display);

        if (me.getEmail() != null && !me.getEmail().trim().isEmpty()) {
            tvEmail.setText(me.getEmail());
        }

        // If you later use photoURL:
        // if (me.getPhotoUrl() != null) Glide.with(this).load(me.getPhotoUrl()).placeholder(R.drawable.user).into(ivAvatar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null && uid != null) {
            db.child("users").child(uid).removeEventListener(profileListener);
        }
    }

    // Simple POJO that matches /users/{uid}
    public static class UserProfile {
        public String name;
        public String email;
        public String photoUrl;
        public String phone;
        public String role;

        public UserProfile() {} // required

        public UserProfile(String name, String email, String photoUrl, String phone, String role) {
            this.name = name;
            this.email = email;
            this.photoUrl = photoUrl;
            this.phone = phone;
            this.role = role;
        }
    }
}
