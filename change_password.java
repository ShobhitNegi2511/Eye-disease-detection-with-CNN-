package com.example.eddc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class change_password extends AppCompatActivity {

    TextInputEditText phoneInput, newPasswordInput, confirmPasswordInput;
    Button changePasswordButton;
    DatabaseReference databaseReference;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase Database
        databaseReference = FirebaseDatabase.getInstance("https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("users");


        // Initialize UI elements
        phoneInput = findViewById(R.id.phone_number);
        newPasswordInput = findViewById(R.id.new_password);
        confirmPasswordInput = findViewById(R.id.confirm_password);
        changePasswordButton = findViewById(R.id.change_password_button);

        changePasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get input values
                String phoneNumber = phoneInput.getText().toString().trim();
                String newPassword = newPasswordInput.getText().toString().trim();
                String confirmPassword = confirmPasswordInput.getText().toString().trim();

                // Validate inputs
                if (phoneNumber.isEmpty()) {
                    phoneInput.setError("Phone number is required");
                    phoneInput.requestFocus();
                    return;
                }

                if (newPassword.isEmpty()) {
                    newPasswordInput.setError("New password is required");
                    newPasswordInput.requestFocus();
                    return;
                }

                if (confirmPassword.isEmpty()) {
                    confirmPasswordInput.setError("Confirm your password");
                    confirmPasswordInput.requestFocus();
                    return;
                }

                // Check if passwords match
                if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(change_password.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check password strength
                if (newPassword.length() < 6) {
                    Toast.makeText(change_password.this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update password in database
                updatePasswordInDatabase(phoneNumber, newPassword);
            }
        });
    }

    private void updatePasswordInDatabase(String phoneNumber, String newPassword) {
        // Directly reference the user node using phone number as key
        DatabaseReference userRef = databaseReference.child(phoneNumber);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // User exists, update password
                    userRef.child("password").setValue(newPassword)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(change_password.this,
                                        "Password updated successfully",
                                        Toast.LENGTH_SHORT).show();
                                // Clear fields
                                phoneInput.setText("");
                                newPasswordInput.setText("");
                                confirmPasswordInput.setText("");
                                Intent i = new Intent(change_password.this, MainActivity.class);
                                startActivity(i);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(change_password.this,
                                        "Failed to update password: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });

                } else {
                    Toast.makeText(change_password.this,
                            "No user found with this phone number",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(change_password.this,
                        "Database error: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
