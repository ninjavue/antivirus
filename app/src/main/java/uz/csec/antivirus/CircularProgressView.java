package uz.csec.antivirus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class CircularProgressView extends View {
	private Paint bgPaint;
	private Paint progressPaint;
	private Paint dotsPaint;
	private Paint outerRingPaint;
	private Paint textPaint;
	private Paint subtitlePaint;
	private RectF oval;
	private RectF outerOval;
	private float progress = 0f;
	private float strokeWidth = 20f;
	private float outerStrokeWidth = 40f;
	private float dotRadiusLarge = 0f;
	private float dotRadiusSmall = 0f;
	private float centerX, centerY, radius, outerRadius, dotsRadius;
	private String mainText = "89";
	private String subtitleText = "Optimallashtirish";

	public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private float dp(float value) {
		return value * getResources().getDisplayMetrics().density;
	}

	private void init() {
		bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		bgPaint.setStyle(Paint.Style.STROKE);
		bgPaint.setStrokeWidth(strokeWidth);
		bgPaint.setColor(0xFFF3F4F6);
		bgPaint.setStrokeCap(Paint.Cap.ROUND);

		progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		progressPaint.setStyle(Paint.Style.STROKE);
		progressPaint.setStrokeWidth(strokeWidth);
		progressPaint.setStrokeCap(Paint.Cap.ROUND);

		dotsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		dotsPaint.setStyle(Paint.Style.FILL);
		dotsPaint.setColor(0xFF3B82F6);
		// Dots sizes
		dotRadiusLarge = dp(3);
		dotRadiusSmall = dp(5);

		outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		outerRingPaint.setStyle(Paint.Style.STROKE);
		outerRingPaint.setStrokeWidth(outerStrokeWidth);
		outerRingPaint.setColor(0xFFF5F8FB);
		outerRingPaint.setStrokeCap(Paint.Cap.ROUND);

		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setColor(0xFF1E3A8A);
		textPaint.setTextAlign(Paint.Align.CENTER);

		subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		subtitlePaint.setStyle(Paint.Style.FILL);
		subtitlePaint.setColor(0xFF6B7280);
		subtitlePaint.setTextAlign(Paint.Align.CENTER);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		centerX = w / 2f;
		centerY = h / 2f;
		float halfMin = Math.min(w, h) / 2f;

		float gapBetweenRings = dp(8);
		float dotsGap = dp(20);
		float safeEdge = dp(2);

		float edgeBudget = outerStrokeWidth / 2f + dotsGap + dotRadiusLarge + safeEdge;

		radius = halfMin - edgeBudget - gapBetweenRings - strokeWidth / 2f;
		if (radius < dp(24)) radius = dp(24);
		outerRadius = radius + strokeWidth / 2f + gapBetweenRings;

		dotsRadius = outerRadius + outerStrokeWidth / 2f + dotsGap;

		float left = centerX - radius;
		float top = centerY - radius;
		float right = centerX + radius;
		float bottom = centerY + radius;
		oval = new RectF(left, top, right, bottom);

		float outerLeft = centerX - outerRadius;
		float outerTop = centerY - outerRadius;
		float outerRight = centerX + outerRadius;
		float outerBottom = centerY + outerRadius;
		outerOval = new RectF(outerLeft, outerTop, outerRight, outerBottom);
		
		int[] colors = {0xFF1E3A8A, 0xFF3B82F6, 0xFF60A5FA, 0xFF93C5FD};
		float[] positions = {0.0f, 0.33f, 0.66f, 1.0f};
		
		LinearGradient gradient = new LinearGradient(
			centerX - radius, centerY - radius,
			centerX + radius, centerY + radius,
			colors, positions, Shader.TileMode.CLAMP
		);
		progressPaint.setShader(gradient);

		textPaint.setTextSize(radius * 0.4f);
		subtitlePaint.setTextSize(radius * 0.15f);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		canvas.drawArc(outerOval, 0, 360, false, outerRingPaint);
		
		canvas.drawArc(oval, 0, 360, false, bgPaint);
		
		if (progress > 0) {
			canvas.drawArc(oval, -90, progress * 360, false, progressPaint);
		}
		
		canvas.drawText(mainText, centerX, centerY - radius * 0.1f, textPaint);
		
		String[] lines = subtitleText.split("\n");
		float lineHeight = subtitlePaint.getTextSize() * 1.2f;
		float startY = centerY + radius * 0.1f;
		
		for (int i = 0; i < lines.length; i++) {
			canvas.drawText(lines[i], centerX, startY + (i * lineHeight), subtitlePaint);
		}
		
		drawDots(canvas);
	}

	private void drawDots(Canvas canvas) {
		float[] angles = {135, 225, 315, 45};
		
		for (float angle : angles) {
			float radians = (float) Math.toRadians(angle);
			float x = centerX + (float) Math.cos(radians) * dotsRadius;
			float y = centerY + (float) Math.sin(radians) * dotsRadius;
			float r = (angle == 225f || angle == 315f) ? dotRadiusSmall : dotRadiusLarge;
			canvas.drawCircle(x, y, r, dotsPaint);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), 
						   MeasureSpec.getSize(heightMeasureSpec));
		setMeasuredDimension(size, size);
	}

	public void setProgress(float value) {
		this.progress = Math.max(0, Math.min(1, value));
		invalidate();
	}

	public void setMainText(String text) {
		this.mainText = text;
		invalidate();
	}

	public void setSubtitleText(String text) {
		this.subtitleText = text;
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
	
	public void animateProgress(float to, long duration) {
		ValueAnimator animator = ValueAnimator.ofFloat(progress, to);
		animator.setDuration(duration);
		animator.addUpdateListener(animation -> {
			setProgress((float) animation.getAnimatedValue());
		});
		animator.start();
	}
} 