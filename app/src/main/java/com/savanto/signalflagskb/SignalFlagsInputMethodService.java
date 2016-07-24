package com.savanto.signalflagskb;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.preference.PreferenceManager;
import android.support.annotation.IntegerRes;
import android.support.annotation.XmlRes;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;


public final class SignalFlagsInputMethodService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    private static final int KEYCODE_HOIST = 32;
    private static final int KEYCODE_RETURN = 10;
    private static final int KEYCODE_OPTIONS = -1000;
    private static final int KEYCODE_REPEAT_1 = -1001;
    private static final int KEYCODE_REPEAT_2 = -1002;
    private static final int KEYCODE_REPEAT_3 = -1003;
    private static final int KEYCODE_REPEAT_4 = -1004;

    private enum Type {
        DEFAULT(R.xml.qwerty),
        SYMBOLS(R.xml.symbols),
        ;

        private final @XmlRes int xml;

        Type(@XmlRes int xml) {
            this.xml = xml;
        }
    }

    private enum Number {
        ICS("ics", R.integer.keyboard_mode_numbers_ics),
        NATO("nato", R.integer.keyboard_mode_numbers_nato),
        POPHAM("popham", R.integer.keyboard_mode_numbers_popham),
        ;

        private final @IntegerRes int mode;
        private final String slug;

        Number(String slug, @IntegerRes int mode) {
            this.mode = mode;
            this.slug = slug;
        }

        public static Number find(String slug) {
            for (final Number number : Number.values()) {
                if (number.slug.equals(slug)) {
                    return number;
                }
            }
            return ICS;
        }
    }

    private SignalFlagsKeyboardView mInputView;
    private TextView mHoistView;

    private Type type = Type.DEFAULT;
    private Number number = Number.ICS;
    private boolean doHoist;

    private final StringBuilder mHoist = new StringBuilder();
    private boolean previousHoist;

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
        case InputType.TYPE_CLASS_NUMBER: // FALL-THROUGH
        case InputType.TYPE_CLASS_DATETIME:
            // Numbers and dates default to the symbols keyboard,
            // with no extra features.
            this.type = Type.SYMBOLS;
            break;
        case InputType.TYPE_CLASS_PHONE:
            // TODO numberpad
            this.type = Type.SYMBOLS;
            break;
        case InputType.TYPE_CLASS_TEXT:
            // This is general text editing. We will default to the
            // normal default keyboard.
            this.type = Type.DEFAULT;
            break;
        default:
            // For all unknown input types, use default keyboard
            // with no special features.
            this.type = Type.DEFAULT;
            break;
        }
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        this.mHoist.setLength(0);
        this.updateHoist();
        this.previousHoist = false;

        // Determine number mode and hints.
        this.mInputView = (SignalFlagsKeyboardView) this.getLayoutInflater().inflate(
                R.layout.input,
                null
        );
        this.mInputView.setOnKeyboardActionListener(this);

        return this.mInputView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.doHoist = prefs.getBoolean("com.savanto.signalflagskb.DoHoist", true);
        this.number = Number.find(prefs.getString("com.savanto.signalflagskb.NumberSystem", "ics"));
        final boolean showHints = prefs.getBoolean("com.savanto.signalflagskb.ShowHints", false);
        this.mInputView.setKeyboard(new Keyboard(this, this.type.xml, this.number.mode));
        this.mInputView.showHints(showHints);
        this.mInputView.setShifted(true);
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateCandidatesView() {
        this.mHoistView = (TextView) this.getLayoutInflater().inflate(R.layout.hoist, null);
        return this.mHoistView;
    }

    @Override
    public void onFinishInput() {
        // Clear current composing text and candidates.
        this.mHoist.setLength(0);
        this.updateHoist();
        this.previousHoist = false;
        this.setCandidatesViewShown(false);
        super.onFinishInput();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                candidatesStart,
                candidatesEnd
        );

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (this.mHoist.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            this.mHoist.setLength(0);
            this.updateHoist();
            this.previousHoist = false;

            final InputConnection ic = this.getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && this.mInputView != null) {
                    if (this.mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we are currently
                // composing text for the user, we want to modify that instead
                // of letting the application do the delete itself.
                if (this.mHoist.length() > 0) {
                    this.onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed to the editor.
     */
    private void doHoist() {
        // If composing a hoist, send it to the editor
        if (this.mHoist.length() > 0) {
            final InputConnection ic = this.getCurrentInputConnection();
            // Determine spacing:
            // Automatically insert space between previous and current hoists.
            // Check for previous hoists
            if (this.previousHoist) {
                // send text with space
                ic.commitText(Character.toString((char) KEYCODE_HOIST) + this.mHoist, 1);
            } else {
                // send text without space
                ic.commitText(this.mHoist, 1);
            }

            this.previousHoist = true;
            this.mHoist.setLength(0);
            this.updateHoist();
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        final InputConnection ic = this.getCurrentInputConnection();
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /*
     * Implementation of OnKeyboardActionListener to take action when the user
     * interacts with the keyboard.
     */
    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                this.handleBackspace();
                break;
            case Keyboard.KEYCODE_SHIFT:
                this.mInputView.toggleShift(primaryCode);
                break;
            case Keyboard.KEYCODE_CANCEL:
                this.requestHideSelf(0);
                break;
            case KEYCODE_OPTIONS:
                this.getApplication().startActivity(
                        new Intent(this.getBaseContext(), SignalFlagsSettings.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                );
                break;

            // Handle repeater flags
            case KEYCODE_REPEAT_1:
                this.handleRepeat(0);
                break;
            case KEYCODE_REPEAT_2:
                this.handleRepeat(1);
                break;
            case KEYCODE_REPEAT_3:
                this.handleRepeat(2);
                break;
            case KEYCODE_REPEAT_4:
                this.handleRepeat(3);
                break;

            case Keyboard.KEYCODE_MODE_CHANGE:
                if (this.mInputView != null) {
                    if (this.type != Type.DEFAULT) {
                        this.type = Type.DEFAULT;
                    } else {
                        this.type = Type.SYMBOLS;
                    }
                    this.mInputView.setKeyboard(new Keyboard(this, this.type.xml, this.number.mode));
                }
                break;

            case KEYCODE_HOIST:
                // Space, or "hoist" key.
                // If we are composing text, do the hoist, which will figure out spacing.
                // Otherwise, simply send a space to the editor.
                if (this.mHoist.length() > 0) {
                    this.doHoist();
                } else {
                    this.getCurrentInputConnection().commitText(
                            Character.toString((char) primaryCode),
                            1
                    );
                    // Reset hoist count for this line
                    this.previousHoist = false;
                }
                break;

            case KEYCODE_RETURN:
                // Return/enter key.
                // If we are composing text, do the hoist which will figure out spacing.
                // Then send the key event to the application.
                if (this.mHoist.length() > 0) {
                    this.doHoist();
                }
                this.keyDownUp(KeyEvent.KEYCODE_ENTER);
                // Reset hoist count for this line
                this.previousHoist = false;
                break;

            default:
                if (this.mInputView.isShifted()) {
                    primaryCode = Character.toUpperCase(primaryCode);
                }
                if (this.type == Type.DEFAULT) {
                    this.mInputView.toggleShift(primaryCode);
                }
                if (this.doHoist && ! Character.isWhitespace(primaryCode)) {
                    this.mHoist.append((char) primaryCode);
                    this.updateHoist();
                } else {
                    this.getCurrentInputConnection().commitText(
                            Character.toString((char) primaryCode),
                            1
                    );
                }
                break;
        }
    }

    @Override
    public void onText(CharSequence text) {
        final InputConnection ic = this.getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            if (this.mHoist.length() > 0) {
                this.doHoist();
            }
            ic.commitText(text, 0);
            ic.endBatchEdit();
        }
    }

    /**
     * Update the hoist candidate display from the current composing text.
     */
    private void updateHoist() {
        if (this.mHoist.length() > 0) {
            this.setCandidatesViewShown(true);
            this.mHoistView.setText(this.mHoist.toString());
        } else if (this.mHoistView != null) {
            this.mHoistView.setText("");
        }
    }

    private void handleBackspace() {
        final int length = this.mHoist.length();
        if (length > 1) {
            this.mHoist.delete(length - 1, length);
            this.updateHoist();
        } else if (length > 0) {
            this.mHoist.setLength(0);
            this.updateHoist();
        } else {
            this.keyDownUp(KeyEvent.KEYCODE_DEL);
        }
    }

    private void handleRepeat(int offset) {
        if (this.mHoist.length() > offset) {
            this.mHoist.append(this.mHoist.charAt(offset));
        }
        this.updateHoist();
    }

    @Override
    public void swipeDown() {
        this.requestHideSelf(0);
    }

    @Override
    public void swipeLeft() {
        this.handleBackspace();
    }

    @Override
    public void swipeRight() {
        this.doHoist();
    }

    @Override
    public void swipeUp() {
        // NOP
    }

    @Override
    public void onPress(int primaryCode) {
        // NOP
    }

    @Override
    public void onRelease(int primaryCode) {
        // NOP
    }
}
