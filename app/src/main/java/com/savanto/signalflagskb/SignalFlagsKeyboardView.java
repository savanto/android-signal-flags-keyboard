package com.savanto.signalflagskb;

import android.content.Context;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;

/**
 * @author savanto
 *
 */
public class SignalFlagsKeyboardView extends KeyboardView
{
    public static final int KEYCODE_HOIST = 32;
    public static final int KEYCODE_RETURN = 10;
    public static final int KEYCODE_OPTIONS = -1000;
    public static final int KEYCODE_REPEAT_1 = -1001;
    public static final int KEYCODE_REPEAT_2 = -1002;
    public static final int KEYCODE_REPEAT_3 = -1003;
    public static final int KEYCODE_REPEAT_4 = -1004;

    /**
     * @param context
     * @param attrs
     */
    public SignalFlagsKeyboardView(Context context, AttributeSet attrs)
    {
        super(context, attrs);        
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public SignalFlagsKeyboardView(Context context, AttributeSet attrs,
            int defStyle)
    {
        super(context, attrs, defStyle);
    }

}
