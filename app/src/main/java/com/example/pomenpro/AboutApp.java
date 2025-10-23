package com.example.pomenpro;  // keep your package

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AboutApp extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // CHANGE THIS if your layout XML file name is different.
        // Use the actual name of the XML you pasted (e.g., R.layout.activity_about_app).
        setContentView(R.layout.activity_about_app);

        // Version text (textView19 in your XML)
        TextView tvVersion = findViewById(R.id.textView19);
        String versionName = "1.0";
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                PackageInfo pi = getPackageManager()
                        .getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0));
                versionName = pi.versionName;
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                versionName = pi.versionName;
            }
        } catch (Exception ignored) {
            // If PackageManager throws a fit, we just keep the default.
        }
        tvVersion.setText("Version " + versionName);

        // Copyright text (textView20 in your XML)
        TextView tvCopyright = findViewById(R.id.textView20);
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        tvCopyright.setText("© " + year + " Pomen Pro");
    }
}
