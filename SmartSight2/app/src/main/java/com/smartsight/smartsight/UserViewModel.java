package com.smartsight.smartsight;


import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class UserViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final LiveData<UserProfile> user;

    public UserViewModel(@NonNull Application application) {
        super(application);
        repository = new AppRepository(application);
        user = repository.getUserProfile();
    }

    public LiveData<UserProfile> getUser() {
        return user;
    }

    public void insertUser(UserProfile userProfile) {
        repository.insertUser(userProfile);
    }
}