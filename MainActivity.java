package com.example.eddc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class MainActivity extends AppCompatActivity {
    EditText e1, e2;
    Button b1, b2, b3, b4;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
        );

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        e1 = findViewById(R.id.phone);
        e2 = findViewById(R.id.password);
        b1 = findViewById(R.id.forget);
        b2 = findViewById(R.id.create);
        b3 = findViewById(R.id.contact);
        b4 = findViewById(R.id.sign);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        FirebaseStorage.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app");

        // Forgot Password Button
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MainActivity2.class);
                startActivity(i);
            }
        });

        // Create Account Button
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, account_creation.class);
                startActivity(i);
            }
        });

        // Contact Button
        b3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setData(Uri.parse("mailto:"));
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{"shobhitnegi.39593@gmail.com"});
                i.putExtra(Intent.EXTRA_SUBJECT, "Customer Report");
                startActivity(i);
            }
        });

        // Sign In Button
        b4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = e1.getText().toString().trim();
                String password = e2.getText().toString().trim();

                if (phone.isEmpty()) {
                    e1.setError("Phone number is required");
                    e1.requestFocus();
                    return;
                }

                if (password.isEmpty()) {
                    e2.setError("Password is required");
                    e2.requestFocus();
                    return;
                }

                DatabaseReference usersRef = database.getReference("users");

                usersRef.child(phone).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            String storedPassword = dataSnapshot.child("password").getValue(String.class);

                            if (storedPassword != null && storedPassword.equals(password)) {
                                // Login success
                                Intent i = new Intent(MainActivity.this, login.class);
                                startActivity(i);
                                finish();
                            } else {
                                Toast.makeText(MainActivity.this, "Incorrect password", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("Firebase", "Database error: " + error.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to retrieve data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
