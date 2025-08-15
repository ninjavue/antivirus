package uz.csec.antivirus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class CleanerCircularProgressView extends View {
    private Paint bgPaint;
    private Paint progressPaint;
    private RectF oval;
    private float progress = 0f;
    private float bgProgress = 0f;
    private int[] gradientColors = {0xFF4B8AFF, 0xFF7B61FF, 0xFF00E0FF, 0xFF4B8AFF};
    private int[] bgColors = {0xFF232A36, 0xFF232A36};
    private float bgStrokeWidth = 22f;
    private float strokeWidth = 16f;
    private float shadowRadius = 8f;
    private int extraPad = 16;

    public CleanerCircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(bgStrokeWidth);
        bgPaint.setColor(0xFFF3F4F6);
        bgPaint.setShadowLayer(shadowRadius, 0, 0, 0xFFF3F4F6);
        setLayerType(LAYER_TYPE_SOFTWARE, bgPaint);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setShadowLayer(shadowRadius, 0, 0, 0xFF00BFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, progressPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokeWidth / 2f + shadowRadius;
        float left = pad + (extraPad / 2f);
        float top = pad + (extraPad / 2f);
        float right = w - pad - (extraPad / 2f);
        float bottom = h - pad - (extraPad / 2f);
        oval = new RectF(left, top, right, bottom);
        SweepGradient sweepGradient = new SweepGradient(w/2f, h/2f, gradientColors, null);
        progressPaint.setShader(sweepGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(oval, -90, bgProgress * 360, false, bgPaint);
        canvas.drawArc(oval, -90, progress * 360, false, progressPaint);
    }

    public void setProgress(float value) {
        this.progress = value;
        invalidate();
    }

    public void setBgProgress(float value) {
        this.bgProgress = value;
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

    public void animateBgProgress(float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(bgProgress, to);
        animator.setDuration(1200);
        animator.addUpdateListener(animation -> {
            setBgProgress((float) animation.getAnimatedValue());
        });
        animator.start();
    }
} 