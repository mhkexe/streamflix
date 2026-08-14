package com.nextservices.nextvision.fragments.favorites

import com.nextservices.nextvision.adapters.AppAdapter

data class FavoriteSectionHeader(
    val title: String,
    val section: FavoritesViewModel.Section,
) : AppAdapter.Item {
    override var itemType: AppAdapter.Type = AppAdapter.Type.FAVORITE_SECTION_HEADER
}
