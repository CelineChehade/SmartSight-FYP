package com.example.smartsight;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
        void onItemClick(SavedItem item);       // short tap → read details
        void onItemHold(SavedItem item);        // long hold → open action menu
        void onItemDelete(SavedItem item);      // trash icon
    }

    private List<SavedItem> items = new ArrayList<>();
    private final OnItemActionListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault());

    private static final long HOLD_DURATION_MS = 2000;

    public SavedItemsAdapter(OnItemActionListener listener) {
        this.listener = listener;
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

        // Name
        h.txtCustomName.setText(item.customName != null ? item.customName : "(no name)");

        // Category label
        String categoryLabel = "text".equalsIgnoreCase(item.category) ? "Note (Text)" : "Object";
        h.txtCategory.setText(categoryLabel);

        // Date & time
        h.txtDate.setText(dateFormat.format(new Date(item.scanDate)));

        // Detected preview
        if ("text".equalsIgnoreCase(item.category)) {
            h.txtDetected.setText("\"" + (item.detectedName != null ? item.detectedName : "") + "\"");
        } else {
            h.txtDetected.setText("Detected: " +
                    (item.detectedName != null ? item.detectedName : "unknown"));
        }

        // Image
        if (item.imagePath != null && !item.imagePath.isEmpty()) {
            File f = new File(item.imagePath);
            if (f.exists()) {
                h.imgThumbnail.setImageBitmap(BitmapFactory.decodeFile(item.imagePath));
            } else {
                h.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            h.imgThumbnail.setImageResource(android.R.drawable.ic_menu_edit);
        }

        // ─── TAP vs HOLD handling ───
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
                        // It was a short tap
                        if (listener != null) listener.onItemClick(item);
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacksAndMessages(null);
                    return true;
            }
            return false;
        });

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onItemDelete(item);
        });

        // Accessibility
        h.itemView.setContentDescription(
                h.txtCustomName.getText() + ", " +
                        categoryLabel + ", saved on " +
                        h.txtDate.getText() +
                        ". Hold to rename or delete."
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView txtCustomName, txtCategory, txtDate, txtDetected;
        ImageButton btnDelete;

        ItemHolder(@NonNull View v) {
            super(v);
            imgThumbnail = v.findViewById(R.id.imgThumbnail);
            txtCustomName = v.findViewById(R.id.txtCustomName);
            txtCategory = v.findViewById(R.id.txtCategory);
            txtDate = v.findViewById(R.id.txtDate);
            txtDetected = v.findViewById(R.id.txtDetected);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}