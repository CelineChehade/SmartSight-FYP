package com.example.smartsight;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavedItemsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private NoteViewModel noteViewModel;
    private NoteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_items);

        tts = new TextToSpeech(this, this);

        RecyclerView recyclerView = findViewById(R.id.recyclerSavedItems);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NoteAdapter();
        recyclerView.setAdapter(adapter);

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        noteViewModel.getAllNotes().observe(this, notes -> {
            adapter.setNotes(notes);
            if (notes == null || notes.isEmpty()) {
                tts.speak("No saved scans yet.",
                        TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                tts.speak("You have " + notes.size() + " saved scan" +
                                (notes.size() == 1 ? "." : "s."),
                        TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
        }
    }

    private class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

        private List<SavedNote> notes = new ArrayList<>();

        void setNotes(List<SavedNote> notes) {
            this.notes = notes;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved, parent, false);
            return new NoteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
            SavedNote note = notes.get(position);

            String displayName = (note.customName != null && !note.customName.isEmpty())
                    ? note.customName : "Scan " + (position + 1);

            String date = new SimpleDateFormat("dd MMM yyyy, HH:mm",
                    Locale.getDefault()).format(new Date(note.scanDate));

            holder.tvName.setText(displayName);
            holder.tvDate.setText(date);
            holder.tvPreview.setText(note.extractedText != null ? note.extractedText : "");

            // Tap → read full text aloud
            holder.itemView.setOnClickListener(v -> {
                String toSpeak = (note.customName != null && !note.customName.isEmpty())
                        ? note.customName + ". " + note.extractedText
                        : note.extractedText;
                tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
            });

            // Long press → delete confirmation
            holder.itemView.setOnLongClickListener(v -> {
                tts.speak("Hold to delete " + displayName,
                        TextToSpeech.QUEUE_FLUSH, null, null);
                new AlertDialog.Builder(SavedItemsActivity.this)
                        .setTitle("Delete scan")
                        .setMessage("Delete \"" + displayName + "\"?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            noteViewModel.delete(note);
                            tts.speak(displayName + " deleted.",
                                    TextToSpeech.QUEUE_FLUSH, null, null);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return notes.size(); }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDate, tvPreview;

            NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName    = itemView.findViewById(R.id.tvNoteName);
                tvDate    = itemView.findViewById(R.id.tvNoteDate);
                tvPreview = itemView.findViewById(R.id.tvNotePreview);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
