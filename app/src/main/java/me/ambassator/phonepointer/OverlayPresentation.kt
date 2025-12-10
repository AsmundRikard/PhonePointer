package me.ambassator.phonepointer

import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayPresentation(context: Context, display: Display) : Presentation(context, display) {

    private lateinit var cursorView: ImageView
    private lateinit var appGridContainer: ScrollView
    private lateinit var appGridLayout: LinearLayout
    private lateinit var rootContainer: FrameLayout
    
    private var screenWidth = 0
    private var screenHeight = 0
    private val appItems = mutableListOf<View>()
    
    // Normalized position (0.0 to 1.0)
    var cursorX = 0.5f
    var cursorY = 0.5f
    
    var isAppDrawerVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                         WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                         WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        val displayMetrics = android.util.DisplayMetrics()
        display.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        rootContainer = FrameLayout(context)
        rootContainer.setBackgroundColor(Color.TRANSPARENT)
        
        // App Grid (hidden by default) - vertical scroll with rows
        appGridContainer = ScrollView(context)
        appGridContainer.setBackgroundColor(Color.argb(230, 20, 20, 20))
        appGridContainer.visibility = View.GONE
        appGridContainer.isFillViewport = true
        
        appGridLayout = LinearLayout(context)
        appGridLayout.orientation = LinearLayout.VERTICAL
        appGridLayout.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        appGridLayout.setPadding(32, 32, 32, 32)
        appGridContainer.addView(appGridLayout)
        
        val gridParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootContainer.addView(appGridContainer, gridParams)
        
        // Cursor (always on top)
        cursorView = ImageView(context)
        cursorView.setImageResource(android.R.drawable.arrow_down_float)
        cursorView.rotation = -90f
        cursorView.setColorFilter(Color.RED)
        
        val cursorParams = FrameLayout.LayoutParams(24, 24)
        cursorParams.gravity = Gravity.TOP or Gravity.LEFT
        rootContainer.addView(cursorView, cursorParams)
        
        setContentView(rootContainer)
        
        // Load apps after layout is set
        loadApps()
    }
    
    private fun loadApps() {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(pm).toString() }
        
        appGridLayout.removeAllViews()
        appItems.clear()
        
        val columnsCount = 6
        val itemWidth = (screenWidth - 64) / columnsCount // Subtract padding
        
        var currentRow: LinearLayout? = null
        var itemsInRow = 0
        
        for (app in sortedApps) {
            // Create new row if needed
            if (currentRow == null || itemsInRow >= columnsCount) {
                currentRow = LinearLayout(context)
                currentRow.orientation = LinearLayout.HORIZONTAL
                currentRow.gravity = Gravity.CENTER_HORIZONTAL
                appGridLayout.addView(currentRow)
                itemsInRow = 0
            }
            
            val itemLayout = LinearLayout(context)
            itemLayout.orientation = LinearLayout.VERTICAL
            itemLayout.gravity = Gravity.CENTER
            itemLayout.setPadding(8, 16, 8, 16)
            
            val iconView = ImageView(context)
            iconView.setImageDrawable(app.loadIcon(pm))
            val iconParams = LinearLayout.LayoutParams(72, 72)
            iconParams.gravity = Gravity.CENTER
            iconView.layoutParams = iconParams
            itemLayout.addView(iconView)
            
            val labelView = TextView(context)
            labelView.text = app.loadLabel(pm)
            labelView.setTextColor(Color.WHITE)
            labelView.textSize = 9f
            labelView.gravity = Gravity.CENTER
            labelView.maxLines = 1
            labelView.setSingleLine(true)
            itemLayout.addView(labelView)
            
            // Store package/activity info for launching
            itemLayout.tag = app
            
            val itemParams = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            currentRow.addView(itemLayout, itemParams)
            
            appItems.add(itemLayout)
            itemsInRow++
        }
    }
    
    fun toggleAppDrawer() {
        isAppDrawerVisible = !isAppDrawerVisible
        appGridContainer.visibility = if (isAppDrawerVisible) View.VISIBLE else View.GONE
    }
    
    fun getAppAtCursor(): ResolveInfo? {
        if (!isAppDrawerVisible) return null
        
        val pixelX = (cursorX * screenWidth).toInt()
        val pixelY = (cursorY * screenHeight).toInt()
        
        for (item in appItems) {
            val location = IntArray(2)
            item.getLocationInWindow(location)
            
            val left = location[0]
            val top = location[1]
            val right = left + item.width
            val bottom = top + item.height
            
            if (pixelX in left..right && pixelY in top..bottom) {
                return item.tag as? ResolveInfo
            }
        }
        return null
    }
    
    fun launchAppAtCursor() {
        val app = getAppAtCursor() ?: return
        
        val launchIntent = Intent(Intent.ACTION_MAIN)
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        launchIntent.setClassName(app.activityInfo.packageName, app.activityInfo.name)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.launchDisplayId = display.displayId
        }
        
        try {
            context.startActivity(launchIntent, options.toBundle())
            toggleAppDrawer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCursor(dx: Float, dy: Float) {
        val sensitivity = 1.0f 
        
        cursorX = (cursorX + dx * sensitivity).coerceIn(0f, 1f)
        cursorY = (cursorY + dy * sensitivity).coerceIn(0f, 1f)
        
        val pixelX = (cursorX * screenWidth).toInt()
        val pixelY = (cursorY * screenHeight).toInt()
        
        cursorView.translationX = pixelX.toFloat()
        cursorView.translationY = pixelY.toFloat()
    }
    
    fun setCursorPos(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, 1f)
        cursorY = y.coerceIn(0f, 1f)
        val pixelX = (cursorX * screenWidth).toInt()
        val pixelY = (cursorY * screenHeight).toInt()
        cursorView.translationX = pixelX.toFloat()
        cursorView.translationY = pixelY.toFloat()
    }
}
