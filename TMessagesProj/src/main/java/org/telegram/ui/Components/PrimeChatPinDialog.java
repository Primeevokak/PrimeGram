package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.CycleInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeChatPin;
import org.telegram.messenger.PrimePinKdf;
import org.telegram.messenger.PrimePinVault;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.music.PrimeMusicTheme;

/**
 * PrimeGram: enroll/unlock dialogs for a per-chat PIN ({@link PrimeChatPin}), built on
 * the same dark card shell every other custom Music-tab dialog this session already
 * uses ({@link PrimeMusicDialog}) for a consistent look, plus the new
 * {@link PrimeChatPinKeypadView}. PBKDF2 (600k iterations) always runs off
 * {@link Utilities#globalQueue}, never the calling thread - it is deliberately slow.
 */
public final class PrimeChatPinDialog {

    public interface EnrollCallback {
        void onEnrolled();
    }

    /** {@code unlocked} is {@code false} both for a wrong/locked-out PIN attempt that
     *  gave up and for the dialog being dismissed (back button, outside tap) without
     *  ever succeeding - a caller gating real content on this must treat both the same
     *  way: content stays hidden. */
    public interface UnlockCallback {
        void onResult(boolean unlocked);
    }

    private PrimeChatPinDialog() {
    }

    public static void showEnroll(Activity activity, long dialogId, EnrollCallback onDone) {
        if (activity == null) {
            return;
        }
        Context context = activity;
        LinearLayout card = PrimeMusicDialog.newTitledCard(context, "PIN для этого чата");
        card.addView(PrimeMusicDialog.bodyText(context, "Придумайте PIN из 4-6 цифр"),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        PrimeChatPinKeypadView keypad = new PrimeChatPinKeypadView(context);
        LinearLayout.LayoutParams keypadParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 4);
        keypadParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(keypad, keypadParams);

        TextView errorText = new TextView(context);
        errorText.setTextColor(0xFFFF3B5C);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        errorText.setGravity(Gravity.CENTER);
        card.addView(errorText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);

        // Mutable across the two steps of this one dialog's lifetime - "first" holds the
        // step-1 entry while step 2 collects the confirmation, wiped the moment either
        // step resolves (match, mismatch, or the dialog closing).
        char[][] firstPinHolder = new char[1][];

        keypad.setListener(pin -> {
            if (firstPinHolder[0] == null) {
                firstPinHolder[0] = pin.clone();
                errorText.setText("");
                ((TextView) card.getChildAt(1)).setText("Повторите PIN");
                keypad.reset();
                return;
            }
            char[] first = firstPinHolder[0];
            boolean matches = PrimePinKdf.constantTimeEquals(first, pin);
            if (!matches) {
                java.util.Arrays.fill(first, '\0');
                firstPinHolder[0] = null;
                errorText.setText("PIN не совпадает - начните заново");
                shake(keypad);
                keypad.reset();
                ((TextView) card.getChildAt(1)).setText("Придумайте PIN из 4-6 цифр");
                return;
            }
            attemptEnroll(activity, dialogId, pin, false, card, errorText, keypad, dialogRef[0], onDone);
            java.util.Arrays.fill(first, '\0');
            firstPinHolder[0] = null;
        });
    }

    private static void attemptEnroll(Activity activity, long dialogId, char[] pin, boolean acceptSoftware,
                                       LinearLayout card, TextView errorText, PrimeChatPinKeypadView keypad,
                                       android.app.AlertDialog dialog, EnrollCallback onDone) {
        char[] pinCopy = pin.clone();
        Utilities.globalQueue.postRunnable(() -> {
            PrimePinVault.EnrollmentResult result = PrimeChatPin.enroll(activity, dialogId, pinCopy, acceptSoftware);
            java.util.Arrays.fill(pinCopy, '\0');
            AndroidUtilities.runOnUIThread(() -> {
                if (result == PrimePinVault.EnrollmentResult.ENROLLED) {
                    dialog.dismiss();
                    if (onDone != null) {
                        onDone.onEnrolled();
                    }
                } else if (result == PrimePinVault.EnrollmentResult.SOFTWARE_CONSENT_REQUIRED) {
                    errorText.setText("Устройство без защищённого чипа - PIN будет храниться менее надёжно");
                    attemptEnroll(activity, dialogId, pin, true, card, errorText, keypad, dialog, onDone);
                } else {
                    errorText.setText("Не удалось включить PIN, попробуйте снова");
                    keypad.reset();
                }
            });
        });
    }

