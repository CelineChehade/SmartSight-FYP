package com.example.smartsight;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SavedItemsActivity extends BaseActivity
        implements TextToSpeech.OnInitListener,
        SavedItemsAdapter.OnItemActionListener,
        VoiceEditManager.Callbacks {

    private ItemViewModel     itemViewModel;
    private SavedItemsAdapter adapter;
    private TextToSpeech      tts;
    private VoiceEditManager  voiceEditManager;
    private AppRepository     repository;
    private ReminderVoiceFlow reminderFlow;
    private SavedItem         currentEditItem;

    private static final int AUDIO_PERMISSION_CODE = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_items);
        TalkBackSilencer.silence(this, null);

        tts        = new TextToSpeech(this, this);
        repository = new AppRepository(this);

        RecyclerView recycler = findViewById(R.id.recyclerSavedItems);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        // Must be YES so TalkBack can reach inside and move focus between rows.
        recycler.setImportantForAccessibility(
                RecyclerView.IMPORTANT_FOR_ACCESSIBILITY_YES);

        adapter = new SavedItemsAdapter(this, this);
        recycler.setAdapter(adapter);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);
        itemViewModel.getAllItems().observe(this, items -> {
            adapter.setItems(items);
            if ("fr".equals(SettingsPrefs.getLanguage(this))
                    || !AccessibilityUtils.isTalkBackEnabled(this)) {
                if (items == null || items.isEmpty()) {
                    TtsHelper.speak(tts, getString(R.string.no_saved_items));
                } else if (items.size() == 1) {
                    TtsHelper.speak(tts, getString(R.string.saved_items_count_one));
                } else {
                    TtsHelper.speak(tts,
                            getString(R.string.saved_items_count_many, items.size()));
                }
            }
        });

        SharedSpeechRecognizer.init(this);
        requestMicPermission();
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, tts);
            TalkBackSilencer.silence(this, tts);
            voiceEditManager = new VoiceEditManager(this, tts, this);
            // Hand TTS to adapter so it can apply focus speech to each row
            adapter.setTts(tts);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted — nothing extra needed, mic is ready.
            } else {
                // Permission denied — tell the user and open app settings.
                TtsHelper.speakThen(tts,
                        getString(R.string.permission_mic_denied_go_to_settings),
                        () -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
            }
        }
    }

    // ── Item interaction ─────────────────────────────────────────────────────

    @Override
    public void onItemClick(SavedItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.customName != null ? item.customName : "").append(". ");
        if ("text".equalsIgnoreCase(item.category)) {
            sb.append(getString(R.string.this_is_text_note)).append(". ");
            if (item.detectedName != null && !item.detectedName.isEmpty()) {
                sb.append(getString(R.string.text_reads, item.detectedName)).append(". ");
            }
        } else {
            sb.append(getString(R.string.this_is_object)).append(". ");
            if (item.detectedName != null) {
                sb.append(getString(R.string.detected_as,
                        LabelTranslator.translateForTts(this, item.detectedName))).append(". ");
            }
        }
        sb.append(getString(R.string.hold_to_edit));
        TtsHelper.speak(tts, sb.toString());
    }

    @Override
    public void onItemHold(SavedItem item) {
        currentEditItem = item;
        repository.getFirstReminderForItem(item.itemId, reminder -> {
            boolean hasReminder = (reminder != null);
            runOnUiThread(() -> {
                if (voiceEditManager != null) {
                    voiceEditManager.buildItemDescription(
                            item.customName != null ? item.customName : "",
                            hasReminder);
                }
            });
        });
    }

    // ── VoiceEditManager.Callbacks ───────────────────────────────────────────

    @Override
    public void onActionRename(String newName) {
        if (currentEditItem != null) {
            itemViewModel.rename(currentEditItem.itemId, newName);
            TtsHelper.speak(tts, getString(R.string.vem_renamed_to, newName));
            currentEditItem = null;
        }
    }

    @Override
    public void onActionAddReminder(String itemName) {
        if (currentEditItem != null) startReminderFlowForItem(currentEditItem);
    }

    @Override
    public void onActionEditReminder(String itemName) {
        if (currentEditItem != null) {
            repository.deleteAllRemindersForItem(currentEditItem.itemId);
            startReminderFlowForItem(currentEditItem);
        }
    }

    @Override
    public void onActionDeleteReminder(String itemName) {
        if (currentEditItem != null) {
            repository.deleteAllRemindersForItem(currentEditItem.itemId);
            TtsHelper.speak(tts, getString(R.string.reminder_removed));
            currentEditItem = null;
        }
    }

    @Override
    public void onActionDelete(String itemName) {
        if (currentEditItem != null) {
            itemViewModel.delete(currentEditItem);
            TtsHelper.speak(tts, getString(R.string.vem_deleted));
            currentEditItem = null;
        }
    }

    @Override
    public void onCancelled() {
        currentEditItem = null;
    }

    // ── Reminder flow ────────────────────────────────────────────────────────

    private void startReminderFlowForItem(SavedItem item) {
        if (reminderFlow != null) reminderFlow.shutdown();
        reminderFlow = new ReminderVoiceFlow(this, tts, new ReminderVoiceFlow.Callbacks() {
            @Override
            public void onReminderDefined(String repeatType, long reminderTimeMs) {
                persistAndScheduleReminder(item, repeatType, reminderTimeMs);
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    TtsHelper.speak(tts, getString(R.string.vem_cancelled));
                    currentEditItem = null;
                });
            }
        });
        reminderFlow.start();
    }

    private void persistAndScheduleReminder(SavedItem item, String repeatType,
                                            long reminderTimeMs) {
        Reminder r     = new Reminder();
        r.itemId       = item.itemId;
        r.repeatType   = repeatType;
        r.isActive     = true;
        r.reminderTime = reminderTimeMs;
        repository.insertReminder(r, newId -> {
            ReminderScheduler.scheduleAt(SavedItemsActivity.this,
                    (int) newId, item.itemId, item.customName,
                    repeatType, reminderTimeMs);
            runOnUiThread(() -> {
                TtsHelper.speak(tts, getString(R.string.reminder_saved));
                currentEditItem = null;
            });
        });
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        SharedSpeechRecognizer.get().cancel();
        if (voiceEditManager != null) voiceEditManager.shutdown();
        if (reminderFlow != null) { reminderFlow.shutdown(); reminderFlow = null; }
    }

    @Override
    protected void onDestroy() {
        if (voiceEditManager != null) voiceEditManager.shutdown();
        if (reminderFlow != null) reminderFlow.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
