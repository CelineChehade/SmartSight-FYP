package com.example.smartsight;

import android.Manifest;
import android.content.Context;
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

public class SavedItemsActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener,
        SavedItemsAdapter.OnItemActionListener,
        VoiceEditManager.Callbacks {

    private ItemViewModel itemViewModel;
    private SavedItemsAdapter adapter;
    private TextToSpeech tts;
    private VoiceEditManager voiceEditManager;

    private static final int AUDIO_PERMISSION_CODE = 300;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SettingsPrefs.isHighContrast(this)) {
            setTheme(android.R.style.Theme_Black_NoTitleBar);
        }

        setContentView(R.layout.activity_saved_items);

        tts = new TextToSpeech(this, this);

        RecyclerView recycler = findViewById(R.id.recyclerSavedItems);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SavedItemsAdapter(this, this);
        recycler.setAdapter(adapter);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);

        itemViewModel.getAllItems().observe(this, items -> {
            adapter.setItems(items);

            if (items == null || items.isEmpty()) {
                speak(getString(R.string.no_saved_items));
            } else if (items.size() == 1) {
                speak(getString(R.string.saved_items_count_one));
            } else {
                speak(getString(R.string.saved_items_count_many, items.size()));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, tts);
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
            speak(getString(R.string.mic_permission_required));
        }
    }

    // ───────────────────── ADAPTER CALLBACKS ─────────────────────

    @Override
    public void onItemClick(SavedItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.customName != null ? item.customName : "");
        sb.append(". ");

        if ("text".equalsIgnoreCase(item.category)) {
            sb.append(getString(R.string.this_is_text_note)).append(" ");
            if (item.detectedName != null && !item.detectedName.isEmpty()) {
                sb.append(getString(R.string.text_reads, item.detectedName)).append(" ");
            }
        } else {
            sb.append(getString(R.string.this_is_object)).append(" ");
            if (item.detectedName != null) {
                String translatedLabel = LabelTranslator.translate(this, item.detectedName);
                sb.append(getString(R.string.detected_as, translatedLabel)).append(" ");
            }
        }

        sb.append(getString(R.string.hold_to_edit));
        speak(sb.toString());
    }

    @Override
    public void onItemHold(SavedItem item) {
        if (voiceEditManager != null) {
            voiceEditManager.buildItemDescription(item.customName != null ? item.customName : "");
        }
    }

    // ───────────────────── VOICE EDIT MANAGER CALLBACKS ─────────────────────

    @Override
    public void onActionDelete() {
        // Handle delete action (e.g., remove selected item)
        // You can show a confirmation dialog before actually deleting
    }

    @Override
    public void onActionRename() {
        // Handle rename action (e.g., show rename dialog)
        // Example: prompt user for new name and update itemViewModel
    }

    private void speak(String msg) {
        if (tts != null) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (voiceEditManager != null) {
            // Add shutdown method in VoiceEditManager if needed
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
