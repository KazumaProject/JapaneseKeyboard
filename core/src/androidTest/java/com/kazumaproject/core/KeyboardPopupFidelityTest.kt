package com.kazumaproject.core

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.core.ui.skin.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Exports production drawables and native TextView layout without rescaling screenshots. */
@RunWith(AndroidJUnit4::class)
class KeyboardPopupFidelityTest {
    @Test fun exportAllKanaDirectionsAndTypography() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val configuration = Configuration(app.resources.configuration).apply { densityDpi=480; fontScale=1f }
        val context = app.createConfigurationContext(configuration)
        val output = File(app.getExternalFilesDir(null),"popup-fidelity").apply { mkdirs() }
        val metadata = JSONArray()
        instrumentation.runOnMainSync {
            for (id in listOf(KeyboardSkinId.CUPERTINO_LIGHT,KeyboardSkinId.CUPERTINO_DARK)) {
                val skin = requireNotNull(KeyboardSkinRegistry.find(id))
                for ((direction,label) in mapOf(PopupDirection.LEFT to "に",PopupDirection.TOP to "ぬ",
                    PopupDirection.RIGHT to "ね",PopupDirection.BOTTOM to "の",PopupDirection.CENTER to "か")) {
                    val geometry = SkinPopupGeometry.resolve(258,168,direction,direction!=PopupDirection.CENTER)
                    val bounds = geometry.bounds
                    val view = KeyWindowLayout(context).apply {
                        skinId=id
                        skinDirection=direction
                        skinSelected=direction==PopupDirection.CENTER
                        elevation=0f
                        val inset=geometry.textInsets
                        setPadding(inset.left,inset.top,inset.right,inset.bottom)
                    }
                    val text = TextView(context).apply {
                        this.text=label
                        textSize=28f
                        includeFontPadding=false
                        gravity=Gravity.CENTER
                        setTextColor(if(direction==PopupDirection.CENTER) skin.palette.selectionText else skin.palette.text)
                        skin.configurePopupText(this,direction!=PopupDirection.CENTER)
                    }
                    view.addView(text,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
                    view.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(),View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(bounds.height(),View.MeasureSpec.EXACTLY))
                    view.layout(0,0,bounds.width(),bounds.height())
                    val bitmap=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val name="${id.preferenceValue}-${direction.name.lowercase()}"
                    File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    val surface=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888)
                    skin.popupDrawable(context.resources,direction,direction==PopupDirection.CENTER).apply {
                        setBounds(0,0,bounds.width(),bounds.height());draw(Canvas(surface))
                    }
                    File(output,"$name-surface.png").outputStream().use { surface.compress(Bitmap.CompressFormat.PNG,100,it) }
                    metadata.put(JSONObject().put("name",name).put("text",label)
                        .put("offset_x",bounds.left).put("offset_y",bounds.top)
                        .put("width",bounds.width()).put("height",bounds.height())
                        .put("baseline",text.top+text.baseline+text.translationY).put("text_size_px",text.textSize))
                }
            }
        }
        instrumentation.runOnMainSync {
            for (label in listOf("あ","か","に","ぬ","ね","の","q","e","p","a","l")) {
                for (halfPoints in 40..84) {
                    val text = TextView(context).apply {
                        this.text=label; textSize=halfPoints/2f; includeFontPadding=false
                        gravity=Gravity.CENTER; setTextColor(android.graphics.Color.BLACK)
                    }
                    text.measure(View.MeasureSpec.makeMeasureSpec(258,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(168,View.MeasureSpec.EXACTLY))
                    text.layout(0,0,258,168)
                    val image=Bitmap.createBitmap(258,168,Bitmap.Config.ARGB_8888)
                    text.draw(Canvas(image))
                    File(output,"font-$label-$halfPoints.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
            }
        }
        File(output,"layout.json").writeText(metadata.toString(2))
    }
    @Test fun exportCompleteGuideAndQwertyPreview() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val app=ins.targetContext
        val context=app.createConfigurationContext(Configuration(app.resources.configuration).apply { densityDpi=480;fontScale=1f })
        val output=File(app.getExternalFilesDir(null),"popup-fidelity").apply {mkdirs()}
        val previewMetadata=JSONArray()
        ins.runOnMainSync {
            for(id in listOf(KeyboardSkinId.CUPERTINO_LIGHT,KeyboardSkinId.CUPERTINO_DARK)) {
                val skin=requireNotNull(KeyboardSkinRegistry.find(id))
                val cross=Bitmap.createBitmap(774,504,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(cross)
                val surfaceCross=Bitmap.createBitmap(774,504,Bitmap.Config.ARGB_8888)
                val surfaceCanvas=Canvas(surfaceCross)
                for((direction,label) in mapOf(PopupDirection.LEFT to "き",PopupDirection.TOP to "く",PopupDirection.CENTER to "か",PopupDirection.RIGHT to "け",PopupDirection.BOTTOM to "こ")) {
                    val geometry=SkinPopupGeometry.resolve(258,168,direction,false)
                    val text=TextView(context).apply {this.text=label;textSize=28f;includeFontPadding=false;gravity=Gravity.CENTER
                        setTextColor(if(direction==PopupDirection.CENTER)skin.palette.selectionText else skin.palette.text)
                        skin.configurePopupText(this,false)
                    }
                    val cell=FrameLayout(context).apply {
                        background=skin.guideDrawable(resources,direction,direction==PopupDirection.CENTER)
                        addView(text,FrameLayout.LayoutParams(-1,-1))
                    }
                    cell.measure(View.MeasureSpec.makeMeasureSpec(258,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(168,View.MeasureSpec.EXACTLY));cell.layout(0,0,258,168)
                    canvas.save();canvas.translate((258+geometry.bounds.left).toFloat(),(168+geometry.bounds.top).toFloat());cell.draw(canvas);canvas.restore()
                    surfaceCanvas.save();surfaceCanvas.translate((258+geometry.bounds.left).toFloat(),(168+geometry.bounds.top).toFloat());cell.background.draw(surfaceCanvas);surfaceCanvas.restore()
                }
                File(output,"${id.preferenceValue}-guide.png").outputStream().use {cross.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(output,"${id.preferenceValue}-guide-surface.png").outputStream().use {surfaceCross.compress(Bitmap.CompressFormat.PNG,100,it)}
                for((label,keyLeft) in listOf("q" to 20,"p" to 1189,"e" to 279,"a" to 84,"l" to 1124)) {
                    val keyWidth=if(label=="q")111 else 112
                    val keyTop=if(label=="a" || label=="l")2157 else 1989
                    val geometry=requireNotNull(skin.keyPreview(context.resources,keyWidth,135,keyLeft,1320,keyTop>1989))
                    val width=geometry.width;val height=geometry.height;val dx=geometry.xOffset
                    previewMetadata.put(JSONObject().put("name","${id.preferenceValue}-preview-$label")
                        .put("key_left",keyLeft).put("key_top",keyTop).put("key_width",keyWidth).put("key_height",135)
                        .put("x_offset",dx).put("y_offset",geometry.yOffset).put("width",width).put("height",height))
                    val text=TextView(context).apply {this.text=label;textSize=32f;includeFontPadding=false
                        gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;setPadding(0,18,0,0);setTextColor(skin.palette.text)
                        background=geometry.background
                        skin.configurePreviewText(this)
                        val shift=((-dx+keyWidth/2f)-width/2f)*.2f
                        setPadding((shift*2).toInt().coerceAtLeast(0),paddingTop,(-shift*2).toInt().coerceAtLeast(0),0)
                    }
                    text.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));text.layout(0,0,width,height)
                    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);text.draw(Canvas(bitmap))
                    File(output,"${id.preferenceValue}-preview-$label.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    val surface=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    text.background.setBounds(0,0,width,height);text.background.draw(Canvas(surface))
                    File(output,"${id.preferenceValue}-preview-$label-surface.png").outputStream().use {surface.compress(Bitmap.CompressFormat.PNG,100,it)}
                }
            }
        }
        File(output,"preview-layout.json").writeText(previewMetadata.toString(2))
    }

}
