package com.savanto.signalflagskb;

import android.content.Context;
import android.inputmethodservice.Keyboard;

/**
 * @author savanto
 *
 */
public class SignalFlagsKeyboard extends Keyboard
{

    /**
     * @param context
     * @param xmlLayoutResId
     */
    public SignalFlagsKeyboard(Context context, int xmlLayoutResId)
    {
        super(context, xmlLayoutResId);
    }

    /**
     * @param context
     * @param xmlLayoutResId
     * @param modeId
     */
    public SignalFlagsKeyboard(Context context, int xmlLayoutResId, int modeId)
    {
        super(context, xmlLayoutResId, modeId);
    }

    
    /**
     * @param context
     * @param layoutTemplateResId
     * @param characters
     * @param columns
     * @param horizontalPadding
     */
    public SignalFlagsKeyboard(Context context, int layoutTemplateResId,
            CharSequence characters, int columns, int horizontalPadding)
    {
        super(context, layoutTemplateResId, characters, columns,
                horizontalPadding);
    }

}
