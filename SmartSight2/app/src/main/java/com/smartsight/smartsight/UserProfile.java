package com.smartsight.smartsight;



import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfile {

    @PrimaryKey
    public int id; // Only one user

    public String fullName;
    public String language;
}