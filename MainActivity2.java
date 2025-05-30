package com.example.eddc;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity2 extends AppCompatActivity {

    private static final String TAG = "MainActivity2";
    private static final String FIREBASE_URL = "https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app";

    // UI components
    private TextInputLayout phoneInputLayout;
    private TextInputEditText phoneNumberInput;
    private MaterialButton verifyButton;
    private View progressBar;

    // Firebase components
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);

        // Configure Edge-to-Edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase Auth and Database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance(FIREBASE_URL).getReference();

        // Initialize UI components
        initializeViews();

        // Set up input validation
        setupInputValidation();

        // Set up click listener for verification button
        verifyButton.setOnClickListener(v -> startVerificationProcess());
    }

    private void initializeViews() {
        phoneInputLayout = findViewById(R.id.phoneInputLayout);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        verifyButton = findViewById(R.id.verifyButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupInputValidation() {
        // Add text change listener for real-time validation
        phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear error as user types
                phoneInputLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Enable/disable button based on input
                verifyButton.setEnabled(s.length() >= 10);
            }
        });
    }

    private void startVerificationProcess() {
        String phoneNumber = phoneNumberInput.getText() != null ?
                phoneNumberInput.getText().toString().trim() : "";

        if (validatePhoneNumber(phoneNumber)) {
            // Ensure phone number has country code
            if (!phoneNumber.startsWith("+")) {
                // Add +91 (India) country code if not present - adjust for your region
                phoneNumber = "+91" + phoneNumber;
            }

            // Show loading state
            setLoadingState(true);

            // Start Firebase Phone Auth verification
            startFirebasePhoneVerification(phoneNumber);
        }
    }

    private boolean validatePhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            phoneInputLayout.setError("Phone number cannot be empty");
            return false;
        } else if (phoneNumber.length() < 10) {
            phoneInputLayout.setError("Please enter a valid phone number");
            return false;
        }

        return true;
    }

    private void startFirebasePhoneVerification(String phoneNumber) {
        Log.d(TAG, "Starting phone verification for: " + phoneNumber);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                // This callback will be invoked in two situations:
                                // 1 - Instant verification. In some cases the phone number can be instantly
                                //     verified without needing to send or enter a verification code.
                                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                                //     detect the incoming verification SMS and perform verification without
                                //     user action.
                                Log.d(TAG, "onVerificationCompleted: Auto-verification completed");
                                setLoadingState(false);
                                signInWithPhoneAuthCredential(credential);
                            }

                            @Override
                            public void onVerificationFailed(@NonNull FirebaseException e) {
                                // This callback is invoked if an invalid request for verification is made,
                                // for instance if the the phone number format is invalid.
                                Log.e(TAG, "onVerificationFailed: ", e);
                                setLoadingState(false);
                                Toast.makeText(MainActivity2.this,
                                        "Verification failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onCodeSent(@NonNull String verificationId,
                                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                // The SMS verification code has been sent to the provided phone number,
                                // we now need to ask the user to enter the code and then construct a credential
                                // by combining the code with a verification ID.
                                Log.d(TAG, "onCodeSent: Verification code sent");
                                mVerificationId = verificationId;
                                mResendToken = token;
                                setLoadingState(false);

                                // Show dialog to enter verification code
                                showVerificationCodeDialog(phoneNumber);

                                Toast.makeText(MainActivity2.this,
                                        "Verification code sent to " + phoneNumber,
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void showVerificationCodeDialog(String phoneNumber) {
        // Create a dialog with an input field for the verification code
        final TextInputLayout codeInputLayout = new TextInputLayout(this);
        codeInputLayout.setHint("Enter 6-digit verification code");

        final TextInputEditText codeInput = new TextInputEditText(codeInputLayout.getContext());
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInputLayout.addView(codeInput);

        // Create countdown timer for verification timeout
        final CountDownTimer[] timer = new CountDownTimer[1];

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Verify Phone Number")
                .setMessage("A verification code has been sent to " + phoneNumber)
                .setView(codeInputLayout)
                .setPositiveButton("Verify", (dialogInterface, i) -> {
                    String enteredCode = codeInput.getText() != null ?
                            codeInput.getText().toString().trim() : "";
                    verifyPhoneNumberWithCode(enteredCode);
                    if (timer[0] != null) {
                        timer[0].cancel();
                    }
                })
                .setNegativeButton("Cancel", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    if (timer[0] != null) {
                        timer[0].cancel();
                    }
                })
                .setNeutralButton("Resend Code", (dialogInterface, i) -> {
                    resendVerificationCode(phoneNumber, mResendToken);
                    if (timer[0] != null) {
                        timer[0].cancel();
                    }
                })
                .setCancelable(false)
                .create();

        dialog.show();

        // Start countdown timer
        timer[0] = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (dialog.isShowing()) {
                    dialog.setMessage("A verification code has been sent to " + phoneNumber +
                            "\nExpires in " + (millisUntilFinished / 1000) + " seconds");
                }
            }

            @Override
            public void onFinish() {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                    Toast.makeText(MainActivity2.this,
                            "Verification code expired. Please try again.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }.start();
    }

    private void resendVerificationCode(String phoneNumber, PhoneAuthProvider.ForceResendingToken token) {
        setLoadingState(true);
        Log.d(TAG, "Resending verification code to: " + phoneNumber);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                setLoadingState(false);
                                signInWithPhoneAuthCredential(credential);
                            }

                            @Override
                            public void onVerificationFailed(@NonNull FirebaseException e) {
                                Log.e(TAG, "Resend verification failed: ", e);
                                setLoadingState(false);
                                Toast.makeText(MainActivity2.this,
                                        "Verification failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onCodeSent(@NonNull String verificationId,
                                                   @NonNull PhoneAuthProvider.ForceResendingToken resendToken) {
                                Log.d(TAG, "Resend code sent successfully");
                                setLoadingState(false);
                                mVerificationId = verificationId;
                                mResendToken = resendToken;
                                Toast.makeText(MainActivity2.this,
                                        "New verification code sent",
                                        Toast.LENGTH_SHORT).show();

                                // Show dialog to enter verification code
                                showVerificationCodeDialog(phoneNumber);
                            }
                        })
                        .setForceResendingToken(token)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyPhoneNumberWithCode(String code) {
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mVerificationId != null) {
            setLoadingState(true);
            Log.d(TAG, "Verifying code: " + code);
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
            signInWithPhoneAuthCredential(credential);
        } else {
            Log.e(TAG, "verifyPhoneNumberWithCode: mVerificationId is null");
            Toast.makeText(this, "Verification failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoadingState(false);
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = task.getResult().getUser();

                        Toast.makeText(MainActivity2.this,
                                "Verification successful!",
                                Toast.LENGTH_SHORT).show();
                        proceedToNextStep();
                    } else {
                        // Sign in failed, display a message and update the UI
                        Log.w(TAG, "signInWithCredential:failure", task.getException());

                        String errorMessage = "Verification failed";
                        if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid verification code";
                        } else if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }

                        Toast.makeText(MainActivity2.this,
                                errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void proceedToNextStep() {
        // Navigate to ChangePasswordActivity
        Intent changePasswordIntent = new Intent(MainActivity2.this, change_password.class);
        // You can pass any necessary data to the ChangePasswordActivity
        // For example, you might want to pass the user's phone number
        String phoneNumber = Objects.requireNonNull(phoneNumberInput.getText()).toString().trim();
        changePasswordIntent.putExtra("PHONE_NUMBER", phoneNumber);
        // Or pass the Firebase User ID
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            changePasswordIntent.putExtra("USER_ID", user.getUid());
        }
        startActivity(changePasswordIntent);
        finish(); // Close this activity
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            verifyButton.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            verifyButton.setEnabled(true);
            progressBar.setVisibility(View.GONE);
        }
    }
}