package com.example.pomenpro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class LoginActivity extends AppCompatActivity {

    private EditText txtUsername, txtPassword;
    private Button btnLogin, btnSignup, btnForgotPass;

    private FirebaseAuth auth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep it simple; no EdgeToEdge helper to avoid dependency tantrums.
        setContentView(R.layout.activity_login);

        // Initialize Firebase (in case you don't do it in Application).
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference();

        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        btnForgotPass = findViewById(R.id.btnForgotPass);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doLogin(); }
        });

        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                // Don't finish; let users come back with Back.
            }
        });

        btnForgotPass.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doReset(); }
        });
    }

    private void doLogin() {
        String email = safeTrim(txtUsername);
        String pass = safeTrim(txtPassword);

        if (!isValidEmail(email)) { toast("Enter a valid email."); return; }
        if (TextUtils.isEmpty(pass)) { toast("Password cannot be empty."); return; }

        btnLogin.setEnabled(false);
        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override public void onComplete(@NonNull Task<AuthResult> task) {
                        btnLogin.setEnabled(true);

                        if (!task.isSuccessful()) {
                            toast(task.getException() != null ? task.getException().getMessage() : "Login failed");
                            return;
                        }
                        if (auth.getCurrentUser() == null) {
                            toast("User not found after login. Try again.");
                            return;
                        }

                        String uid = auth.getCurrentUser().getUid();
                        db.child("users").child(uid).child("role")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String role = snapshot.getValue(String.class);

                                        if (role == null) {
                                            toast("Role not set. Contact admin.");
                                            auth.signOut();
                                            return;
                                        }

                                        if ("technician".equalsIgnoreCase(role)) {
                                            startActivity(new Intent(LoginActivity.this, TechnicianDashboardActivity.class));
                                            finish();
                                        } else {
                                            toast("Unrecognized role: " + role);
                                            auth.signOut();
                                        }
                                    }

                                    @Override public void onCancelled(@NonNull DatabaseError error) {
                                        toast("Failed to read role: " + error.getMessage());
                                    }
                                });
                    }
                });
    }

    private void doReset() {
        String email = safeTrim(txtUsername);
        if (!isValidEmail(email)) {
            toast("Enter the email to reset.");
            return;
        }
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) toast("Password reset email sent.");
                        else toast(task.getException() != null ? task.getException().getMessage() : "Failed to send reset email.");
                    }
                });
    }

    private String safeTrim(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private boolean isValidEmail(String s) {
        return !TextUtils.isEmpty(s) && Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
