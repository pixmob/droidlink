/*
 * Copyright (C) 2011 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.droidlink.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

/**
 * Component drawing an arrow head to the left.
 * @author Pixmob
 */
public class ArrowHeadView extends View {
    private final Paint arrowPaint;
    private Path arrowPath;
    
    public ArrowHeadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // Initialize paint attributes.
        String colorStr = attrs.getAttributeValue(null, "color");
        if (TextUtils.isEmpty(colorStr)) {
            colorStr = "#FFFFFF";
        }
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.parseColor(colorStr));
        arrowPaint.setStyle(Paint.Style.FILL);
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        if (changed) {
            // Update arrow based on dimensions.
            final int w = right - left;
            final int h = bottom - top;
            arrowPath = new Path();
            arrowPath.moveTo(w, 0);
            arrowPath.lineTo(0, h / 2);
            arrowPath.lineTo(w, h);
            arrowPath.close();
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (arrowPath != null) {
            canvas.drawPath(arrowPath, arrowPaint);
        }
    }
}
