package com.example.pomenpro;// ← change to your package

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    private CardView cvAbout, cvHelp;
    private ImageView ivBack;
    private Button btnLogout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); // if your XML file name is different, fix this

        // Views
        cvAbout   = findViewById(R.id.cardView6);
        cvHelp    = findViewById(R.id.cardView7);
        ivBack    = findViewById(R.id.imageView21);
        btnLogout = findViewById(R.id.button);

        // About App
        cvAbout.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, AboutApp.class);
            startActivity(i);
        });

        // Help
        cvHelp.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, HelpActivity.class);
            startActivity(i);
        });

        // Back arrow
        ivBack.setOnClickListener(v -> onBackPressed());

        // Logout (Firebase). If you’re not using Firebase, replace with your own logout flow.
        btnLogout.setOnClickListener(v -> {
            try {
                FirebaseAuth.getInstance().signOut();
            } catch (Exception ignored) { }
            // Send them to your login/start screen and clear history so back doesn’t sneak them in.
            Intent i = new Intent(SettingsActivity.this, LoginActivity.class); // change if your auth entry is different
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
