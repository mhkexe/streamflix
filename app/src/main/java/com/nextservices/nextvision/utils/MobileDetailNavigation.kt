package com.nextservices.nextvision.utils

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.navOptions
import com.nextservices.nextvision.R

fun NavController.navigateMobileDetail(@IdRes destinationId: Int, args: Bundle? = null) {
    navigate(destinationId, args, navOptions {
        anim {
            enter = R.anim.detail_enter
            exit = R.anim.detail_exit
            popEnter = R.anim.detail_pop_enter
            popExit = R.anim.detail_pop_exit
        }
    })
}