    public static void showUnlock(Activity activity, long dialogId, UnlockCallback onResult) {
        showVerify(activity, "Чат защищён PIN-ом", "Введите PIN, чтобы открыть этот чат",
                (ctx, pin) -> PrimeChatPin.verify(ctx, dialogId, pin), onResult);
    }

    /** Removing a chat's PIN needs the PIN itself, same as {@link PrimePinVault#disable}
     *  for the app-wide one - one entry, not "unlock then confirm again", since a second
     *  prompt right after the first would just be asking the same proof of knowledge
     *  twice. */
    public static void showRemove(Activity activity, long dialogId, Runnable onRemoved) {
        showVerify(activity, "Снять шифрование", "Введите PIN, чтобы снять шифрование с этого чата",
                (ctx, pin) -> PrimeChatPin.remove(ctx, dialogId, pin), unlocked -> {
            if (unlocked && onRemoved != null) {
                onRemoved.run();
            }
        });
    }

    private interface VerifyOp {
        PrimePinVault.VerificationResult run(Context context, char[] pin);
    }

    private static void showVerify(Activity activity, String title, String subtitleText, VerifyOp op, UnlockCallback onResult) {
        if (activity == null) {
            if (onResult != null) {
                onResult.onResult(false);
            }
            return;
        }
        Context context = activity;
        LinearLayout card = PrimeMusicDialog.newTitledCard(context, title);
        TextView subtitle = PrimeMusicDialog.bodyText(context, subtitleText);
        card.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        PrimeChatPinKeypadView keypad = new PrimeChatPinKeypadView(context);
        LinearLayout.LayoutParams keypadParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 4);
        keypadParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(keypad, keypadParams);

        TextView errorText = new TextView(context);
        errorText.setTextColor(0xFFFF3B5C);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        errorText.setGravity(Gravity.CENTER);
        card.addView(errorText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        boolean[] resolved = {false};
        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);
        dialogRef[0].setOnDismissListener(d -> {
            if (!resolved[0] && onResult != null) {
                onResult.onResult(false);
            }
        });

        keypad.setListener(pin -> {
            char[] pinCopy = pin.clone();
            Utilities.globalQueue.postRunnable(() -> {
                PrimePinVault.VerificationResult result = op.run(activity, pinCopy);
                java.util.Arrays.fill(pinCopy, '\0');
                AndroidUtilities.runOnUIThread(() -> {
                    if (result.status == PrimePinVault.VerificationStatus.ACCEPTED
                            || result.status == PrimePinVault.VerificationStatus.EMERGENCY) {
                        resolved[0] = true;
                        dialogRef[0].dismiss();
                        if (onResult != null) {
                            onResult.onResult(true);
                        }
                    } else if (result.status == PrimePinVault.VerificationStatus.LOCKED) {
                        long seconds = result.retryAfterMillis / 1000;
                        errorText.setText("Слишком много попыток - повторите через " + formatDuration(seconds));
                        shake(keypad);
                        keypad.reset();
                    } else {
                        errorText.setText("Неверный PIN");
                        shake(keypad);
                        keypad.reset();
                    }
                });
            });
        });
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60;
        if (minutes < 60) {
            return minutes + " мин";
        }
        return (minutes / 60) + " ч " + (minutes % 60) + " мин";
    }

    private static void shake(android.view.View view) {
        view.animate().cancel();
        view.setTranslationX(0);
        android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(view, "translationX", 0, dp(10), -dp(10), dp(6), -dp(6), 0);
        shake.setDuration(320);
        shake.setInterpolator(new CycleInterpolator(2));
        shake.start();
    }
}
