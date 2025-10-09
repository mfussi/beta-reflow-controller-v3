package com.tangentlines.reflowclient.shared.storage

import com.tangentlines.reflowclient.shared.model.EditableProfile
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/** Serialize to JSON string matching server schema. */
fun toJson(profile: EditableProfile): String = json.encodeToString(EditableProfile.serializer(), profile)

/** Parse JSON string into profile. */
fun fromJson(text: String): EditableProfile = json.decodeFromString(EditableProfile.serializer(), text)

/*
// Optional JVM helpers:
fun saveToFile(profile: EditableProfile, file: File) {
    file.writeText(toJson(profile))
}

fun loadFromFile(file: File): EditableProfile = fromJson(file.readText())
 */