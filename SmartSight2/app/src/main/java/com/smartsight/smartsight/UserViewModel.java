package com.example.smartsight;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class UserViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final LiveData<UserProfile> userProfile;

    public UserViewModel(@NonNull Application application) {
        super(application);
        repository = new AppRepository(application);
        userProfile = repository.getUserProfile();
    }

    public LiveData<UserProfile> getUserProfile() {
        return userProfile;
    }

    public void insertUser(UserProfile userProfile) {
        repository.insertUser(userProfile);
    }
}
