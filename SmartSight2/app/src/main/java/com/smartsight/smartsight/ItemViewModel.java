package com.example.smartsight;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class ItemViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final LiveData<List<SavedItem>> allItems;

    public ItemViewModel(@NonNull Application application) {
        super(application);
        repository = new AppRepository(application);
        allItems = repository.getAllItems();
    }

    public LiveData<List<SavedItem>> getAllItems() {
        return allItems;
    }

    public void insert(SavedItem item) {
        repository.insertItem(item);
    }

    public void delete(SavedItem item) {
        repository.deleteItem(item);
    }
}
