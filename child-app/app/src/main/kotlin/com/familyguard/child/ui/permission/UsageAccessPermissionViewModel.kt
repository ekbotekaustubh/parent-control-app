package com.familyguard.child.ui.permission

import androidx.lifecycle.ViewModel
import com.familyguard.child.service.UsageStatsPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UsageAccessPermissionViewModel @Inject constructor(
    private val permissionChecker: UsageStatsPermissionChecker,
) : ViewModel() {
    fun isGranted(): Boolean = permissionChecker.isGranted()
}
