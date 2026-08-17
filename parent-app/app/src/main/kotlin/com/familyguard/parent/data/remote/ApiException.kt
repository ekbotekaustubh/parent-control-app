package com.familyguard.parent.data.remote

/** A network/API failure already translated into a user-presentable message. */
class ApiException(message: String, val code: String? = null) : Exception(message)
