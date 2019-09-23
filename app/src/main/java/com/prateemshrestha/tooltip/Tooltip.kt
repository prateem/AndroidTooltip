package com.prateemshrestha.tooltip

import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.DialogFragment

/**
 * Modified custom dialog fragment that shows a tooltip with the given [message] anchored
 * to the given [anchor] View at the attachment [position].
 *
 * @param message The message to display in the tooltip.
 * @param anchor The view to anchor the tooltip to. The Tooltip-View connection
 *   will be at the center of the rect-edge corresponding to the specified attachment
 *   [position] of the tooltip. If the center is not a feasible connection point, then the
 *   Tooltip-View connection will be as close to it as possible while displaying the full
 *   Tooltip.
 * @param position The position the Tooltip should adopt relative to the given [anchor].
 *   Defaults to [Tooltip.TooltipPosition.AUTO]
 */
@Suppress("unused")
class Tooltip(
    private val message: CharSequence,
    private val anchor: View,
    private val position: TooltipPosition = TooltipPosition.AUTO
) : DialogFragment() {
    companion object {
        const val TAG = "Tooltip"

        /**
         * Convenience method for showing a tooltip.
         *
         * @param activity [AppCompatActivity] to grab a fragment manager from
         * @param message The message to display in the tooltip.
         * @param anchor The view to anchor the tooltip to. The Tooltip-View connection
         *   will be at the center of the rect-edge corresponding to the specified attachment
         *   [position] of the tooltip. If the center is not a feasible connection point, then the
         *   Tooltip-View connection will be as close to it as possible while displaying the full
         *   Tooltip.
         * @param position The position the Tooltip should adopt relative to the given [anchor].
         *   Defaults to [Tooltip.TooltipPosition.AUTO]
         */
        fun show(
            activity: AppCompatActivity,
            message: CharSequence,
            anchor: View,
            position: TooltipPosition = TooltipPosition.AUTO
        ) = Tooltip(message, anchor, position).show(activity.supportFragmentManager, TAG)
    }

    enum class TooltipPosition { ABOVE, BELOW, AUTO }

    private val constraintSet = ConstraintSet()

    private lateinit var tooltipContainer: ConstraintLayout
    private lateinit var tooltipConnector: View
    private lateinit var tooltipBubble: FrameLayout
    private lateinit var tooltipMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TooltipDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.tooltip, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tooltipContainer = view.findViewById(R.id.tooltip_container)
        tooltipConnector = view.findViewById(R.id.tooltip_connector)
        tooltipBubble = view.findViewById(R.id.tooltip_bubble)
        tooltipMessage = view.findViewById(R.id.tooltip_message)

        tooltipMessage.text = message

        // If the background is touched, dismiss.
        view.setOnTouchListener { _, _ -> dismiss(); true }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val flagTranslucentStatus = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            val hasTranslucentStatusBar =
                window.attributes.flags and flagTranslucentStatus == flagTranslucentStatus
            val topInset =
                if (hasTranslucentStatusBar) 0
                else
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                        window.decorView.rootWindowInsets.systemWindowInsetTop
                    } else {
                        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                        if (resId != 0)
                            resources.getDimensionPixelSize(resId)
                        else 0
                    }
            val shadowPadding = resources.getDimensionPixelOffset(R.dimen.tooltip_shadow_padding)

            // We need measured widths and heights. So, everything below must be done in a runnable
            // because the Runnable is guaranteed to be called after the views have been laid out.
            view?.post {
                val containerLP = tooltipContainer.layoutParams as FrameLayout.LayoutParams

                // tooltipConnector requires both a vertical (A) and horizontal (B) constraint
                // tooltipBubble (C) requires a vertical constraint
                constraintSet.clone(tooltipContainer)

                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val anchorPosition = Point().also { point ->
                    IntArray(2).let { location ->
                        anchor.getLocationOnScreen(location)
                        point.set(location[0], location[1])
                    }
                }

                val tooltipConnectionEdge =
                    if (position == TooltipPosition.AUTO)
                    // We check the relative location of the vertical middle of the anchor to
                    // determine which attachment edge is ideal.
                        if ((anchorPosition.y + (anchor.height / 2) - topInset) > (screenHeight / 2))
                            TooltipPosition.ABOVE
                        else
                            TooltipPosition.BELOW
                    else
                        position

                // Set the vertical positioning information for connector and bubble - (A and C)
                if (tooltipConnectionEdge == TooltipPosition.ABOVE) {
                    val totalTooltipHeight = tooltipConnector.height + tooltipBubble.height
                    constraintSet.connect(
                        R.id.tooltip_connector, ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
                        0
                    )
                    constraintSet.connect(
                        R.id.tooltip_bubble, ConstraintSet.BOTTOM,
                        R.id.tooltip_connector, ConstraintSet.TOP,
                        0
                    )
                    containerLP.topMargin =
                        anchorPosition.y - totalTooltipHeight - topInset - shadowPadding
                } else {
                    constraintSet.setRotation(R.id.tooltip_connector, 180.0f)
                    constraintSet.connect(
                        R.id.tooltip_connector, ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID, ConstraintSet.TOP,
                        0
                    )
                    constraintSet.connect(
                        R.id.tooltip_bubble, ConstraintSet.TOP,
                        R.id.tooltip_connector, ConstraintSet.BOTTOM,
                        0
                    )
                    containerLP.topMargin =
                        anchorPosition.y + anchor.height - topInset - shadowPadding
                }

                // Now figure out horizontal position(s) for the connector and maybe the bubble too
                val viewAnchorMiddle = anchorPosition.x + (anchor.width / 2)
                val connectorSpacing = resources
                    .getDimensionPixelOffset(R.dimen.tooltip_connector_spacing)

                val halfScreenWidth = screenWidth / 2
                val connectorWidth = tooltipConnector.width
                val halfBubbleWidth = tooltipBubble.width / 2

                when {
                    // Is everything... by chance... perfectly centered? That's convenient.
                    viewAnchorMiddle == halfScreenWidth -> {
                        constraintSet.connect(
                            R.id.tooltip_connector, ConstraintSet.START,
                            ConstraintSet.PARENT_ID, ConstraintSet.START,
                            0
                        )
                        constraintSet.connect(
                            R.id.tooltip_connector, ConstraintSet.END,
                            ConstraintSet.PARENT_ID, ConstraintSet.END,
                            0
                        )
                    }

                    // No? Ah, well. Move things around sensibly, while trying to stay centered.
                    // Shift the connector (and bubble?) to the appropriate side as necessary...

                    // View anchor is somewhere to the left of screen-middle.
                    viewAnchorMiddle < halfScreenWidth -> {
                        var connectorLeft: Int
                        if (viewAnchorMiddle >= (tooltipBubble.left + connectorSpacing)) {
                            // This is easy. No need to shift tooltip bubble. Just position the connector.
                            connectorLeft = viewAnchorMiddle - (connectorWidth / 2)
                        } else {
                            // View anchor is somewhere on the outside of the bubble bounds.
                            // Do what we can to keep everything decently centered.
                            connectorLeft = viewAnchorMiddle - (connectorWidth / 2)
                            var bubbleLeft = connectorLeft - connectorSpacing
                            if (bubbleLeft < 0) {
                                // Tooltip needs to be placed at left screen edge.
                                connectorLeft = connectorSpacing
                                bubbleLeft = 0
                            } else {
                                // Try to keep the connector relatively centered with the bubble.
                                // While we have room to shift with side-bias maintained, do so
                                while ((bubbleLeft - connectorSpacing) > 0
                                    && (bubbleLeft - connectorSpacing + halfBubbleWidth) >= viewAnchorMiddle
                                ) { bubbleLeft -= connectorSpacing }
                            }

                            constraintSet.clear(R.id.tooltip_bubble, ConstraintSet.END)
                            constraintSet.setMargin(R.id.tooltip_bubble,
                                ConstraintSet.START, bubbleLeft)
                        }

                        constraintSet.connect(
                            R.id.tooltip_connector, ConstraintSet.START,
                            ConstraintSet.PARENT_ID, ConstraintSet.START,
                            connectorLeft
                        )
                    }

                    // View anchor is somewhere to the right of screen-middle.
                    else -> {
                        val connectorEndMargin: Int
                        if (viewAnchorMiddle <= (tooltipBubble.right - connectorSpacing)) {
                            // This is easy. No need to shift tooltip bubble. Just position the connector.
                            connectorEndMargin = screenWidth - viewAnchorMiddle - (connectorWidth / 2)
                        } else {
                            // View anchor is somewhere on the outside of the bubble bounds.
                            // Do what we can to keep everything decently centered.
                            var connectorRightEdge = viewAnchorMiddle + (connectorWidth / 2)
                            var bubbleRight = connectorRightEdge + connectorSpacing
                            if (bubbleRight > screenWidth) {
                                // Tooltip needs to be placed at right screen edge.
                                connectorRightEdge = screenWidth - connectorSpacing
                                bubbleRight = screenWidth
                            } else {
                                // Try to keep the connector relatively centered with the bubble.
                                // While we have room to shift with side-bias maintained, do so
                                while ((bubbleRight + connectorSpacing) < screenWidth
                                    && (bubbleRight + connectorSpacing - halfBubbleWidth) <= viewAnchorMiddle
                                ) { bubbleRight += connectorSpacing }
                            }

                            connectorEndMargin = screenWidth - connectorRightEdge
                            val bubbleRightMargin = screenWidth - bubbleRight

                            constraintSet.clear(R.id.tooltip_bubble, ConstraintSet.START)
                            constraintSet.setMargin(R.id.tooltip_bubble,
                                ConstraintSet.END, bubbleRightMargin)
                        }

                        constraintSet.connect(
                            R.id.tooltip_connector, ConstraintSet.END,
                            ConstraintSet.PARENT_ID, ConstraintSet.END,
                            connectorEndMargin
                        )
                    }
                }

                // Do the thing(s)
                constraintSet.applyTo(tooltipContainer)
                tooltipContainer.layoutParams = containerLP
            }
        }
    }

}