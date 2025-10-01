package com.example.pomenpro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText txtName, txtEmail, txtPassword, txtPhone;
    private RadioGroup radioGroupRole;
    private Button btnRegister, btnBackLogin;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register2); // your XML

        // Firebase
        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // UI refs
        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        txtPhone = findViewById(R.id.txtPhone);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackLogin = findViewById(R.id.btnBackLogin);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> registerUser());
        btnBackLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = txtName.getText().toString().trim();
        String email = txtEmail.getText().toString().trim();
        String password = txtPassword.getText().toString().trim();
        String phone = txtPhone.getText().toString().trim();

        // Role
        int selectedId = radioGroupRole.getCheckedRadioButtonId();
        String finalRole = (selectedId == R.id.radioAdmin) ? "admin" : "technician";

        // Validation
        if (TextUtils.isEmpty(name)) {
            txtName.setError("Name required");
            txtName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtEmail.setError("Valid email required");
            txtEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            txtPassword.setError("Password (min 6 chars)");
            txtPassword.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // Firebase create
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);

                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        if (firebaseUser == null) {
                            Toast.makeText(RegisterActivity.this, "Unexpected auth error.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String uid = firebaseUser.getUid();
                        long createdAt = System.currentTimeMillis();

                        // User map
                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("email", email);
                        user.put("role", finalRole);
                        user.put("phone", phone);
                        user.put("createdAt", createdAt);

                        // Save in DB
                        usersRef.child(uid).setValue(user)
                                .addOnCompleteListener(dbTask -> {
                                    if (dbTask.isSuccessful()) {
                                        // Try sending verification email
                                        firebaseUser.sendEmailVerification()
                                                .addOnCompleteListener(mailTask -> {
                                                    if (mailTask.isSuccessful()) {
                                                        Toast.makeText(RegisterActivity.this,
                                                                "Registered successfully. Verification email sent.",
                                                                Toast.LENGTH_LONG).show();
                                                    } else {
                                                        Toast.makeText(RegisterActivity.this,
                                                                "Registered, but failed to send verification email.",
                                                                Toast.LENGTH_LONG).show();
                                                    }

                                                    // Always sign out after register
                                                    FirebaseAuth.getInstance().signOut();

                                                    // Redirect to login
                                                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    startActivity(intent);
                                                    finish();
                                                });

                                    } else {
                                        // rollback
                                        firebaseUser.delete();
                                        Toast.makeText(RegisterActivity.this,
                                                "Failed to save profile: " + dbTask.getException().getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });

                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
