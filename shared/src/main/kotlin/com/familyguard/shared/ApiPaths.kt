package com.familyguard.shared

/**
 * Path constants shared by the backend's route registration and both Android apps'
 * Retrofit service interfaces, so a typo can't silently diverge between them.
 * All are relative to the API base path "/api/v1".
 */
object ApiPaths {
    const val BASE = "/api/v1"

    // Auth (parent)
    const val AUTH_SIGNUP = "auth/signup"
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REFRESH = "auth/refresh"
    const val AUTH_LOGOUT = "auth/logout"

    // Children
    const val CHILDREN = "children"
    const val CHILD_BY_ID = "children/{childId}"

    // Pairing
    const val PAIRING_CODES = "children/{childId}/pairing-codes"
    const val PAIRING_CLAIM = "pairing/claim"
    const val PAIRING_CLAIM_STATUS = "pairing/claim/{deviceId}/status"
    const val DEVICE_APPROVE = "devices/{deviceId}/approve"
    const val DEVICE_TOKEN_EXCHANGE = "devices/{deviceId}/token-exchange"
    const val DEVICE_TOKEN_REFRESH = "devices/token/refresh"
    const val DEVICES = "devices"
    const val DEVICE_REVOKE = "devices/{deviceId}/revoke"

    // Rules
    const val CHILD_APP_RULE = "children/{childId}/apps/{packageName}/rule"
    const val CHILD_RULES = "children/{childId}/rules"

    // Device-facing
    const val DEVICE_CONFIG = "device/config"
    const val DEVICE_USAGE_SYNC = "device/usage-sync"
    const val DEVICE_HEARTBEAT = "device/heartbeat"
    const val DEVICE_ACCESS_REQUESTS = "device/access-requests"

    // Access requests (parent)
    const val ACCESS_REQUESTS = "access-requests"
    const val ACCESS_REQUEST_RESOLVE = "access-requests/{requestId}/resolve"

    // Dashboard
    const val CHILD_DASHBOARD = "children/{childId}/dashboard"

    const val CLAIM_TICKET_HEADER = "X-Claim-Ticket"
}
