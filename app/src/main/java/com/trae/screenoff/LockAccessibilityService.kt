package com.trae.screenoff

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockAccessibilityService? = null
            private set

        /** 触发系统锁屏（熄屏），需要用户先在无障碍设置里开启本服务 */
        fun lock() {
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
