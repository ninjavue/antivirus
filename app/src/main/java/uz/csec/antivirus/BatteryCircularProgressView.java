package uz.csec.antivirus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class BatteryCircularProgressView extends View {
    private Paint bgPaint;
    private Paint progressPaint;
    private RectF batteryBodyRect;
    private Path batteryPath;
    private float progress = 0f;
    private float strokeWidth = 5f;
    private float cornerRadius = 30f;
    private float capHeight = 40f;
    private float capWidthRatio = 0.3f;
    private int extraPad = 5;

    public BatteryCircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFFFFFFFF); // oq

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(0xFFFE945A); // orange #fe945a
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokeWidth / 2f;
        float left = pad + (extraPad / 2f);
        float top = pad + (extraPad / 2f) + capHeight; // Start below cap
        float right = w - pad - (extraPad / 2f);
        float bottom = h - pad - (extraPad / 2f);
        batteryBodyRect = new RectF(left, top, right, bottom);

        batteryPath = new Path();
        // Battery body with rounded corners
        batteryPath.addRoundRect(batteryBodyRect, cornerRadius, cornerRadius, Path.Direction.CW);

        // --- Battery cap with top-left & top-right radius ---
        float capLeft = left + (right - left) * (1 - capWidthRatio) / 2;
        float capRight = right - (right - left) * (1 - capWidthRatio) / 2;
        float capTop = top - capHeight;
        float capBottom = top;

        RectF capRect = new RectF(capLeft, capTop, capRight, capBottom);

        float[] capRadii = new float[]{
                cornerRadius, cornerRadius, // top-left
                cornerRadius, cornerRadius, // top-right
                0f, 0f,                     // bottom-right
                0f, 0f                      // bottom-left
        };

        Path capPath = new Path();
        capPath.addRoundRect(capRect, capRadii, Path.Direction.CW);

        // combine both
        batteryPath.addPath(capPath);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw battery outline and cap
        canvas.drawPath(batteryPath, bgPaint);

        // Draw progress (filled portion from bottom up)
        if (progress > 0) {
            float progressHeight = batteryBodyRect.height() * progress;
            RectF progressRect = new RectF(
                    batteryBodyRect.left,
                    batteryBodyRect.bottom - progressHeight,
                    batteryBodyRect.right,
                    batteryBodyRect.bottom
            );

            float[] progressRadii = new float[]{
                    0f, 0f,                      // top-left
                    0f, 0f,                      // top-right
                    cornerRadius, cornerRadius,  // bottom-right
                    cornerRadius, cornerRadius   // bottom-left
            };

            Path progressPath = new Path();
            progressPath.addRoundRect(progressRect, progressRadii, Path.Direction.CW);
            canvas.drawPath(progressPath, progressPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec) + extraPad;
        int h = MeasureSpec.getSize(heightMeasureSpec) + extraPad;
        setMeasuredDimension(w, h);
    }

    public void setProgress(float value) {
        this.progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public void animateProgress(float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(progress, to);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> {
            setProgress((float) animation.getAnimatedValue());
        });
        animator.start();
    }
}
