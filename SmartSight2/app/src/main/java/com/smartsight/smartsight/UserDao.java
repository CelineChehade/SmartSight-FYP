package com.smartsight.smartsight;


import androidx.lifecycle.LiveData;
import androidx.room.*;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserProfile user);

    @Query("SELECT * FROM user_profile LIMIT 1")
    LiveData<UserProfile> getUserProfile();
}