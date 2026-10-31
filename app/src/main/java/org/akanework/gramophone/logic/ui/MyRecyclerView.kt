/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.media3.common.util.Log
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.FixOnItemTouchListenerRecyclerView
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.GridPaddingDecoration

open class MyRecyclerView : FixOnItemTouchListenerRecyclerView {
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) :
            super(context, attributeSet, defStyleAttr) {
        val arr = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.MyRecyclerView, defStyleAttr, 0
        )
        if (arr.getBoolean(R.styleable.MyRecyclerView_hasFixedSize, false)) {
            setHasFixedSize(true)
        }
        if (arr.getBoolean(R.styleable.MyRecyclerView_useGridPaddingDecoration, false)) {
            addItemDecoration(GridPaddingDecoration(context))
        }
        arr.recycle()
    }

    constructor(context: Context, attributeSet: AttributeSet?)
            : this(context, attributeSet, 0)

    constructor(context: Context) : this(context, null)

    private var lastWidth = 0
    private var deferredFastScrollBuilder: (() -> FastScroller)? = null
    private var ah: MyAnimationHelper? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        if (lastWidth != width) {
            adapter.dispatchToAdapter { it.onWidthChanged(width) }
            lastWidth = width
        }
        if (width == 0)
            Log.w(
                "MyRecyclerView",
                "width is 0 in doOnLayout??? will not set fast scroll builder!!"
            )
        else {
            deferredFastScrollBuilder?.let {
                it.invoke()
                deferredFastScrollBuilder = null
            }
        }
        super.onLayout(changed, l, t, r, b)
    }

    private fun RecyclerView.Adapter<*>?.dispatchToAdapter(action: (Adapter<*>) -> Unit) {
        if (this is ConcatAdapter) {
            adapters.forEach { it.dispatchToAdapter(action) }
        } else if (this is Adapter<*>) {
            action(this)
        }
    }

    fun startSmoothScrollCompat(scroller: SmoothScroller) {
        layoutManager?.startSmoothScroll(scroller)
    }

    fun scrollToPositionWithOffsetCompat(position: Int, offset: Int) {
        (layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, offset)
    }

    fun fastScroll(popupTextProvider: PopupTextProvider?, ihh: ItemHeightHelper?) {
        // fast scroll builders should be called only after layout phase
        val fsBuilder = {
            FastScrollerBuilder(this)
                .setViewHelper(RecyclerViewHelper(this, popupTextProvider, ihh))
                .also { builder ->
                    builder.setAnimationHelper(MyAnimationHelper().also {
                        ah = it
                    })
                }
                .useMd2Style()
                .setTrackDrawable(
                    AppCompatResources.getDrawable(
                        context,
                        R.drawable.ic_transparent
                    )!!
                )
                .build()
        }
        if (width == 0)
            this.deferredFastScrollBuilder = fsBuilder
        else
            fsBuilder()
    }

    override fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        super.setAdapter(adapter)
        if (width != 0)
            adapter.dispatchToAdapter { it.onWidthChanged(width) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Let's clean up some resources by pretending the view is gone (a detached view is as good
        // as gone for our purposes anyway)
        adapter?.onDetachedFromRecyclerView(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // We're so back
        adapter?.onAttachedToRecyclerView(this)
    }

    abstract class Adapter<VH : ViewHolder> : RecyclerView.Adapter<VH>() {
        final override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            onAttachedToRecyclerView(recyclerView as MyRecyclerView)
        }

        final override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            onDetachedFromRecyclerView(recyclerView as MyRecyclerView)
        }

        open fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {}
        open fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {}
        open fun onWidthChanged(width: Int) {}
    }

    private inner class MyAnimationHelper : DefaultAnimationHelper(this) {
        var trackViewCache: View? = null
        var thumbViewCache: View? = null

        override fun showScrollbar(trackView: View, thumbView: View) {
            super.showScrollbar(trackView, thumbView)
            this.trackViewCache = trackView
            this.thumbViewCache = thumbView
        }

        override fun hideScrollbar(trackView: View, thumbView: View) {
            super.hideScrollbar(trackView, thumbView)
            this.trackViewCache = null
            this.thumbViewCache = null
        }

        fun hideScrollbar() {
            if (trackViewCache != null)
                hideScrollbar(trackViewCache!!, thumbViewCache!!)
        }
    }
}

abstract class DefaultItemHeightHelper : ItemHeightHelper {
    companion object {
        fun concatItemHeightHelper(
            one: ItemHeightHelper,
            oneCount: () -> Int,
            two: ItemHeightHelper
        ): ItemHeightHelper {
            return object : DefaultItemHeightHelper() {
                override fun getItemHeightFromZeroTo(to: Int): Int {
                    val oc = oneCount()
                    val oh = one.getItemHeightFromZeroTo(to.coerceAtMost(oc))
                    val th = if (to >= oc) two.getItemHeightFromZeroTo(to - oc) else 0
                    return (oh + th)
                }
            }
        }
    }
}