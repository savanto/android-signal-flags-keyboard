package com.savanto.signalflagskb;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.TextView;

/**
 * @author savanto
 *
 */
public class SignalFlagsInputMethodService extends InputMethodService implements KeyboardView.OnKeyboardActionListener
{
//	private InputMethodManager mInputMethodManager;

	private SignalFlagsKeyboardView mInputView;
//	private KeyboardView mInputView;
	private TextView mHoistView;

	private SharedPreferences mPreferences;

	private SignalFlagsKeyboard mDefKeyboard;
	private SignalFlagsKeyboard mSymKeyboard;
	private SignalFlagsKeyboard mAltKeyboard;
//	private SignalFlagsKeyboard mNumKeyboard;
	private SignalFlagsKeyboard mCurKeyboard;

	private int mLastDisplayWidth;
	private boolean mCapsLock;
	private boolean mShift;

	private StringBuilder mHoist = new StringBuilder();
	private long mHoistCount;

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onCreate()
	 **
	 * Main initialization of the input method component.
	 */
	@Override
	public void onCreate()
	{
		super.onCreate();

//		this.mInputMethodManager = (InputMethodManager) this.getSystemService(SignalFlagsInputMethodService.INPUT_METHOD_SERVICE);
		this.mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onInitializeInterface()
	 **
	 * This is the point where you can do all of your UI initialization. It
	 * is called after creation and any configuration change.
	 */
	@Override
	public void onInitializeInterface()
	{
		if (this.mDefKeyboard != null)
		{
			// Configuration changes can happen after the keyboard gets recreated,
			// so we need to be able to re-build the keyboards if the available
			// space has changed.
			int displayWidth = this.getMaxWidth();
			if (displayWidth == this.mLastDisplayWidth)
				return;
			this.mLastDisplayWidth = displayWidth;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onCreateInputView()
	 ** 
	 * Called by the framework when your view for creating input needs to
	 * be generated. This will be called the first time your input method
	 * is displayed, and every time it needs to be re-created such as due to
	 * a configuration change.
	 */
	@Override
	public View onCreateInputView()
	{
		this.mInputView = (SignalFlagsKeyboardView) this.getLayoutInflater()
				.inflate(R.layout.input, null);
		this.mInputView.setOnKeyboardActionListener(this);
		this.mInputView.setKeyboard(this.mDefKeyboard);
		return this.mInputView;
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onCreateCandidatesView()
	 **
	 * Called by the framework when your view for showing candidates needs to
	 * be generated, like {@link #onCreateInputView}.
	 */
	@Override
	public View onCreateCandidatesView()
	{
		this.mHoistView = (TextView) this.getLayoutInflater().inflate(R.layout.hoist, null);
		return this.mHoistView;
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onStartInput(android.view.inputmethod.EditorInfo, boolean)
	 **
	 * This is the main point where we do our initialization of the input method
	 * to begin operating on an application. At this point we have been
	 * bound to the client, and are now receiving all of the detailed information
	 * about the target of our edits.
	 */
	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting)
	{
		super.onStartInput(attribute, restarting);

		// Setup the right keyboards to show depending on preference settings
		this.setupKeyboards();

		// Reset our state. We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any way.
		this.mHoist.setLength(0);
		this.updateHoist();
		this.mHoistCount = 0;
		this.mShift = true;
		this.mCapsLock = false;

		// We are now going to initialize our state based on the type of
		// text being edited.
		switch (attribute.inputType & InputType.TYPE_MASK_CLASS)
		{
			case InputType.TYPE_CLASS_NUMBER:
			case InputType.TYPE_CLASS_DATETIME:
				// Numbers and dates default to the symbols keyboard,
				// with no extra features.
				this.mCurKeyboard = this.mSymKeyboard;
				break;

			case InputType.TYPE_CLASS_PHONE:
				// TODO numberpad
				this.mCurKeyboard = this.mSymKeyboard;
				break;

			case InputType.TYPE_CLASS_TEXT:
				// This is general text editing. We will default to the
				// normal default keyboard.
				this.mCurKeyboard = this.mDefKeyboard;

				// Look at current state of the editor
				// to decide whether the keyboard should start out shifted.
				this.updateShiftKeyState(attribute);
				break;

			default:
				// For all unknown input types, use default keyboard
				// with no special features.
				this.mCurKeyboard = this.mDefKeyboard;
				break;
		}

		// Update the label on the enter key, depending on what
		// the application says it will do.
		// TODO
//		this.mCurKeyboard.setImeOptions(this.getResources(), attribute.imeOptions);
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onFinishInput()
	 **
	 * This is called when the user is done editing a field. We can use
	 * this to reset our state.
	 */
	@Override
	public void onFinishInput()
	{
		super.onFinishInput();

		// Clear current composing text and candidates.
		this.mHoist.setLength(0);
		this.updateHoist();
		this.mHoistCount = 0;

		// We only hide the candidates window when finishing input on
		// a particular editor, to avoid popping the underlying application
		// up and down if the user is entering text into the bottom of
		// its window.
		this.setCandidatesViewShown(false);

		this.mCurKeyboard = this.mDefKeyboard;
		if (this.mInputView != null)
			this.mInputView.closing();
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onStartInputView(android.view.inputmethod.EditorInfo, boolean)
	 **
	 * 
	 */
	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting)
	{
		super.onStartInputView(attribute, restarting);
		// Apply the selected keyboard to the input view
		this.mInputView.setKeyboard(this.mCurKeyboard);
		this.mInputView.closing();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)
	{
		
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onUpdateSelection(int, int, int, int, int, int)
	 **
	 * Deal with the editor reporting movement of its cursor.
	 */
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
			int newSelStart, int newSelEnd,
			int candidatesStart, int candidatesEnd)
	{
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
				candidatesStart, candidatesEnd);

		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		if (this.mHoist.length() > 0 && (newSelStart != candidatesEnd
				|| newSelEnd != candidatesEnd))
		{
			this.mHoist.setLength(0);
			this.updateHoist();
			this.mHoistCount = 0;

			InputConnection ic = this.getCurrentInputConnection();
			if (ic != null)
				ic.finishComposingText();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.InputMethodService#onKeyDown(int, android.view.KeyEvent)
	 **
	 * Use this to monitor key events being delivered to the application.
	 * We get first crack at them, and can either consume them or let them
	 * continue to the app.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_BACK:
				// The InputMethodService already takes care of the back
				// key for us, to dismiss the input method if it is shown.
				// However, our keyboard could be showing a pop-up window
				// that back should dismiss, so we first allow it to do that.
				if (event.getRepeatCount() == 0 && this.mInputView != null)
				{
					if (this.mInputView.handleBack())
						return true;
				}
				break;

			case KeyEvent.KEYCODE_DEL:
				// Special handling of the delete key: if we are currently
				// composing text for the user, we want to modify that instead
				// of letting the application do the delete itself.
				if (this.mHoist.length() > 0)
				{
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
	private void doHoist()
	{
		// If composing a hoist, send it to the editor
		if (this.mHoist.length() > 0)
		{
			InputConnection ic = this.getCurrentInputConnection();
			// Determine spacing:
			// Automatically insert space between previous and current hoists.
			// Check for previous hoists
			if (this.mHoistCount > 0)
				ic.commitText(Character.toString((char) SignalFlagsKeyboardView.KEYCODE_HOIST) + this.mHoist, 1);	// send text with space
			else
				ic.commitText(this.mHoist, 1);		// send text without space

			this.mHoistCount++;
			this.mHoist.setLength(0);
			this.updateHoist();
		}
	}

	/**
	 * Helper to update the shift state of the keyboard based
	 * on the initial editor state.
	 */
	private void updateShiftKeyState(EditorInfo attr)
	{
		if (attr != null 
				&& this.mInputView != null 
				&& this.mDefKeyboard == this.mInputView.getKeyboard())
		{
			int caps = 0;
			EditorInfo ei = this.getCurrentInputEditorInfo();
			if (ei != null && ei.inputType != InputType.TYPE_NULL)
				caps = this.getCurrentInputConnection().getCursorCapsMode(attr.inputType);
			this.mInputView.setShifted(this.mCapsLock || caps != 0);
		}
	}


	/**
	 * Helper to determine whether the character is whitespace
	 */
	private boolean isWhitespace(int code)
	{
		return Character.isWhitespace(code);
	}

	/**
	 * Helper to send a key down / key up pair to the current editor.
	 */
	private void keyDownUp(int keyEventCode)
	{
		this.getCurrentInputConnection()
			.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		this.getCurrentInputConnection()
			.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}

	/*
	 * Implementation of OnKeyboardActionListener to take action when the user
	 * interacts with the keyboard.
	 */

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onKey(int, int[])
	 */
	@Override
	public void onKey(int primaryCode, int[] keyCodes)
	{
		switch (primaryCode)
		{
			case Keyboard.KEYCODE_DELETE:
				this.handleBackspace();
				break;
			case Keyboard.KEYCODE_SHIFT:
				this.handleShift();
				break;
			case Keyboard.KEYCODE_CANCEL:
				this.handleClose();
				break;
			case SignalFlagsKeyboardView.KEYCODE_OPTIONS:
				this.getApplication().startActivity(new Intent(this.getBaseContext(), SignalFlagsSettings.class)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				break;

			// Handle repeater flags
			case SignalFlagsKeyboardView.KEYCODE_REPEAT_1:
				this.handleRepeat(0);
				break;
			case SignalFlagsKeyboardView.KEYCODE_REPEAT_2:
				this.handleRepeat(1);
				break;
			case SignalFlagsKeyboardView.KEYCODE_REPEAT_3:
				this.handleRepeat(2);
				break;
			case SignalFlagsKeyboardView.KEYCODE_REPEAT_4:
				this.handleRepeat(3);
				break;

			case Keyboard.KEYCODE_MODE_CHANGE:
				if (this.mInputView != null)
				{
					Keyboard current = this.mInputView.getKeyboard();
					if (current != this.mDefKeyboard)
						current = this.mDefKeyboard;
					else
					{
						current = this.mSymKeyboard;
						this.mShift = false;
						this.mCapsLock = false;
					}
					
					this.mInputView.setKeyboard(current);
				}
				break;

			case SignalFlagsKeyboardView.KEYCODE_HOIST:
				// Space, or "hoist" key.
				// If we are composing text, do the hoist, which will figure out spacing.
				// Otherwise, simply send a space to the editor.
				if (this.mHoist.length() > 0)
					this.doHoist();
				else
				{
					this.getCurrentInputConnection().commitText(Character.toString((char) primaryCode), 1);
					// Reset hoist count for this line
					this.mHoistCount = 0;
				}
				break;

			case SignalFlagsKeyboardView.KEYCODE_RETURN:
				// Return/enter key.
				// If we are composing text, do the hoist which will figure out spacing.
				// Then send the key event to the application.
				if (this.mHoist.length() > 0)
					this.doHoist();
				this.keyDownUp(KeyEvent.KEYCODE_ENTER);
				// Reset hoist count for this line
				this.mHoistCount = 0;
				break;

			default:
				this.handleCharacter(primaryCode, keyCodes);
				break;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.inputmethodservice.KeyboardView.OnKeyboardActionListener#onText(java.lang.CharSequence)
	 */
	@Override
	public void onText(CharSequence text)
	{
		InputConnection ic = this.getCurrentInputConnection();
		if (ic != null)
		{
			ic.beginBatchEdit();
			if (this.mHoist.length() > 0)
				this.doHoist();
			ic.commitText(text, 0);
			ic.endBatchEdit();
		}
	}

	/**
	 * Update the hoist candidate display from the current composing text.
	 */
	private void updateHoist()
	{
		if (this.mHoist.length() > 0)
		{
			this.setCandidatesViewShown(true);
			this.mHoistView.setText(this.mHoist.toString());
		}
		else if (this.mHoistView != null)
			this.mHoistView.setText("");
	}

	private void handleBackspace()
	{
		final int length = this.mHoist.length();
		if (length > 1)
		{
			this.mHoist.delete(length - 1, length);
			this.updateHoist();
		}
		else if (length > 0)
		{
			this.mHoist.setLength(0);
			this.updateHoist();
		}
		else
			this.keyDownUp(KeyEvent.KEYCODE_DEL);
	}

	/**
	 * Shift has been pressed. Handle shifting/unshifting/capslock
	 */
	private void handleShift()
	{
		if (this.mInputView == null)
			return;

		Keyboard current = this.mInputView.getKeyboard();

		// Default keyboard -- normal shift/capslock operation
		if (current == this.mDefKeyboard)
		{
			if (this.mCapsLock)
			{
				this.mCapsLock = false;
				this.mShift = false;
			}
			else
			{
				// If already shifted, turn capslock on, if unshifted, ensure it's off.
				this.mCapsLock = this.mShift;
				// Toggle shift
				this.mShift = ! this.mShift;
			}
		}
		else	// Shift key cycles symbol keyboards
		{
			if (this.mShift)
				this.mInputView.setKeyboard(this.mAltKeyboard);
			else
				this.mInputView.setKeyboard(this.mSymKeyboard);

			// No capslock here
			this.mCapsLock = false;
			// Toggle shift
			this.mShift = ! this.mShift;
		}
	}

	private void handleCharacter(int primaryCode, int[] keyCodes)
	{
		if (this.isInputViewShown())
		{
			if (this.mShift || this.mCapsLock)
				primaryCode = Character.toUpperCase(primaryCode);
			// Reset shift
			if (this.mInputView.getKeyboard() == this.mDefKeyboard)
				this.mShift = false;
		}
		if (! this.isWhitespace(primaryCode))
		{
			this.mHoist.append((char) primaryCode);
			this.updateShiftKeyState(this.getCurrentInputEditorInfo());
			this.updateHoist();
		}
		else
			this.getCurrentInputConnection().commitText(Character.toString((char) primaryCode), 1);
	}

	private void handleRepeat(int offset)
	{
		if (this.mHoist.length() > offset)
			this.mHoist.append(this.mHoist.charAt(offset));
		this.updateHoist();
	}

	private void handleClose()
	{
		this.doHoist();
		this.requestHideSelf(0);
		this.mInputView.closing();
	}

	@Override
	public void swipeDown()
	{
		this.handleClose();
	}

	@Override
	public void swipeLeft()
	{
		this.handleBackspace();
	}

	@Override
	public void swipeRight()
	{
		this.doHoist();
	}

	@Override
	public void swipeUp()
	{ }

	@Override
	public void onPress(int primaryCode)
	{ }

	@Override
	public void onRelease(int primaryCode)
	{ }

	/**
	 * Helper to setup the right keyboards to use based on preferences.
	 */
	private void setupKeyboards()
	{
		// Determine number mode
		int numberMode;
		switch (Integer.valueOf(this.mPreferences.getString(this.getString(R.string.pref_key_numbers_system), "2")))
		{
			case 1:
				numberMode = R.integer.keyboard_mode_numbers_arabic;
				break;
			case 2:
				numberMode = R.integer.keyboard_mode_numbers_ics;
				break;
			case 3:
				numberMode = R.integer.keyboard_mode_numbers_nato;
				break;
			case 4:
				numberMode = R.integer.keyboard_mode_numbers_popham;
				break;
			default:
				numberMode = R.integer.keyboard_mode_numbers_ics;
				break;
		}

		// Define main keyboard
		// Determine hints mode
		if (this.mPreferences.getBoolean(this.getString(R.string.pref_key_hints), false))
			this.mDefKeyboard = new SignalFlagsKeyboard(this, R.xml.qwerty, R.integer.keyboard_mode_letters_hints);
		else
			this.mDefKeyboard = new SignalFlagsKeyboard(this, R.xml.qwerty, R.integer.keyboard_mode_letters_normal);

		// Define symbols and alternative symbols keyboards.
		this.mSymKeyboard = new SignalFlagsKeyboard(this, R.xml.qwerty_symbols, numberMode);
		this.mAltKeyboard = new SignalFlagsKeyboard(this, R.xml.qwerty_alt_symbols);

		// Define number pad.
		// TODO
//		this.mNumKeyboard = new SignalFlagsKeyboard(this, R.xml.numpad);


	}
}
