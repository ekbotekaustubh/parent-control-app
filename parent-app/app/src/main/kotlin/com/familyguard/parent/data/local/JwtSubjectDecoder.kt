package com.familyguard.parent.data.local

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Extracts the `sub` claim (parent id) from a parent access JWT, purely for local display /
 * cache-keying convenience — e.g. `POST /auth/login` (docs/api-spec.md) returns only tokens,
 * not a `parentId`, unlike `/auth/signup`. This does NOT verify the token's signature and
 * must never be treated as an authorization decision: the backend always re-derives and
 * verifies identity server-side from the signed JWT on every call
 * (docs/api-spec.md's anti-IDOR rule) — this is client-side convenience only.
 */
object JwtSubjectDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    fun decodeSubject(jwt: String): String? {
        val parts = jwt.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val payloadJson = json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8)).jsonObject
            payloadJson["sub"]?.jsonPrimitive?.content
        }.getOrNull()
    }
}
