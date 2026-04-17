package com.example.smartsight;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class NoteViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final LiveData<List<SavedNote>> allNotes;

    public NoteViewModel(@NonNull Application application) {
        super(application);
        repository = new AppRepository(application);
        allNotes   = repository.getAllNotes();
    }

    public LiveData<List<SavedNote>> getAllNotes() {
        return allNotes;
    }

    public void insert(SavedNote note) {
        repository.insertNote(note);
    }

    public void delete(SavedNote note) {
        repository.deleteNote(note);
    }
}
