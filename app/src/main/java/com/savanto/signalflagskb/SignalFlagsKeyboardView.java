package com.savanto.signalflagskb;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;


public final class SignalFlagsKeyboardView extends KeyboardView {
    private final Paint hintPaint = new Paint();
    private final Paint shiftPaint = new Paint();
    private boolean isCapsLocked;
    private boolean showHints;

    public SignalFlagsKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Resources res = context.getResources();

        this.hintPaint.setTextAlign(Paint.Align.CENTER);
        //noinspection deprecation
        final @ColorInt int hintColor = res.getColor(R.color.hint_text);
        this.hintPaint.setColor(hintColor);
        this.hintPaint.setTextSize(96);

        //noinspection deprecation
        final @ColorInt int shiftColor = res.getColor(R.color.shift_text);
        this.shiftPaint.setColor(shiftColor);
    }

    /* package */ void showHints(boolean showHints) {
        this.showHints = showHints;
        this.invalidate();
    }

    /* package */ void toggleShift(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            if (this.isCapsLocked) {
                this.isCapsLocked = false;
                this.setShifted(false);
            } else if (this.isShifted()) {
                this.isCapsLocked = true;
            } else {
                this.setShifted(true);
            }
        } else if (this.isShifted() && ! this.isCapsLocked) {
            this.setShifted(false);
        }
    }

    @Override
    protected boolean onLongPress(Keyboard.Key popupKey) {
        if (popupKey.codes.length > 1) {
            this.getOnKeyboardActionListener().onKey(popupKey.codes[1], null);
            return true;
        } else {
            return super.onLongPress(popupKey);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (final Keyboard.Key key : this.getKeyboard().getKeys()) {
            if (this.showHints && (key.codes[0] >= 'a' && key.codes[0] <= 'z'
                    || key.codes[0] >= '0' && key.codes[0] <= '9')) {
                canvas.drawText(
                        String.valueOf((char) Character.toUpperCase(key.codes[0])),
                        key.x + (key.width / 2),
                        key.y + (key.height / 1.4f),
                        this.hintPaint
                );
            } else if (key.codes[0] == Keyboard.KEYCODE_SHIFT && this.isShifted()) {
                this.shiftPaint.setStyle(
                        this.isCapsLocked ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE
                );
                canvas.drawCircle(
                        key.x + (key.width / 4 * 3),
                        key.y + (key.height / 4),
                        10,
                        this.shiftPaint
                );
            }
        }
    }
}
