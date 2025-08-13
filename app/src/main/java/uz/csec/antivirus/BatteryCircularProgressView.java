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

public class BatteryCircularProgressView extends View {
    private Paint bgPaint;
    private Paint progressPaint;
    private RectF oval;
    private float progress = 0f;
    private int[] gradientColors = {0xFF4B8AFF, 0xFF7B61FF, 0xFF00E0FF, 0xFF4B8AFF};
    private float strokeWidth = 48f;
    private float shadowRadius = 18f;
    private int extraPad = 40;

    public BatteryCircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeWidth);
        bgPaint.setColor(0xFF232A36);
        bgPaint.setShadowLayer(shadowRadius, 0, 0, 0xFF101010);
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
        float shift = 10 * getResources().getDisplayMetrics().density;
        float left = pad + (extraPad / 2f) - shift;
        float top = pad + (extraPad / 2f) - shift;
        float right = w - pad - (extraPad / 2f) - shift;
        float bottom = h - pad - (extraPad / 2f) - shift;
        oval = new RectF(left, top, right, bottom);
        SweepGradient sweepGradient = new SweepGradient(w/2f, h/2f, gradientColors, null);
        progressPaint.setShader(sweepGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(oval, 0, 360, false, bgPaint);
        canvas.drawArc(oval, -90, progress * 360, false, progressPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec) + extraPad;
        int h = MeasureSpec.getSize(heightMeasureSpec) + extraPad;
        setMeasuredDimension(w, h);
    }

    public void setProgress(float value) {
        this.progress = value;
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