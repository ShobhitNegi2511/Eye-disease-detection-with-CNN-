package com.example.eddc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class History extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private static final String PREFS_NAME = "EDDCPrefs";
    private static final String USER_ID_KEY = "user_id";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView txtNoHistory;
    private HistoryAdapter adapter;
    private List<DetectionHistoryItem> historyItems;

    private String currentUserId;
    private DatabaseReference databaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Set title
        setTitle("Detection History");

        // Get user ID from intent with multiple fallbacks
        setupUserId();

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance("https://eye-disease-detection-6a200-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference();

        // Initialize UI components
        recyclerView = findViewById(R.id.recyclerHistory);
        progressBar = findViewById(R.id.progressBarHistory);
        txtNoHistory = findViewById(R.id.txtNoHistory);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyItems = new ArrayList<>();
        adapter = new HistoryAdapter(historyItems);
        recyclerView.setAdapter(adapter);

        // Load history data
        loadHistoryData();
    }

    /**
     * Setup user ID with multiple fallback mechanisms
     */
    private void setupUserId() {
        // First try to get from intent
        currentUserId = getIntent().getStringExtra("USER_ID");

        // Then try Firebase Auth
        if (currentUserId == null || currentUserId.isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                currentUserId = user.getUid();
            }
        }

        // Then try SharedPreferences
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(USER_ID_KEY, null);
        }

        // Finally use default ID if all else fails
        if (currentUserId == null || currentUserId.isEmpty()) {
            // Default for testing - should not reach here if login is working correctly
            currentUserId = "9548343029";
            Log.w(TAG, "Using default user ID as fallback");
        }

        Log.d(TAG, "Using user ID: " + currentUserId);
    }

    private void loadHistoryData() {
        progressBar.setVisibility(View.VISIBLE);

        // Reference to user's history in database
        Query historyQuery = databaseRef.child("users").child(currentUserId).child("history")
                .orderByChild("timestamp");

        historyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                progressBar.setVisibility(View.GONE);
                historyItems.clear();

                if (!dataSnapshot.exists() || !dataSnapshot.hasChildren()) {
                    txtNoHistory.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    Log.d(TAG, "No history found for user: " + currentUserId);
                    return;
                }

                // Process each history item
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        String disease = snapshot.child("disease").getValue(String.class);

                        // Handle both Double and Float since the value type might vary
                        float confidence = 0f;
                        if (snapshot.child("confidence").getValue() instanceof Double) {
                            Double confDouble = snapshot.child("confidence").getValue(Double.class);
                            confidence = confDouble != null ? confDouble.floatValue() : 0f;
                        } else if (snapshot.child("confidence").getValue() instanceof Float) {
                            Float confFloat = snapshot.child("confidence").getValue(Float.class);
                            confidence = confFloat != null ? confFloat : 0f;
                        } else if (snapshot.child("confidence").getValue() instanceof Long) {
                            Long confLong = snapshot.child("confidence").getValue(Long.class);
                            confidence = confLong != null ? confLong.floatValue() : 0f;
                        } else if (snapshot.child("confidence").getValue() instanceof Integer) {
                            Integer confInt = snapshot.child("confidence").getValue(Integer.class);
                            confidence = confInt != null ? confInt.floatValue() : 0f;
                        }

                        String timestamp = snapshot.child("timestamp").getValue(String.class);
                        String imageBase64 = snapshot.child("image").getValue(String.class);

                        if (disease == null) {
                            Log.w(TAG, "Skipping history item with null disease name");
                            continue;
                        }

                        DetectionHistoryItem item = new DetectionHistoryItem(
                                snapshot.getKey(),
                                disease,
                                confidence,
                                timestamp != null ? timestamp : "Unknown date",
                                imageBase64
                        );

                        historyItems.add(item);
                        Log.d(TAG, "Added history item: " + disease + " with confidence: " + confidence);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing history item", e);
                    }
                }

                // Sort items newest first
                Collections.reverse(historyItems);

                if (historyItems.isEmpty()) {
                    txtNoHistory.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    txtNoHistory.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                    Log.d(TAG, "Displaying " + historyItems.size() + " history items");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(History.this,
                        "Failed to load history: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Database error loading history", databaseError.toException());
            }
        });
    }

    // History Item model class
    static class DetectionHistoryItem {
        String id;
        String disease;
        float confidence;
        String timestamp;
        String imageBase64;

        DetectionHistoryItem(String id, String disease, float confidence, String timestamp, String imageBase64) {
            this.id = id;
            this.disease = disease;
            this.confidence = confidence;
            this.timestamp = timestamp;
            this.imageBase64 = imageBase64;
        }
    }

    // RecyclerView Adapter
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        private List<DetectionHistoryItem> items;

        HistoryAdapter(List<DetectionHistoryItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.history_list_item, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            DetectionHistoryItem item = items.get(position);

            // Set text data
            holder.txtDisease.setText(item.disease);

            // MODIFIED: Hide confidence in history view
            holder.txtConfidence.setVisibility(View.GONE);

            holder.txtTimestamp.setText(item.timestamp);

            // Load image if available
            if (item.imageBase64 != null && !item.imageBase64.isEmpty()) {
                try {
                    byte[] decodedBytes = Base64.decode(item.imageBase64, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    holder.imgEye.setImageBitmap(bitmap);
                    holder.imgEye.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e(TAG, "Error decoding image", e);
                    holder.imgEye.setVisibility(View.GONE);
                }
            } else {
                holder.imgEye.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
            TextView txtDisease, txtConfidence, txtTimestamp;
            ImageView imgEye;

            HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                txtDisease = itemView.findViewById(R.id.txtHistoryDisease);
                txtConfidence = itemView.findViewById(R.id.txtHistoryConfidence);
                txtTimestamp = itemView.findViewById(R.id.txtHistoryTimestamp);
                imgEye = itemView.findViewById(R.id.imgHistoryEye);
            }
        }
    }
}