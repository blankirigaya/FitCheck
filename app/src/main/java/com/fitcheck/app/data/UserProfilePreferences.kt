package com.fitcheck.app.data

import android.content.Context

/** Small local-only profile used as context by every styling prompt. */
data class UserProfile(
    val name: String,
    val age: Int,
    val gender: String,
    val profession: String
)

object UserProfilePreferences {
    private const val FILE = "fitcheck_user_profile"
    private const val AGE = "age"
    private const val NAME = "name"
    private const val GENDER = "gender"
    private const val PROFESSION = "profession"

    fun read(context: Context): UserProfile? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val age = prefs.getInt(AGE, -1)
        val name = prefs.getString(NAME, null)
        val gender = prefs.getString(GENDER, null)
        val profession = prefs.getString(PROFESSION, null)
        return if (!name.isNullOrBlank() && age > 0 && !gender.isNullOrBlank() && !profession.isNullOrBlank()) UserProfile(name, age, gender, profession) else null
    }

    fun save(context: Context, profile: UserProfile) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(NAME, profile.name)
            .putInt(AGE, profile.age)
            .putString(GENDER, profile.gender)
            .putString(PROFESSION, profile.profession)
            .apply()
    }
}
