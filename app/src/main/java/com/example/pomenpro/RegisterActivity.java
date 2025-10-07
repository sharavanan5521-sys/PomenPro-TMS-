package com.example.pomenpro;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText txtName, txtEmail, txtPassword, txtPhone;
    private RadioGroup radioGroupRole;
    private RadioButton radioAdmin, radioTechnician;
    private Button btnRegister, btnBackLogin;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep it simple; avoid EdgeToEdge unless you added the dependency and @+id/main exists.
        setContentView(R.layout.activity_register2);

        // Firebase init (safe even if already initialized)
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference();

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        txtPhone = findViewById(R.id.txtPhone);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        radioAdmin = findViewById(R.id.radioAdmin);
        radioTechnician = findViewById(R.id.radioTechnician);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackLogin = findViewById(R.id.btnBackLogin);
        progressBar = findViewById(R.id.progressBar);

        btnBackLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doRegister(); }
        });
    }

    private void doRegister() {
        final String name = safeTrim(txtName);
        final String email = safeTrim(txtEmail);
        final String pass = safeTrim(txtPassword);
        final String phone = safeTrim(txtPhone);
        final String role = radioAdmin.isChecked() ? "admin"
                : (radioTechnician.isChecked() ? "technician" : "");

        if (TextUtils.isEmpty(name)) { toast("Name is required."); return; }
        if (!isValidEmail(email)) { toast("Enter a valid email."); return; }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) { toast("Password must be at least 6 characters."); return; }
        if (TextUtils.isEmpty(phone)) { toast("Phone is required."); return; }
        if (TextUtils.isEmpty(role)) { toast("Select a role."); return; }

        setBusy(true);
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            setBusy(false);
                            toast(task.getException() != null ? task.getException().getMessage() : "Registration failed");
                            return;
                        }

                        FirebaseUser user = auth.getCurrentUser();
                        if (user == null) {
                            setBusy(false);
                            toast("User not found after registration.");
                            return;
                        }

                        String uid = user.getUid();
                        Map<String, Object> profile = new HashMap<>();
                        profile.put("name", name);
                        profile.put("email", email);
                        profile.put("phone", phone);
                        profile.put("role", role);
                        profile.put("createdAt", System.currentTimeMillis());

                        db.child("users").child(uid).setValue(profile)
                                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<Void>() {
                                    @Override public void onComplete(@NonNull Task<Void> writeTask) {
                                        setBusy(false);
                                        if (writeTask.isSuccessful()) {
                                            toast("Registration successful. You can log in now.");
                                            // Optional: send email verification
                                            // user.sendEmailVerification();
                                            finish();
                                        } else {
                                            toast(writeTask.getException() != null
                                                    ? writeTask.getException().getMessage()
                                                    : "Failed to save profile");
                                        }
                                    }
                                });
                    }
                });
    }

    private String safeTrim(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private boolean isValidEmail(String s) {
        return !TextUtils.isEmpty(s) && Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!busy);
        btnBackLogin.setEnabled(!busy);
        txtName.setEnabled(!busy);
        txtEmail.setEnabled(!busy);
        txtPassword.setEnabled(!busy);
        txtPhone.setEnabled(!busy);
        radioGroupRole.setEnabled(!busy);
        radioAdmin.setEnabled(!busy);
        radioTechnician.setEnabled(!busy);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
