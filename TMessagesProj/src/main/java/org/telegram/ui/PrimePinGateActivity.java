package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimePinContract;
import org.telegram.messenger.PrimePinKdf;
import org.telegram.messenger.PrimePinKeyStore;
import org.telegram.messenger.PrimePinLockPolicy;
import org.telegram.messenger.PrimePinSession;
import org.telegram.messenger.PrimePinVault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PrimeGram: the PIN entry/enrollment screen. Plain {@link Activity}, not a themed
 * {@code BaseFragment} - it must be able to render before
 * {@code ApplicationLoader.postInitApplication()} has run (that call is itself gated on
 * this screen), so nothing here can depend on {@code Theme} or other post-init statics.
 *
 * <p>Nothing is drawn as the user types - no dots, no digit count - and the on-screen
 * keypad is reshuffled on every open and after every wrong attempt, as a mitigation
 * against someone reading finger position or screen smudges rather than the PIN itself.
 */
public class PrimePinGateActivity extends Activity {

    public static final String EXTRA_SETUP_EMERGENCY = "primegram.setup_emergency";
    public static final String EXTRA_DISABLE_PIN = "primegram.disable_pin";
    public static final String EXTRA_DISABLE_EMERGENCY = "primegram.disable_emergency";

    private static final int BACKGROUND_COLOR = Color.rgb(23, 33, 43);
    private static final int PANEL_COLOR = Color.rgb(30, 42, 54);
    private static final int PRIMARY_COLOR = Color.rgb(82, 136, 193);
    private static final int ERROR_COLOR = Color.rgb(224, 96, 96);
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int SUBTEXT_COLOR = Color.rgb(150, 165, 180);

    // Hardcoded Russian literals, matching this codebase's PrimeGram-settings convention
    // (PrimeGramSettingsActivity/GreyZoneActivity do the same) rather than string resources -
    // this screen also has to render before LocaleController is necessarily ready.
    private static final String STR_CLEAR = "Очистить";
    private static final String STR_CONTINUE = "Продолжить";
    private static final String STR_SET_TITLE = "Установите PIN-код";
    private static final String STR_SET_DESC = "PIN защищает вход в приложение. Никто не увидит, сколько цифр вы ввели.";
    private static final String STR_CONTINUE_WITHOUT_PIN = "Продолжить без PIN-кода";
    private static final String STR_ENTER_TITLE = "Введите PIN-код";
    private static final String STR_DISABLE_TITLE = "Отключить PIN-код";
    private static final String STR_DISABLE_DESC = "Вместе с основным PIN будет удалён и аварийный, если он был задан.";
    private static final String STR_EMERGENCY_VERIFY_FIRST = "Сначала введите основной PIN-код";
    private static final String STR_EMERGENCY_SET_TITLE = "Задайте аварийный PIN-код";
    private static final String STR_EMERGENCY_SET_DESC = "Ввод этого PIN-кода вместо основного сотрёт данные этого устройства.";
    private static final String STR_CONFIRM_TITLE = "Повторите PIN-код";
    private static final String STR_CORRUPT_TITLE = "Не удалось прочитать защищённые данные";
    private static final String STR_CORRUPT_DESC = "Попробуйте перезапустить приложение. Если это повторится — потребуется сброс PIN-кода в настройках.";
    private static final String STR_SOFTWARE_WARNING_TITLE = "Аппаратная защита недоступна";
    private static final String STR_SOFTWARE_WARNING_DESC = "На этом устройстве PIN-код будет храниться без отдельного защищённого чипа. Продолжить всё равно?";
    private static final String STR_SOFTWARE_WARNING_PROCEED = "Продолжить";
    private static final String STR_SOFTWARE_WARNING_CANCEL = "Отмена";
    private static final String STR_MISMATCH = "PIN-коды не совпадают";
    private static final String STR_GENERIC_ERROR = "Что-то пошло не так, попробуйте ещё раз";
    private static final String STR_EMERGENCY_SAME_AS_PRIMARY = "Аварийный PIN должен отличаться от основного";
    private static final String STR_WRONG = "Неверный PIN-код";
    private static final String STR_CHECKING = "Проверка...";
    private static final String STR_LOCKED = "Заблокировано на";
    private static final int MAX_PIN_LENGTH = PrimePinContract.MAX_PIN_LENGTH;

