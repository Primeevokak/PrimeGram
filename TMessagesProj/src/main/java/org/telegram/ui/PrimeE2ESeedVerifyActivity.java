package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PrimeE2EManager;
import org.telegram.messenger.PrimeE2EState;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram E2E: per-chat safety-number screen - shows a short numeric code derived
 * from both sides' long-term signing identities plus a manually-agreed seed phrase (the
 * seed phrase itself is never sent anywhere by the app - see {@code PrimeE2EManager
 * .computeVerificationCode}'s doc), lets the user mark the chat verified once they've
 * compared the code with the other person out of band, and offers a destructive session
 * reset for ratchet-desync recovery.
 */
public class PrimeE2ESeedVerifyActivity extends BaseFragment {

    private final long dialogId;
    private TextView codeView;
    private EditText seedField;

    public PrimeE2ESeedVerifyActivity(long dialogId) {
        super();
        this.dialogId = dialogId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Проверка E2E-шифрования");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        TextView explain = new TextView(context);
        explain.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        explain.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        explain.setText("Введите фразу, о которой вы заранее договорились с собеседником вне "
                + "Telegram (голосом, лично). Сравните получившийся код с собеседником — если он "
                + "совпадает, переписка защищена от подмены при установке соединения. Само "
                + "приложение эту фразу никуда не передаёт.");
        root.addView(explain, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        seedField = new EditTextBoldCursor(context);
        seedField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        seedField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        seedField.setHint("Секретная фраза");
        seedField.setSingleLine(true);
        // PrimeGram E2E: pre-fill with whatever was typed here last time - previously
        // this field always started blank, which read as "my phrase gets deleted and
        // never saves" even though the resulting code was computed correctly each time.
        seedField.setText(PrimeE2EState.getSeedPhrase(dialogId));
        root.addView(seedField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        codeView = new TextView(context);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        root.addView(codeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));

        TextView verifyButton = new TextView(context);
        verifyButton.setGravity(Gravity.CENTER);
        verifyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        verifyButton.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        verifyButton.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6),
                getThemedColor(Theme.key_featuredStickers_addButton)));
        verifyButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        verifyButton.setText("Коды совпадают — подтвердить");
        verifyButton.setOnClickListener(v -> {
            PrimeE2EState.setVerified(dialogId, true);
            finishFragment();
        });
        root.addView(verifyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView resetButton = new TextView(context);
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        resetButton.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        resetButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        resetButton.setText("Сбросить сессию");
        resetButton.setOnClickListener(v -> confirmReset());
        root.addView(resetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        seedField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                PrimeE2EState.setSeedPhrase(dialogId, s.toString());
                refreshCode();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        scrollView.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = scrollView;
        refreshCode();
        return scrollView;
    }

    private void refreshCode() {
        String code = PrimeE2EManager.computeVerificationCode(dialogId, seedField.getText().toString());
        codeView.setText(code != null ? code : "——————");
    }

    private void confirmReset() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Сбросить сессию?");
        builder.setMessage("Переписка будет установлена заново. Уже полученные зашифрованные "
                + "сообщения перестанут расшифровываться.");
        builder.setPositiveButton("Сбросить", (dialog, which) -> {
            PrimeE2EManager.disableEncryption(currentAccount, dialogId);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
