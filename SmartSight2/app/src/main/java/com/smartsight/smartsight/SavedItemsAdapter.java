package com.example.smartsight;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavedItemsAdapter extends RecyclerView.Adapter<SavedItemsAdapter.ItemHolder> {

    public interface OnItemActionListener {
        void onItemClick(SavedItem item);
        void onItemHold(SavedItem item);
    }

    private List<SavedItem> items = new ArrayList<>();
    private final Context context;
    private final OnItemActionListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault());

    private TextToSpeech tts;

    private static final long HOLD_DURATION_MS = 2000;

    public SavedItemsAdapter(Context context, OnItemActionListener listener) {
        this.context  = LocaleManager.wrap(context.getApplicationContext());
        this.listener = listener;
    }

    /**
     * Called from SavedItemsActivity.onInit() once TTS is ready.
     * Re-binds all rows so TalkBack focus speech is applied to existing items.
     */
    public void setTts(TextToSpeech tts) {
        this.tts = tts;
        notifyDataSetChanged();
    }

    public void setItems(List<SavedItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_row, parent, false);
        return new ItemHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemHolder h, int position) {
        SavedItem item = items.get(position);

        h.txtCustomName.setText(item.customName != null ? item.customName : "");

        String categoryLabel;
        if ("text".equalsIgnoreCase(item.category)) {
            categoryLabel = context.getString(R.string.this_is_text_note);
        } else {
            categoryLabel = context.getString(R.string.this_is_object);
        }
        h.txtCategory.setText(categoryLabel);

        h.txtDate.setText(dateFormat.format(new Date(item.scanDate)));

        if ("text".equalsIgnoreCase(item.category)) {
            h.txtDetected.setText("\"" +
                    (item.detectedName != null ? item.detectedName : "") + "\"");
        } else {
            String translated = item.detectedName != null
                    ? LabelTranslator.translate(context, item.detectedName)
                    : "";
            h.txtDetected.setText(context.getString(R.string.detected_as, translated));
        }

        if (item.imagePath != null && !item.imagePath.isEmpty()) {
            File f = new File(item.imagePath);
            if (f.exists()) {
                android.graphics.Bitmap bm = BitmapFactory.decodeFile(item.imagePath);
                if (bm != null) {
                    h.imgThumbnail.setImageBitmap(bm);
                } else {
                    h.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                h.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            h.imgThumbnail.setImageResource(android.R.drawable.ic_menu_edit);
        }

        // Content description for non-TalkBack accessibility services
        String name = item.customName != null ? item.customName : "";
        h.itemView.setContentDescription(
                name + ", " + categoryLabel + ", " +
                h.txtDate.getText() + ". " +
                context.getString(R.string.hold_to_edit));

        // When TalkBack is on: silence default TalkBack speech and redirect
        // focus announcements through the app's TTS (correct language, correct voice).
        // Context null → no "button." suffix appended (this is a list row, not a button).
        if (tts != null && AccessibilityUtils.isTalkBackEnabled(context)) {
            String focusLabel = name.isEmpty() ? categoryLabel : name + ". " + categoryLabel;
            TalkBackSilencer.addFocusSpeech(h.itemView, focusLabel, tts, null);
        }

        // Hold detection
        final Handler holdHandler = new Handler(Looper.getMainLooper());
        final boolean[] holdTriggered = {false};

        h.itemView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    holdTriggered[0] = false;
                    holdHandler.postDelayed(() -> {
                        holdTriggered[0] = true;
                        if (listener != null) listener.onItemHold(item);
                    }, HOLD_DURATION_MS);
                    return true;

                case MotionEvent.ACTION_UP:
                    holdHandler.removeCallbacksAndMessages(null);
                    if (!holdTriggered[0]) {
                        if (listener != null) listener.onItemClick(item);
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacksAndMessages(null);
                    return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView  txtCustomName, txtCategory, txtDate, txtDetected;

        ItemHolder(@NonNull View v) {
            super(v);
            imgThumbnail  = v.findViewById(R.id.imgThumbnail);
            txtCustomName = v.findViewById(R.id.txtCustomName);
            txtCategory   = v.findViewById(R.id.txtCategory);
            txtDate       = v.findViewById(R.id.txtDate);
            txtDetected   = v.findViewById(R.id.txtDetected);
        }
    }
}
