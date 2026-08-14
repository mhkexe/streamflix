package com.nextservices.nextvision.models

import com.nextservices.nextvision.adapters.AppAdapter

sealed interface Show : AppAdapter.Item {
    var isFavorite: Boolean
}
