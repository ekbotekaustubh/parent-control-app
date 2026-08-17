package com.familyguard.parent.data.local.db

import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.DashboardResponse
import com.familyguard.shared.dto.UsageTodayItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun DashboardResponse.toCacheEntity(childId: String, json: Json, cachedAtEpochMillis: Long): DashboardCacheEntity =
    DashboardCacheEntity(
        childId = childId,
        deviceStatus = device.status,
        deviceOnline = device.online,
        lastSeenAt = device.lastSeenAt,
        lastSyncAt = device.lastSyncAt,
        rulesJson = json.encodeToString(rules),
        usageTodayJson = json.encodeToString(usageToday),
        cachedAtEpochMillis = cachedAtEpochMillis,
    )

fun DashboardCacheEntity.decodeRules(json: Json): List<AppRuleResponse> =
    runCatching { json.decodeFromString<List<AppRuleResponse>>(rulesJson) }.getOrDefault(emptyList())

fun DashboardCacheEntity.decodeUsageToday(json: Json): List<UsageTodayItem> =
    runCatching { json.decodeFromString<List<UsageTodayItem>>(usageTodayJson) }.getOrDefault(emptyList())
