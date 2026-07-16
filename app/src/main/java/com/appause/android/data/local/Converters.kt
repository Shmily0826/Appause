package com.appause.android.data.local

import androidx.room.TypeConverter

/**
 * Room Type Converters.
 *
 * What are TypeConverters?
 * - Room only knows how to store simple types: Int, Long, String, Float, Double, Blob.
 * - If you want to store a complex type (like List<String> or Date), you need a converter.
 * - A converter is a pair of functions: one to convert FROM the complex type TO a simple type,
 *   and one to convert back.
 *
 * Current status:
 * - Our v1 entities only use simple types (Long, String, Int), so no converters are needed yet.
 * - This class is included as a placeholder for future use.
 * - Example: if we later add a "packageNames: List<String>" column to AppGroup,
 *   we would use the stringListConverter below.
 */
class Converters {

    /**
     * Convert a List<String> to a single comma-separated String for storage.
     * Example: ["com.app.a", "com.app.b"] → "com.app.a,com.app.b"
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(",") ?: ""
    }

    /**
     * Convert a comma-separated String back to a List<String>.
     * Example: "com.app.a,com.app.b" → ["com.app.a", "com.app.b"]
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return if (value.isNullOrEmpty()) emptyList() else value.split(",")
    }
}
