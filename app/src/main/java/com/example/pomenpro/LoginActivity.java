package com.example.pomenpro;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton, signupButton, forgotPassButton;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ✅ Make sure your login screen XML file name matches here
        setContentView(R.layout.activity_login);

        // Firebase
        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // UI refs
        emailEditText = findViewById(R.id.txtUsername);
        passwordEditText = findViewById(R.id.txtPassword);
        loginButton = findViewById(R.id.btnLogin);
        signupButton = findViewById(R.id.btnSignup);
        forgotPassButton = findViewById(R.id.btnForgotPass);

        // 🔑 If a user is already logged in, go straight to dashboard
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndNavigate(currentUser.getUid());
        }

        // Login button
        loginButton.setOnClickListener(v -> attemptLogin());

        // Signup button → go to Register
        signupButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // Forgot password button
        forgotPassButton.setOnClickListener(v -> handleForgotPassword());
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required!");
            emailEditText.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address!");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required!");
            passwordEditText.requestFocus();
            return;
        }
        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters!");
            passwordEditText.requestFocus();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, (Task<AuthResult> task) -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            checkUserRoleAndNavigate(user.getUid());
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this,
                                "Login failed. Please check your email and password.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserRoleAndNavigate(String uid) {
        usersRef.child(uid).child("role")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String role = snapshot.getValue(String.class);
                            Log.d(TAG, "Fetched role: " + role); // Debugging

                            if ("admin".equalsIgnoreCase(role)) {
                                startActivity(new Intent(LoginActivity.this, AdminDashboardActivity.class));
                                finish();
                            } else if ("technician".equalsIgnoreCase(role)) {
                                startActivity(new Intent(LoginActivity.this, TechnicianDashboardActivity.class));
                                finish();
                            } else {
                                Toast.makeText(LoginActivity.this, "User role not recognized.", Toast.LENGTH_SHORT).show();
                                auth.signOut(); // fallback: log out if role invalid
                            }

                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "User role missing in database. Please contact support.",
                                    Toast.LENGTH_LONG).show();
                            auth.signOut();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "DB Error fetching role: " + error.getMessage());
                        Toast.makeText(LoginActivity.this,
                                "Database Error. Try again later.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }


    private void handleForgotPassword() {
        String email = emailEditText.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter your email to receive the reset link!");
            emailEditText.requestFocus();
        } else {
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this,
                                    "Password reset link sent to your email.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Failed to send reset email. Check if the email is correct.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }
}
