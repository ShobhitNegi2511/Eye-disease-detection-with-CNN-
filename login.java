package com.example.eddc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class login extends AppCompatActivity {

    private static final String TAG = "EyeDiseaseDetector";
    private static final String MODEL_FILE = "eye_disease_classifier.pt";
    private static final int INPUT_SIZE = 256;

    private ImageView imageView;
    private Button btnPickImage, btnPredict, btnViewHistory;
    private TextView txtResult;
    private ProgressBar progressBar;
    private Bitmap selectedImage;
    private Module model;

    // All 15 class names (must match training order exactly)
    private final String[] classNames = {"Blepharitis", "Bulging_Eyes", "Cataract", "Chalazion",
            "Conjunctivitis", "Crossed_Eyes", "Diabetic_Retinopathy",
            "Eyelid_Drooping", "Glaucoma", "Jaundice", "Keratitis",
            "Normal", "Pterygium", "Stye", "Uveitis"};

    // Firebase variables
    private DatabaseReference databaseRef;
    private String currentUserId;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    try {
                        // Load image with proper configuration
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                        selectedImage = BitmapFactory.decodeStream(imageStream, null, options);

                        // Ensure image is in RGB format
                        selectedImage = convertToRGB(selectedImage);

                        imageView.setImageBitmap(selectedImage);
                        btnPredict.setEnabled(true);
                        txtResult.setText(R.string.image_selected_message);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load image", e);
                        Toast.makeText(this, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        currentUserId = intent.getStringExtra("USER_ID");
        if (currentUserId == null) {
            currentUserId = "9548343029"; // Default test user id
        }

        databaseRef = FirebaseDatabase.getInstance("https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference();

        imageView = findViewById(R.id.imageView);
        btnPickImage = findViewById(R.id.btnPickImage);
        btnPredict = findViewById(R.id.btnPredict);
        txtResult = findViewById(R.id.txtResult);
        progressBar = findViewById(R.id.progressBar);

        btnViewHistory = findViewById(R.id.btnViewHistory);
        if (btnViewHistory != null) {
            btnViewHistory.setOnClickListener(v -> viewHistory());
        } else {
            Log.w(TAG, "btnViewHistory not found in layout.");
        }

        btnPredict.setEnabled(false);

        btnPickImage.setOnClickListener(v -> openImagePicker());

        btnPredict.setOnClickListener(v -> {
            if (selectedImage != null) {
                predictDisease();
            } else {
                Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show();
            }
        });

        loadModel();
    }

    private Bitmap convertToRGB(Bitmap bitmap) {
        // Create a new RGB bitmap
        Bitmap rgbBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        // Copy pixels ensuring RGB format
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int pixel = bitmap.getPixel(x, y);
                rgbBitmap.setPixel(x, y, Color.rgb(
                        Color.red(pixel),
                        Color.green(pixel),
                        Color.blue(pixel)
                ));
            }
        }
        return rgbBitmap;
    }

    private void loadModel() {
        progressBar.setVisibility(View.VISIBLE);
        txtResult.setText(R.string.loading_model);

        executorService.execute(() -> {
            try {
                // Force delete old cached model if it exists
                File cachedModel = new File(getFilesDir(), MODEL_FILE);
                if (cachedModel.exists()) {
                    boolean deleted = cachedModel.delete();
                    Log.d(TAG, "Deleted old cached model: " + deleted);
                }

                // Copy fresh model from assets into internal storage
                String modelPath = assetFilePath(MODEL_FILE);
                if (modelPath != null) {
                    File modelFile = new File(modelPath);
                    Log.d(TAG, "Model file size: " + modelFile.length() + " bytes");

                    // Load model
                    model = Module.load(modelPath);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        txtResult.setText(R.string.model_loaded);
                        Toast.makeText(login.this, R.string.model_ready, Toast.LENGTH_SHORT).show();
                    });

                    // Run test prediction to confirm model output size
                    testModelWithRandomInput();
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        txtResult.setText(R.string.model_not_found);
                        Toast.makeText(login.this, R.string.model_not_found, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error loading model", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    txtResult.setText(getString(R.string.model_load_error, e.getMessage()));
                    Toast.makeText(
                            login.this,
                            getString(R.string.model_load_error, e.getMessage()),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void testModelWithRandomInput() {
        try {
            // Create random input tensor
            float[] randomInput = new float[3 * INPUT_SIZE * INPUT_SIZE];
            for (int i = 0; i < randomInput.length; i++) {
                randomInput[i] = (float) Math.random();
            }

            Tensor inputTensor = Tensor.fromBlob(randomInput, new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
            Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();

            float[] outputs = outputTensor.getDataAsFloatArray();

            // Log all outputs
            Log.d(TAG, "Test output length: " + outputs.length);
            for (int i = 0; i < outputs.length; i++) {
                Log.d(TAG, String.format(Locale.US, "Class %d (%.20s): %.6f", i,
                        classNames.length > i ? classNames[i] : "Unknown", outputs[i]));
            }

            if (outputs.length != classNames.length) {
                Log.w(TAG, "⚠️ Model output length mismatch: Expected " + classNames.length + ", got " + outputs.length);
            }

        } catch (Exception e) {
            Log.e(TAG, "Model test failed", e);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void predictDisease() {
        if (model == null) {
            Toast.makeText(this, R.string.model_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnPredict.setEnabled(false);
        txtResult.setText(R.string.analyzing_image);

        executorService.execute(() -> {
            try {
                // Ensure image is in RGB format
                Bitmap rgbBitmap = convertToRGB(selectedImage);

                // Resize image to expected model input size
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(rgbBitmap, INPUT_SIZE, INPUT_SIZE, true);

                // Normalization constants (must match training)
                final float[] MEAN = {0.485f, 0.456f, 0.406f};
                final float[] STD = {0.229f, 0.224f, 0.225f};

                // Create input tensor from bitmap
                Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                        resizedBitmap, MEAN, STD
                );

                // Log input tensor statistics for debugging
                float[] inputData = inputTensor.getDataAsFloatArray();
                logTensorStats("Input Tensor", inputData);

                // Run forward pass
                Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();

                // Get raw scores
                float[] scores = outputTensor.getDataAsFloatArray();

                // Log raw outputs for debugging
                Log.d(TAG, "Raw output scores:");
                for (int i = 0; i < Math.min(scores.length, classNames.length); i++) {
                    Log.d(TAG, String.format(Locale.US, "%s: %.6f", classNames[i], scores[i]));
                }

                // Apply softmax to get probabilities
                float[] probabilities = softmax(scores);

                // Find the class with highest probability
                int maxIndex = 0;
                float maxProbability = probabilities[0];
                for (int i = 1; i < Math.min(probabilities.length, classNames.length); i++) {
                    if (probabilities[i] > maxProbability) {
                        maxProbability = probabilities[i];
                        maxIndex = i;
                    }
                }

                String predictedClass = classNames[maxIndex];
                float confidence = maxProbability * 100;

                // MODIFIED: Simplified result string - only showing prediction without confidence
                String result = "Detected: " + predictedClass;

                // Save prediction to Firebase (still include confidence in database)
                saveDetectionToFirebase(predictedClass, confidence, selectedImage);

                // Update UI with results
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnPredict.setEnabled(true);
                    txtResult.setText(result);
                });

            } catch (Exception e) {
                Log.e(TAG, "Prediction error", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnPredict.setEnabled(true);
                    txtResult.setText(getString(R.string.prediction_error, e.getMessage()));
                    Toast.makeText(login.this, R.string.prediction_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void logTensorStats(String name, float[] tensorData) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0;

        for (float value : tensorData) {
            if (value < min) min = value;
            if (value > max) max = value;
            sum += value;
        }

        float mean = sum / tensorData.length;
        Log.d(TAG, String.format(Locale.US,
                "%s stats - Min: %.6f, Max: %.6f, Mean: %.6f",
                name, min, max, mean));
    }

    private void saveDetectionToFirebase(String diseaseName, float confidence, Bitmap image) {
        String detectionId = databaseRef.child("users").child(currentUserId).child("history").push().getKey();

        if (detectionId == null) {
            Log.e(TAG, "Failed to create detection entry key");
            return;
        }

        String base64Image = bitmapToBase64(image);

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("disease", diseaseName);
        historyEntry.put("confidence", confidence); // Still save confidence to database
        historyEntry.put("image", base64Image);
        historyEntry.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        databaseRef.child("users").child(currentUserId).child("history").child(detectionId)
                .setValue(historyEntry)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "History saved successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving history", e));
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private String assetFilePath(String assetName) throws IOException {
        File file = new File(getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = getAssets().open(assetName);
             OutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[15 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return file.getAbsolutePath();
        }
    }

    private float[] softmax(float[] scores) {
        // Find max score for numerical stability
        float max = Float.NEGATIVE_INFINITY;
        for (float score : scores) {
            if (score > max) max = score;
        }

        // Compute exp and sum
        float sum = 0f;
        float[] expScores = new float[scores.length];
        for (int i = 0; i < scores.length; i++) {
            expScores[i] = (float) Math.exp(scores[i] - max);
            sum += expScores[i];
        }

        // Normalize to get probabilities
        for (int i = 0; i < expScores.length; i++) {
            expScores[i] /= sum;
        }

        return expScores;
    }

    private void viewHistory() {
        Intent intent = new Intent(this, History.class);
        intent.putExtra("USER_ID", currentUserId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}