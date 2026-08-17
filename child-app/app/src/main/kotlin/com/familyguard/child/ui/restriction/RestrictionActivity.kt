package com.familyguard.child.ui.restriction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.familyguard.child.domain.EnforcementResult
import com.familyguard.child.ui.theme.FamilyGuardChildTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The screen that covers a restricted app. Launched by `MonitorForegroundService` with
 * `FLAG_ACTIVITY_NEW_TASK` (a service has no activity task of its own to launch from).
 *
 * [EnforcementResult] is deliberately kept out of `Intent` extras directly (it lives in the
 * Android-free `domain` package and is not `Parcelable`/`Serializable` by design — see
 * `domain/EnforcementDecider.kt`'s KDoc on keeping that package framework-free) — instead
 * this activity's [newIntent] flattens the specific fields it needs into plain extras.
 */
@AndroidEntryPoint
class RestrictionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLimitExceeded = intent.getBooleanExtra(EXTRA_IS_LIMIT_EXCEEDED, false)
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val dailyLimitMinutes = intent.getIntExtra(EXTRA_DAILY_LIMIT_MINUTES, 0)
        val usedMinutes = intent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()

        setContent {
            FamilyGuardChildTheme {
                RestrictionScreen(
                    isLimitExceeded = isLimitExceeded,
                    reason = reason,
                    dailyLimitMinutes = dailyLimitMinutes,
                    usedMinutes = usedMinutes,
                    packageName = packageName,
                    onGoHome = {
                        startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_IS_LIMIT_EXCEEDED = "is_limit_exceeded"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
        private const val EXTRA_USED_MINUTES = "used_minutes"
        private const val EXTRA_PACKAGE_NAME = "package_name"

        fun newIntent(context: Context, result: EnforcementResult): Intent {
            val intent = Intent(context, RestrictionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when (result) {
                is EnforcementResult.Blocked -> {
                    intent.putExtra(EXTRA_IS_LIMIT_EXCEEDED, false)
                    intent.putExtra(EXTRA_REASON, result.reason)
                    intent.putExtra(EXTRA_PACKAGE_NAME, result.packageName)
                }
                is EnforcementResult.LimitExceeded -> {
                    intent.putExtra(EXTRA_IS_LIMIT_EXCEEDED, true)
                    intent.putExtra(EXTRA_REASON, result.reason)
                    intent.putExtra(EXTRA_DAILY_LIMIT_MINUTES, result.dailyLimitMinutes)
                    intent.putExtra(EXTRA_USED_MINUTES, result.usedMinutes)
                    intent.putExtra(EXTRA_PACKAGE_NAME, result.packageName)
                }
                EnforcementResult.Allowed -> Unit // Never called with Allowed; defensive no-op.
            }
            return intent
        }
    }
}
