package com.example.pomenpro;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // Header
    private ImageView btnBack, ivAvatar;
    private TextView tvTitle, tvName, tvEmail;

    // Cards
    private TextView tvFullName, tvPhoneNum, tvAddress;

    // Actions
    private Button btnLogout, btnEditProfile;

    // Firebase
    private FirebaseAuth auth;
    private DatabaseReference db;
    private ValueEventListener profileListener;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Bind views from XML
        btnBack   = findViewById(R.id.imageView11);
        ivAvatar  = findViewById(R.id.imageView9);
        tvTitle   = findViewById(R.id.textView8);
        tvName    = findViewById(R.id.tvName);
        tvEmail   = findViewById(R.id.tvGmail);
        tvFullName = findViewById(R.id.tvFullName);
        tvPhoneNum = findViewById(R.id.tvPhoneNum);
        tvAddress  = findViewById(R.id.tvAddress);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Firebase init
        auth = FirebaseAuth.getInstance();
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = me.getUid();
        db  = FirebaseDatabase.getInstance().getReference();

        // Back arrow
        btnBack.setOnClickListener(v -> finish());

        // Logout confirmation
        btnLogout.setOnClickListener(v -> new AlertDialog.Builder(ProfileActivity.this)
                .setMessage("Log out from this device?")
                .setPositiveButton("Log Out", (d, w) -> {
                    auth.signOut();
                    Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show());

        // Edit Profile
        btnEditProfile.setOnClickListener(v -> openEditProfileSheet());

        // Show Auth fallback until DB loads
        primeWithAuthFallback(me);

        // Live listener for profile data
        profileListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    Map<String, Object> seed = new HashMap<>();
                    seed.put("email", me.getEmail() == null ? "" : me.getEmail());
                    seed.put("name", safeText(tvName));
                    seed.put("phone", "");
                    seed.put("role", "user");
                    seed.put("address", "");
                    seed.put("createdAt", ServerValue.TIMESTAMP);
                    db.child("users").child(uid).updateChildren(seed);
                    applyUI(seed);
                    return;
                }

                // Auto add missing address
                if (!snap.hasChild("address")) {
                    Map<String, Object> patch = new HashMap<>();
                    patch.put("address", "");
                    db.child("users").child(uid).updateChildren(patch);
                }

                Map<String, Object> map = new HashMap<>();
                map.put("name", getStringChild(snap, "name"));
                map.put("email", getStringChild(snap, "email"));
                map.put("phone", getStringChild(snap, "phone"));
                map.put("address", getStringChild(snap, "address"));
                applyUI(map);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        db.child("users").child(uid).addValueEventListener(profileListener);

        // Card quick actions
        tvPhoneNum.setOnClickListener(v -> {
            String raw = stripSpaces(safeText(tvPhoneNum));
            if (!TextUtils.isEmpty(raw) && !raw.equals("Not provided yet.")) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + raw)));
            }
        });
        tvPhoneNum.setOnLongClickListener(v -> copyToClipboard("Phone", stripSpaces(safeText(tvPhoneNum))));

        tvEmail.setOnClickListener(v -> {
            String email = safeText(tvEmail);
            if (!TextUtils.isEmpty(email)) {
                Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
                startActivity(Intent.createChooser(mail, "Send email"));
            }
        });
        tvEmail.setOnLongClickListener(v -> copyToClipboard("Email", safeText(tvEmail)));

        tvAddress.setOnClickListener(v -> {
            String full = safeText(tvAddress);
            if (TextUtils.isEmpty(full) || full.equals("Not provided yet.")) return;
            new AlertDialog.Builder(ProfileActivity.this)
                    .setTitle("Address")
                    .setMessage(full)
                    .setPositiveButton("Close", null)
                    .show();
        });
        tvAddress.setOnLongClickListener(v -> copyToClipboard("Address", safeText(tvAddress)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.gray)); // your top bar color
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your bottom bar color
        }
    }

    // --- Bottom Sheet Edit Flow ---
    private void openEditProfileSheet() {
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_edit_profile, null);
        EditText etName = sheetView.findViewById(R.id.etEditName);
        EditText etPhone = sheetView.findViewById(R.id.etEditPhone);
        EditText etAddress = sheetView.findViewById(R.id.etEditAddress);
        Button btnSave = sheetView.findViewById(R.id.btnSaveChanges);
        View progress = sheetView.findViewById(R.id.progressSave);

        etName.setText(safeText(tvFullName));
        etPhone.setText(stripSpaces(safeText(tvPhoneNum)).replace("+60", "0"));
        String addr = safeText(tvAddress);
        etAddress.setText(addr.equals("Not provided yet.") ? "" : addr);

        BottomSheetDialog dialog = new BottomSheetDialog(ProfileActivity.this);
        dialog.setContentView(sheetView);
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Name required");
                return;
            }
            if (phone.isEmpty() || phone.length() < 8) {
                etPhone.setError("Invalid phone");
                return;
            }
            if (address.length() > 160) {
                etAddress.setError("Address too long");
                return;
            }

            btnSave.setEnabled(false);
            progress.setVisibility(View.VISIBLE);

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("phone", phone);
            updates.put("address", address);

            db.child("users").child(uid).updateChildren(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(ProfileActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        progress.setVisibility(View.GONE);
                    });
        });
    }

    // --- Helpers ---
    private void applyUI(Map<String, Object> map) {
        String name = asString(map.get("name"));
        String email = asString(map.get("email"));
        String phone = asString(map.get("phone"));
        String address = asString(map.get("address"));

        if (!TextUtils.isEmpty(name)) tvName.setText(name);
        if (!TextUtils.isEmpty(email)) tvEmail.setText(email);
        tvFullName.setText(!TextUtils.isEmpty(name) ? name : safeText(tvName));

        tvPhoneNum.setText(TextUtils.isEmpty(phone) ? "Not provided yet." : formatMalaysiaPhone(phone));
        tvAddress.setText(TextUtils.isEmpty(address) ? "Not provided yet." : address);
    }

    private void primeWithAuthFallback(FirebaseUser me) {
        String display;
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
    }

    private boolean copyToClipboard(String label, String text) {
        if (TextUtils.isEmpty(text) || text.equals("Not provided yet.")) return true;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        return true;
    }

    private static String safeText(TextView tv) {
        if (tv == null || tv.getText() == null) return "";
        return tv.getText().toString().trim();
    }

    private static String getStringChild(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return v == null ? "" : String.valueOf(v);
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String stripSpaces(String s) {
        return s == null ? "" : s.replace(" ", "");
    }

    private static String formatMalaysiaPhone(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("0")) return "+60" + digits.substring(1);
        return digits;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null && uid != null && db != null) {
            db.child("users").child(uid).removeEventListener(profileListener);
        }
    }

    public static class UserProfile {
        public String name;
        public String email;
        public String photoUrl;
        public String phone;
        public String role;
        public String address;
        public UserProfile() {}
    }
}
