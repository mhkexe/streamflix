package com.tanasi.navigation.widget

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.MenuView
import androidx.core.view.forEachIndexed
import kotlin.math.min

class NavigationSlideMenuView(
    context: Context,
) : LinearLayout(context), MenuView {

    lateinit var navigationSlideView: NavigationSlideView

    private var childs = mutableListOf<NavigationSlideItemView>()
    var selectedItemId = 0
    var selectedItemPosition = 0

    lateinit var presenter: NavigationSlidePresenter
    private lateinit var menu: MenuBuilder

    private val layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    fun setHorizontal(horizontal: Boolean) {
        orientation = if (horizontal) HORIZONTAL else VERTICAL
        layoutParams.width = if (horizontal) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        requestLayout()
    }

    var menuGravity: Int
        get() = layoutParams.gravity
        set(value) {
            if (layoutParams.gravity != value) {
                layoutParams.gravity = value
                setLayoutParams(layoutParams)
            }
        }

    var menuSpacing: Int
        get() = dividerDrawable.intrinsicHeight
        set(value) {
            dividerDrawable = ShapeDrawable().apply {
                alpha = 0
                intrinsicHeight = value
            }
            showDividers = SHOW_DIVIDER_MIDDLE
        }


    init {
        orientation = VERTICAL
        setLayoutParams(layoutParams)
    }


    override fun initialize(menu: MenuBuilder) {
        this.menu = menu
    }

    override fun getWindowAnimations(): Int = 0


    fun buildMenuView() {
        removeAllViews()

        childs.clear()

        menu.forEachIndexed { i, item ->
            presenter.updateSuspended = true
            item.isCheckable = true
            presenter.updateSuspended = false

            val child = NavigationSlideItemView(context)
            childs.add(child)

            child.isFocusable = true
            child.isFocusableInTouchMode = true

            child.initialize(item as MenuItemImpl, 0)
            child.itemPosition = i

            if (orientation == HORIZONTAL && i == menu.size() - 1) {
                addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
            }
            addView(child)
        }

        navigationSlideView.buildNavigation()

        selectedItemPosition = min(menu.size() - 1, selectedItemPosition)
        menu.getItem(selectedItemPosition).isChecked = true
    }

    fun updateMenuView() {
        if (menu.size() != childs.size) {
            // The size has changed. Rebuild menu view from scratch.
            buildMenuView()
            return
        }

        menu.forEachIndexed { i, item ->
            if (item.isChecked) {
                selectedItemId = item.itemId
                selectedItemPosition = i
            }
        }

        childs.forEachIndexed { i, child ->
            presenter.updateSuspended = true
            child.initialize((menu.getItem(i) as MenuItemImpl), 0)
            presenter.updateSuspended = false
        }

        navigationSlideView.buildNavigation()
    }

    override fun hasFocus() = childs.any { it.hasFocus() }

    fun requestFirstItemFocus(): Boolean = childs.firstOrNull { it.visibility == VISIBLE }?.requestFocus() == true

    fun requestSelectedItemFocus(): Boolean = childs.firstOrNull { it.isSelected && it.visibility == VISIBLE }
        ?.requestFocus() == true

    fun requestItemFocus(itemId: Int): Boolean = childs.firstOrNull { it.id == itemId && it.visibility == VISIBLE }
        ?.requestFocus() == true

    fun requestLastItemFocus(): Boolean = childs.lastOrNull { it.visibility == VISIBLE }
        ?.requestFocus() == true

    fun focusPrevious(child: NavigationSlideItemView): Boolean {
        val index = childs.indexOf(child)
        return (index - 1 downTo 0)
            .asSequence()
            .map { childs[it] }
            .firstOrNull { it.visibility == VISIBLE }
            ?.requestFocus() == true
    }

    fun focusNext(child: NavigationSlideItemView): Boolean {
        val index = childs.indexOf(child)
        return (index + 1 until childs.size)
            .asSequence()
            .map { childs[it] }
            .firstOrNull { it.visibility == VISIBLE }
            ?.requestFocus() == true
    }

    fun focusPreviousFocusedItem(): Boolean {
        val focused = childs.firstOrNull { it.hasFocus() } ?: return requestSelectedItemFocus()
        return focusPrevious(focused)
    }

    fun focusNextFocusedItem(): Boolean {
        val focused = childs.firstOrNull { it.hasFocus() } ?: return requestSelectedItemFocus()
        return focusNext(focused)
    }

    fun open() {
        childs.forEach {
            it.isFocusable = true
            it.isFocusableInTouchMode = true

            it.open()
        }
    }

    fun close() {
        childs.forEach {
            it.isFocusable = it.isSelected
            it.isFocusableInTouchMode = it.isSelected

            it.close()
        }
    }

    fun forEach(action: (child: NavigationSlideItemView, item: MenuItem) -> Unit) {
        childs.forEach {
            action(it, it.itemData)
        }
    }
}