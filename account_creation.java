package com.example.eddc;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.regex.Pattern;

public class account_creation extends AppCompatActivity {
    private static final String TAG = "AccountCreation";
    private static final String FIREBASE_URL = "https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app";

    // Pattern for validating phone numbers (simple 10-digit number)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");

    // Pattern for validating password strength (min 8 chars, at least 1 letter and 1 number)
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$");

    private EditText etPhone, etPassword, etConfirmPassword;
    private Button btnCreateAccount;
    private ProgressBar progressBar;

    private FirebaseDatabase database;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account_creation);

        // Set up window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        etPhone = findViewById(R.id.phone);
        etPassword = findViewById(R.id.password);
        etConfirmPassword = findViewById(R.id.confirm_password); // Make sure to add this field in your layout
        btnCreateAccount = findViewById(R.id.create);
        progressBar = findViewById(R.id.progressBar); // Make sure to add this to your layout

        // If progressBar doesn't exist yet, handle it gracefully
        if (progressBar == null) {
            Log.w(TAG, "ProgressBar not found in layout, progress won't be shown");
        }

        // Initialize Firebase
        database = FirebaseDatabase.getInstance(FIREBASE_URL);
        usersRef = database.getReference("users");

        // Set up click listener for create account button
        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAccount();
            }
        });
    }

    private void createAccount() {
        // Get input values and trim whitespace
        final String phone = etPhone.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        String confirmPassword = "";

        if (etConfirmPassword != null) {
            confirmPassword = etConfirmPassword.getText().toString().trim();
        }

        // Validate inputs
        if (!validateInputs(phone, password, confirmPassword)) {
            return;
        }

        // Show progress
        showProgress(true);

        // First check if phone number already exists
        usersRef.child(phone).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Phone number already registered
                    showProgress(false);
                    Toast.makeText(account_creation.this,
                            "This phone number is already registered. Please use a different number or try logging in.",
                            Toast.LENGTH_LONG).show();
                } else {
                    // Phone number is available, proceed with account creation
                    saveUserToDatabase(phone, password);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showProgress(false);
                Log.e(TAG, "Firebase database error: " + databaseError.getMessage());
                Toast.makeText(account_creation.this,
                        "Database error: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validateInputs(String phone, String password, String confirmPassword) {
        // Validate phone number
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return false;
        }

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            etPhone.setError("Please enter a valid 10-digit phone number");
            etPhone.requestFocus();
            return false;
        }

        // Validate password
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return false;
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            etPassword.setError("Password must be at least 8 characters with letters and numbers");
            etPassword.requestFocus();
            return false;
        }

        // Validate confirm password if the field exists
        if (etConfirmPassword != null) {
            if (TextUtils.isEmpty(confirmPassword)) {
                etConfirmPassword.setError("Please confirm your password");
                etConfirmPassword.requestFocus();
                return false;
            }

            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                etConfirmPassword.requestFocus();
                return false;
            }
        }

        return true;
    }

    private void saveUserToDatabase(String phone, String password) {
        // Create user data object
        HashMap<String, Object> user = new HashMap<>();
        user.put("password", hashPassword(password)); // Still keeping password (you can hash this securely later)

        // Save to Firebase
        usersRef.child(phone).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved successfully");
                    showProgress(false);
                    Toast.makeText(account_creation.this,
                            "Account created successfully! You can now login.",
                            Toast.LENGTH_SHORT).show();

                    // Navigate to login screen
                    finish();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Log.e(TAG, "Failed to save user: " + e.getMessage());
                    Toast.makeText(account_creation.this,
                            "Failed to create account: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private String hashPassword(String password) {
        // WARNING: This is not secure and only for demonstration
        // In a real app, use a secure hashing algorithm with salt
        // Consider using Firebase Authentication instead
        return password;
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        btnCreateAccount.setEnabled(!show);
    }
}