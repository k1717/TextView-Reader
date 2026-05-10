package com.simpletext.reader;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.simpletext.reader.util.PrefsManager;

/**
 * PIN lock screen. Shown on app launch when lock is enabled.
 */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "lock_mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_SET_PIN = 1;
    public static final int MODE_CHANGE_PIN = 2;

    private EditText pinInput;
    private TextView messageText;
    private int mode;
    private String firstEntry; // for PIN confirmation during set

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        pinInput = findViewById(R.id.pin_input);
        messageText = findViewById(R.id.lock_message);
        Button btnConfirm = findViewById(R.id.btn_confirm);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        switch (mode) {
            case MODE_SET_PIN:
                messageText.setText("Enter a new PIN");
                break;
            case MODE_CHANGE_PIN:
                messageText.setText("Enter current PIN");
                break;
            default:
                messageText.setText("Enter PIN to unlock");
                break;
        }

        btnConfirm.setOnClickListener(v -> onConfirm());

        // Number pad buttons
        int[] numBtnIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        };
        for (int i = 0; i < numBtnIds.length; i++) {
            View btn = findViewById(numBtnIds[i]);
            if (btn != null) {
                final String digit = String.valueOf(i);
                btn.setOnClickListener(v -> {
                    String current = pinInput.getText().toString();
                    if (current.length() < 8) {
                        pinInput.setText(current + digit);
                        pinInput.setSelection(pinInput.length());
                    }
                });
            }
        }

        View btnDelete = findViewById(R.id.btn_delete);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                String current = pinInput.getText().toString();
                if (!current.isEmpty()) {
                    pinInput.setText(current.substring(0, current.length() - 1));
                    pinInput.setSelection(pinInput.length());
                }
            });
        }
    }

    private void onConfirm() {
        String pin = pinInput.getText().toString();
        if (pin.length() < 4) {
            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        PrefsManager prefs = PrefsManager.getInstance(this);

        switch (mode) {
            case MODE_UNLOCK:
                if (pin.equals(prefs.getLockPin())) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    messageText.setText("Wrong PIN. Try again.");
                    pinInput.setText("");
                }
                break;

            case MODE_SET_PIN:
                if (firstEntry == null) {
                    firstEntry = pin;
                    messageText.setText("Confirm your PIN");
                    pinInput.setText("");
                } else {
                    if (pin.equals(firstEntry)) {
                        prefs.setLockPin(pin);
                        prefs.setLockEnabled(true);
                        Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        messageText.setText("PINs don't match. Start over.");
                        firstEntry = null;
                        pinInput.setText("");
                    }
                }
                break;

            case MODE_CHANGE_PIN:
                if (firstEntry == null) {
                    // Verify current PIN
                    if (pin.equals(prefs.getLockPin())) {
                        firstEntry = "VERIFIED";
                        messageText.setText("Enter new PIN");
                        pinInput.setText("");
                    } else {
                        messageText.setText("Wrong current PIN");
                        pinInput.setText("");
                    }
                } else if (firstEntry.equals("VERIFIED")) {
                    firstEntry = pin;
                    messageText.setText("Confirm new PIN");
                    pinInput.setText("");
                } else {
                    if (pin.equals(firstEntry)) {
                        prefs.setLockPin(pin);
                        Toast.makeText(this, "PIN changed", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        messageText.setText("PINs don't match. Enter new PIN.");
                        firstEntry = "VERIFIED";
                        pinInput.setText("");
                    }
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_UNLOCK) {
            // Can't back out of unlock
            finishAffinity();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }
}