    private enum Mode { LOADING, ENROLL_FIRST, ENROLL_CONFIRM, SOFTWARE_WARNING, UNLOCK, DISABLE_VERIFY, EMERGENCY_VERIFY, EMERGENCY_FIRST, EMERGENCY_CONFIRM, FATAL }

    private Mode mode = Mode.LOADING;
    private final char[] input = new char[MAX_PIN_LENGTH];
    private int inputLength;
    private char[] firstPin;
    private char[] pendingSoftwarePin;

    private PrimePinVault vault;
    private ExecutorService executor;
    private final java.util.Random keypadRandom = new java.util.Random();

    private FrameLayout root;
    private TextView titleView;
    private TextView descriptionView;
    private TextView dotsView;
    private GridLayout keypadGrid;
    private LinearLayout countdownContainer;
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                setRecentsScreenshotEnabled(false);
            } catch (Throwable ignored) {
            }
        }
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "PrimePinVault"));
        vault = new PrimePinVault(getApplicationContext());
        setContentView(createContent());
        inspectVault();
    }

    @Override
    public void onBackPressed() {
        if (isEmergencySetupRequested() || isDisableRequested() || isDisableEmergencyRequested()) {
            super.onBackPressed();
        }
        // Main gate is undismissable otherwise.
    }

    private boolean isEmergencySetupRequested() {
        return getIntent() != null && getIntent().getBooleanExtra(EXTRA_SETUP_EMERGENCY, false);
    }

    private boolean isDisableRequested() {
        return getIntent() != null && getIntent().getBooleanExtra(EXTRA_DISABLE_PIN, false);
    }

    private boolean isDisableEmergencyRequested() {
        return getIntent() != null && getIntent().getBooleanExtra(EXTRA_DISABLE_EMERGENCY, false);
    }

    // ─── layout ─────────────────────────────────────────────────────────────

    private View createContent() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND_COLOR);
        root.setFilterTouchesWhenObscured(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        columnParams.leftMargin = columnParams.rightMargin = dp(24);
        root.addView(column, columnParams);

        titleView = new TextView(this);
        titleView.setTextColor(TEXT_COLOR);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setGravity(Gravity.CENTER);
        column.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        descriptionView = new TextView(this);
        descriptionView.setTextColor(SUBTEXT_COLOR);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        descriptionView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(8);
        column.addView(descriptionView, descParams);

        dotsView = new TextView(this);
        dotsView.setTextColor(TEXT_COLOR);
        dotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setLetterSpacing(0.3f);
        // Deliberately never shows the digits or how many were typed - see class doc.
        dotsView.setText("");
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsParams.topMargin = dp(24);
        dotsParams.bottomMargin = dp(24);
        column.addView(dotsView, dotsParams);

        countdownContainer = new LinearLayout(this);
        countdownContainer.setOrientation(LinearLayout.VERTICAL);
        countdownContainer.setGravity(Gravity.CENTER);
        column.addView(countdownContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        keypadGrid = new GridLayout(this);
        keypadGrid.setColumnCount(3);
        keypadGrid.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(dp(3 * 90), ViewGroup.LayoutParams.WRAP_CONTENT);
        column.addView(keypadGrid, gridParams);

        return root;
    }

    private int dp(int value) {
        return AndroidUtilities.dp(value);
    }

    private void setBusy(boolean busy) {
        keypadGrid.setEnabled(!busy);
        for (int i = 0; i < keypadGrid.getChildCount(); i++) {
            keypadGrid.getChildAt(i).setEnabled(!busy);
        }
    }

    private void setDots(int filled) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            if (i > 0) sb.append(' ');
            sb.append('●');
        }
        dotsView.setText(sb.toString());
    }

    // ─── keypad ─────────────────────────────────────────────────────────────

    private void renderKeypad() {
        keypadGrid.removeAllViews();
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) digits.add(i);
        Collections.shuffle(digits, keypadRandom);

        for (int i = 0; i < 9; i++) {
            final int digit = digits.get(i);
            addKey(String.valueOf(digit), () -> appendDigit(digit));
        }
        addKey(STR_CLEAR, this::clearInput);
        final int lastDigit = digits.get(9);
        addKey(String.valueOf(lastDigit), () -> appendDigit(lastDigit));
        addKey(STR_CONTINUE, this::submit);
    }

    private void addKey(String label, Runnable onClick) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextColor(TEXT_COLOR);
        key.setBackgroundColor(PANEL_COLOR);
        key.setGravity(Gravity.CENTER);
        key.setSoundEffectsEnabled(false);
        key.setHapticFeedbackEnabled(false);
        key.setFocusable(false);
        key.setMaxLines(1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            key.setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        } else {
            key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }
        key.setOnClickListener(v -> onClick.run());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(84);
        params.height = dp(58);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL);
        keypadGrid.addView(key, params);
    }

    private void appendDigit(int digit) {
        if (inputLength >= MAX_PIN_LENGTH) {
            return;
        }
        input[inputLength++] = Character.forDigit(digit, 10);
        setDots(inputLength);
        if (inputLength >= MAX_PIN_LENGTH) {
            submit();
        }
    }

    private void clearInput() {
        java.util.Arrays.fill(input, '\0');
        inputLength = 0;
        setDots(0);
    }

    private char[] takeInputCopy() {
        char[] copy = new char[inputLength];
        System.arraycopy(input, 0, copy, 0, inputLength);
        return copy;
    }

    // ─── flow ───────────────────────────────────────────────────────────────

    private void inspectVault() {
        mode = Mode.LOADING;
        titleView.setText("");
        descriptionView.setText("");
        setBusy(true);
        executor.submit(() -> {
            PrimePinVault.VaultInspection inspection = vault.inspect();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                onInspected(inspection);
            });
        });
    }

    private void onInspected(PrimePinVault.VaultInspection inspection) {
        if (inspection.state == PrimePinVault.VaultState.NOT_ENROLLED) {
            if (isEmergencySetupRequested() || isDisableRequested() || isDisableEmergencyRequested()) {
                // Nothing to set up/disable without a primary PIN.
                completeGate();
                return;
            }
            showFirstEnrollment();
        } else if (inspection.state == PrimePinVault.VaultState.ENROLLED) {
            if (isEmergencySetupRequested() || isDisableEmergencyRequested()) {
                showEmergencyVerify();
            } else if (isDisableRequested()) {
                showDisableVerify();
            } else {
                showUnlock();
                if (inspection.retryAfterMillis > 0) {
                    startCountdown(inspection.retryAfterMillis);
                }
            }
        } else {
            showFatal();
        }
    }

    private void showFirstEnrollment() {
        mode = Mode.ENROLL_FIRST;
        clearInput();
        renderKeypad();
        setBusy(false);
        titleView.setText(STR_SET_TITLE);
        descriptionView.setText(STR_SET_DESC);
        showSkipOption();
    }

    private void showSkipOption() {
        countdownContainer.removeAllViews();
        TextView skip = new TextView(this);
        skip.setText(STR_CONTINUE_WITHOUT_PIN);
        skip.setTextColor(SUBTEXT_COLOR);
        skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        skip.setPadding(dp(8), dp(8), dp(8), dp(8));
        skip.setOnClickListener(v -> continueWithoutPin());
        countdownContainer.addView(skip);
    }

    private void continueWithoutPin() {
        completeGate();
    }

    private void showUnlock() {
        mode = Mode.UNLOCK;
        clearInput();
        renderKeypad();
        countdownContainer.removeAllViews();
        titleView.setText(STR_ENTER_TITLE);
        descriptionView.setText("");
    }

    private void showDisableVerify() {
        mode = Mode.DISABLE_VERIFY;
        clearInput();
        renderKeypad();
        countdownContainer.removeAllViews();
        titleView.setText(STR_DISABLE_TITLE);
        descriptionView.setText(STR_DISABLE_DESC);
    }

    private void showEmergencyVerify() {
        mode = Mode.EMERGENCY_VERIFY;
        clearInput();
        renderKeypad();
        countdownContainer.removeAllViews();
        titleView.setText(STR_ENTER_TITLE);
        descriptionView.setText(STR_EMERGENCY_VERIFY_FIRST);
    }

    private void showEmergencyFirst() {
        mode = Mode.EMERGENCY_FIRST;
        clearInput();
        renderKeypad();
        titleView.setText(STR_EMERGENCY_SET_TITLE);
        descriptionView.setText(STR_EMERGENCY_SET_DESC);
    }

    private void showEmergencyConfirm() {
        mode = Mode.EMERGENCY_CONFIRM;
        clearInput();
        renderKeypad();
        titleView.setText(STR_CONFIRM_TITLE);
        descriptionView.setText("");
    }

    private void showFatal() {
        mode = Mode.FATAL;
        keypadGrid.removeAllViews();
        countdownContainer.removeAllViews();
        setBusy(true);
        titleView.setText(STR_CORRUPT_TITLE);
        descriptionView.setText(STR_CORRUPT_DESC);
    }

    private void showSoftwareWarning(char[] pin) {
        pendingSoftwarePin = pin;
        mode = Mode.SOFTWARE_WARNING;
        keypadGrid.removeAllViews();
        countdownContainer.removeAllViews();
        titleView.setText(STR_SOFTWARE_WARNING_TITLE);
        descriptionView.setText(STR_SOFTWARE_WARNING_DESC);

        TextView proceed = new TextView(this);
        proceed.setText(STR_SOFTWARE_WARNING_PROCEED);
        proceed.setTextColor(ERROR_COLOR);
        proceed.setPadding(dp(8), dp(16), dp(8), dp(8));
        proceed.setOnClickListener(v -> beginEnrollment(pendingSoftwarePin, true));
        countdownContainer.addView(proceed);

        TextView cancel = new TextView(this);
        cancel.setText(STR_SOFTWARE_WARNING_CANCEL);
        cancel.setTextColor(SUBTEXT_COLOR);
        cancel.setPadding(dp(8), dp(16), dp(8), dp(8));
        cancel.setOnClickListener(v -> showFirstEnrollment());
        countdownContainer.addView(cancel);
    }

    private void submit() {
        if (inputLength < PrimePinContract.MIN_PIN_LENGTH) {
            return;
        }
        char[] pin = takeInputCopy();
        switch (mode) {
            case ENROLL_FIRST:
                firstPin = pin;
                showEnrollConfirm();
                break;
            case ENROLL_CONFIRM:
                if (PrimePinKdf.constantTimeEquals(firstPin, pin)) {
                    beginEnrollment(firstPin, false);
                } else {
                    java.util.Arrays.fill(pin, '\0');
                    showFirstEnrollment();
                    flashError(STR_MISMATCH);
                }
                break;
            case UNLOCK:
                handleVerification(pin, false);
                break;
            case DISABLE_VERIFY:
                handleDisable(pin);
                break;
            case EMERGENCY_VERIFY:
                handleVerification(pin, true);
                break;
            case EMERGENCY_FIRST:
                firstPin = pin;
                showEmergencyConfirm();
                break;
            case EMERGENCY_CONFIRM:
                if (PrimePinKdf.constantTimeEquals(firstPin, pin)) {
                    finishEmergencyEnrollment(firstPin);
                } else {
                    showEmergencyFirst();
                    flashError(STR_MISMATCH);
                }
                break;
            default:
                break;
        }
    }

    private void showEnrollConfirm() {
        mode = Mode.ENROLL_CONFIRM;
        clearInput();
        renderKeypad();
        countdownContainer.removeAllViews();
        titleView.setText(STR_CONFIRM_TITLE);
        descriptionView.setText("");
    }

    private void beginEnrollment(char[] pin, boolean acceptSoftware) {
        setBusy(true);
        executor.submit(() -> {
            PrimePinVault.EnrollmentResult result = vault.enroll(pin, acceptSoftware);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (result == PrimePinVault.EnrollmentResult.ENROLLED) {
                    PrimePinSession.setEnabled(true);
                    completeGate();
                } else if (result == PrimePinVault.EnrollmentResult.SOFTWARE_CONSENT_REQUIRED) {
                    showSoftwareWarning(pin);
                } else {
                    showFirstEnrollment();
                    flashError(genericErrorWithDetail(result.name()));
                }
            });
        });
    }

    private void finishEmergencyEnrollment(char[] pin) {
        setBusy(true);
        executor.submit(() -> {
            PrimePinVault.EmergencyStatus status = vault.enrollEmergency(pin);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (status == PrimePinVault.EmergencyStatus.SET || status == PrimePinVault.EmergencyStatus.REMOVED) {
                    completeGate();
                } else if (status == PrimePinVault.EmergencyStatus.SAME_AS_PRIMARY) {
                    showEmergencyFirst();
                    flashError(STR_EMERGENCY_SAME_AS_PRIMARY);
                } else {
                    showEmergencyFirst();
                    flashError(genericErrorWithDetail(status.name()));
                }
            });
        });
    }

    private void handleVerification(char[] pin, boolean emergencySetupFlow) {
        setBusy(true);
        executor.submit(() -> {
            PrimePinVault.VerificationResult result = vault.verify(pin);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                switch (result.status) {
                    case ACCEPTED:
                        if (emergencySetupFlow && isDisableEmergencyRequested()) {
                            finishEmergencyEnrollment(null);
                        } else if (emergencySetupFlow) {
                            showEmergencyFirst();
                        } else {
                            completeGate();
                        }
                        break;
                    case EMERGENCY:
                        beginEmergencyWipe();
                        break;
                    case LOCKED:
                        setBusy(false);
                        renderKeypad(); // keypad stays live during lockout - see class doc on emergency PIN
                        clearInput();
                        startCountdown(result.retryAfterMillis);
                        break;
                    case REJECTED:
                        setBusy(false);
                        renderKeypad();
                        clearInput();
                        flashError(STR_WRONG);
                        break;
                    default:
                        setBusy(false);
                        showFatal();
                        break;
                }
            });
        });
    }

    private void handleDisable(char[] pin) {
        setBusy(true);
        executor.submit(() -> {
            PrimePinVault.VerificationResult result = vault.disable(pin);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.status == PrimePinVault.VerificationStatus.EMERGENCY) {
                    beginEmergencyWipe();
                } else if (result.status == PrimePinVault.VerificationStatus.ACCEPTED) {
                    PrimePinSession.setEnabled(false);
                    completeGate();
                } else {
                    setBusy(false);
                    renderKeypad();
                    clearInput();
                    flashError(STR_WRONG);
                }
            });
        });
    }

    private void beginEmergencyWipe() {
        mode = Mode.FATAL;
        keypadGrid.removeAllViews();
        countdownContainer.removeAllViews();
        setBusy(true);
        // Visually indistinguishable from a correct PIN, deliberately.
        descriptionView.setText(STR_CHECKING);
        executor.submit(() -> {
            org.telegram.messenger.PrimeEmergencyWipe.run(getApplicationContext());
            runOnUiThread(() -> {
                Intent intent = new Intent(this, LaunchActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finishAffinity();
                System.exit(0);
            });
        });
    }

    private void completeGate() {
        PrimePinSession.unlock();
        startActivity(PrimePinSession.takePendingLaunch(this));
        overridePendingTransition(0, 0);
        finish();
    }

    // ─── lockout countdown ────────────────────────────────────────────────

    private void startCountdown(long durationMs) {
        final long endAt = System.currentTimeMillis() + durationMs;
        countdownContainer.removeAllViews();
        TextView countdownText = new TextView(this);
        countdownText.setTextColor(ERROR_COLOR);
        countdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        countdownText.setGravity(Gravity.CENTER);
        countdownContainer.addView(countdownText);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                long remaining = endAt - System.currentTimeMillis();
                if (remaining <= 0) {
                    countdownContainer.removeAllViews();
                    showUnlock();
                    return;
                }
                long totalSeconds = remaining / 1000;
                long h = totalSeconds / 3600;
                long m = (totalSeconds % 3600) / 60;
                long s = totalSeconds % 60;
                countdownText.setText(STR_LOCKED + " " + String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s));
                root.postDelayed(this, 1000);
            }
        };
        setBusy(false);
        countdownRunnable.run();
    }

    /** Was a bare "something went wrong, try again" - useless for a feature that's never run on
     *  real hardware before and fails in whatever OEM-specific Keystore/StrongBox way that
     *  hardware fails in. {@code resultName} (the enum constant, e.g. "UNAVAILABLE") plus
     *  {@link PrimePinVault#lastError()} (the actual exception class+message the vault's own
     *  catch block just recorded) turns a bug report into something actionable instead of a
     *  screenshot of six words. */
    private static String genericErrorWithDetail(String resultName) {
        String detail = PrimePinVault.lastError();
        return STR_GENERIC_ERROR + " (" + resultName + (detail != null ? ": " + detail : "") + ")";
    }

    private void flashError(String message) {
        descriptionView.setTextColor(ERROR_COLOR);
        descriptionView.setText(message);
        root.postDelayed(() -> descriptionView.setTextColor(SUBTEXT_COLOR), 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        java.util.Arrays.fill(input, '\0');
        if (firstPin != null) java.util.Arrays.fill(firstPin, '\0');
        if (pendingSoftwarePin != null) java.util.Arrays.fill(pendingSoftwarePin, '\0');
        if (executor != null) {
            executor.shutdownNow();
        }
        if (countdownRunnable != null && root != null) {
            root.removeCallbacks(countdownRunnable);
        }
    }
}
