package com.example.smartsight;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

public class SavedItemsActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener,
        SavedItemsAdapter.OnItemActionListener,
        VoiceEditManager.Callbacks {

    private ItemViewModel itemViewModel;
    private SavedItemsAdapter adapter;
    private TextToSpeech tts;
    private VoiceEditManager voiceEditManager;

    private static final int AUDIO_PERMISSION_CODE = 300;

    // ───────────────────── ON CREATE ─────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_items);

        tts = new TextToSpeech(this, this);

        RecyclerView recycler = findViewById(R.id.recyclerSavedItems);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SavedItemsAdapter(this);
        recycler.setAdapter(adapter);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);

        itemViewModel.getAllItems().observe(this, items -> {
            adapter.setItems(items);

            if (items == null || items.isEmpty()) {
                speak("You have no saved items yet.");
            } else {
                speak("You have " + items.size() + " saved item" +
                        (items.size() > 1 ? "s" : "") +
                        ". Tap an item to hear details, or hold to rename or delete.");
            }
        });

        // Ask for mic permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            // Initialize VoiceEditManager once TTS is ready
            voiceEditManager = new VoiceEditManager(this, tts, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE
                && (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            speak("Microphone permission is needed for voice editing.");
        }
    }

    // ───────────────────── ADAPTER CALLBACKS ─────────────────────

    @Override
    public void onItemClick(SavedItem item) {
        // Short tap → just read the details aloud
        StringBuilder sb = new StringBuilder();
        sb.append(item.customName != null ? item.customName : "Unnamed item");
        sb.append(". ");

        if ("text".equalsIgnoreCase(item.category)) {
            sb.append("This is a text note. ");
            if (item.detectedName != null && !item.detectedName.isEmpty()) {
                sb.append("The text reads: ").append(item.detectedName).append(". ");
            }
        } else {
            sb.append("This is an object. ");
            if (item.detectedName != null) {
                sb.append("Detected as ").append(item.detectedName).append(". ");
            }
        }

        sb.append("Hold to rename or delete.");
        speak(sb.toString());
    }

    @Override
    public void onItemHold(SavedItem item) {
        // Long hold → hand off to VoiceEditManager for the full rename/delete dialog
        if (voiceEditManager != null) {
            voiceEditManager.startEditFlow(item);
        }
    }

    @Override
    public void onItemDelete(SavedItem item) {
        // Trash icon tapped → same flow as hold (VoiceEditManager asks delete/rename)
        if (voiceEditManager != null) {
            voiceEditManager.startEditFlow(item);
        }
    }

    // ───────────────────── VOICE EDIT MANAGER CALLBACKS ─────────────────────

    @Override
    public void onDeleteConfirmed(SavedItem item) {
        itemViewModel.delete(item);
    }

    @Override
    public void onRenameConfirmed(SavedItem item, String newName) {
        item.customName = newName;
        // Update in background
        new Thread(() -> {
            AppDatabase.getInstance(getApplicationContext())
                    .itemDao().update(item);
        }).start();
    }

    // ───────────────────── HELPERS ─────────────────────

    private void speak(String msg) {
        if (tts != null) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // ───────────────────── CLEANUP ─────────────────────

    @Override
    protected void onDestroy() {
        if (voiceEditManager != null) voiceEditManager.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
