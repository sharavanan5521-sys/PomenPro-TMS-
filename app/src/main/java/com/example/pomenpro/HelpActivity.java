package com.example.pomenpro; // ← change to your package

import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class HelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help); // make a simple layout, even a TextView, your call


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your top bar color
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_oren)); // your bottom bar color
        }
    }
}