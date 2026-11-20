package com.vpnx.dope.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.nav.core.NavKey

class Navigator(
    val backStack: MutableList<NavKey>
) {
    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLastOrNull()
            true
        } else {
            false
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()
}

@Composable
fun rememberNavigator(initialKey: NavKey = Route.Home): Navigator {
    val backStack = remember { mutableListOf(initialKey) }
    return remember(backStack) { Navigator(backStack) }
}
