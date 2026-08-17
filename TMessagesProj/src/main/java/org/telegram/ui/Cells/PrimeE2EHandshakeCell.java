package org.telegram.ui.Cells;

import android.content.Context;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PrimeE2EManager;
import org.telegram.messenger.PrimeInteractiveCellRegistry;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PrimeHoldButton;

/**
 * PrimeGram E2E: the interactive in-feed prompt for an X3DH handshake request -
 * concrete {@link PrimeInteractiveActionCell} subclass. Renders one of four states,
 * re-derived fresh from current {@code PrimeE2EManager}/session state every {@link #bind}
 * (including on the refresh {@code ChatActivity} triggers via
 * {@code NotificationCenter.primeE2EStateChanged}), never cached on the cell itself:
 *
 * <ul>
 * <li>receiver, request just arrived - hold "Отменить" / hold "Подтвердить"</li>
 * <li>sender, waiting for a reply - hold "Отменить" + a single-tap settings button that
 *     opens the seed-phrase screen (this is what actually lets the sender set the seed
 *     phrase before the handshake completes - previously impossible)</li>
 * <li>established - a quiet settled state, tap-through to the safety-number screen</li>
 * <li>cancelled/reset - neutral, no actions</li>
 * </ul>
 */
public class PrimeE2EHandshakeCell extends PrimeInteractiveActionCell {

    public interface Delegate {
        void openSeedVerify(long dialogId);
    }

    private Delegate delegate;

    public static void register() {
        PrimeInteractiveCellRegistry.register(new PrimeInteractiveCellRegistry.Factory() {
            @Override
            public boolean handles(MessageObject obj) {
                return PrimeE2EManager.isHandshakeRequestMarker(obj);
            }

            @Override
            public PrimeInteractiveActionCell create(Context context, Theme.ResourcesProvider resourcesProvider) {
                return new PrimeE2EHandshakeCell(context, resourcesProvider);
            }
        });
    }

    public PrimeE2EHandshakeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        iconView.setImageResource(R.drawable.msg_secret);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void bind(MessageObject obj) {
        currentMessageObject = obj;
        clearButtons();

        final long dialogId = obj.getDialogId();
        final int account = obj.currentAccount;
        final boolean mine = obj.messageOwner != null && obj.messageOwner.out;

        TLRPC.User peer = MessagesController.getInstance(account).getUser(dialogId);
        String peerName = peer != null ? ContactsController.formatName(peer.first_name, peer.last_name) : "";

        if (PrimeE2EManager.isSessionEstablished(dialogId)) {
            bindEstablished(dialogId);
        } else if (mine && PrimeE2EManager.isHandshakePending(dialogId)) {
            bindOutgoingPending(account, dialogId, peerName);
        } else if (!mine && PrimeE2EManager.isIncomingPending(dialogId)) {
            bindIncomingPending(account, dialogId, peerName);
        } else {
            bindResting(mine, peerName);
        }
    }

    private void bindIncomingPending(int account, long dialogId, String peerName) {
        titleView.setText(peerName.isEmpty()
                ? "Вам предлагают установить зашифрованную связь"
                : peerName + " предлагает установить зашифрованную связь");
        bodyView.setText("Подтвердите, только если доверяете отправителю");

        PrimeHoldButton cancel = new PrimeHoldButton(getContext(), "Отменить", accentColor(), cardBackgroundColor());
        cancel.setOnConfirmed(() -> {
            PrimeE2EManager.cancelHandshake(account, dialogId);
        });
        addButton(cancel);

        PrimeHoldButton confirm = new PrimeHoldButton(getContext(), "Подтвердить", accentColor(), cardBackgroundColor());
        confirm.setOnConfirmed(() -> {
            PrimeE2EManager.confirmIncomingHandshake(account, dialogId);
            if (delegate != null) {
                delegate.openSeedVerify(dialogId);
            }
        });
        addButton(confirm);
    }

    private void bindOutgoingPending(int account, long dialogId, String peerName) {
        titleView.setText(peerName.isEmpty()
                ? "Вы отправили запрос на зашифрованное общение"
                : "Вы отправили " + peerName + " запрос на зашифрованное общение");
        bodyView.setText("Ожидание ответа…");

        PrimeHoldButton cancel = new PrimeHoldButton(getContext(), "Отменить", accentColor(), cardBackgroundColor());
        cancel.setOnConfirmed(() -> PrimeE2EManager.cancelHandshake(account, dialogId));
        addButton(cancel);

        android.widget.TextView settings = new android.widget.TextView(getContext());
        settings.setText("⚙ Настройки");
        settings.setTextColor(accentColor());
        settings.setClickable(true);
        settings.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), org.telegram.messenger.AndroidUtilities.dp(10),
                org.telegram.messenger.AndroidUtilities.dp(14), org.telegram.messenger.AndroidUtilities.dp(10));
        settings.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.openSeedVerify(dialogId);
            }
        });
        addButton(settings);
    }

    private void bindEstablished(long dialogId) {
        titleView.setText("Зашифрованное соединение установлено");
        bodyView.setText("Нажмите, чтобы проверить код безопасности");

        android.widget.TextView open = new android.widget.TextView(getContext());
        open.setText("Открыть проверку");
        open.setTextColor(accentColor());
        open.setClickable(true);
        open.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), org.telegram.messenger.AndroidUtilities.dp(10),
                org.telegram.messenger.AndroidUtilities.dp(14), org.telegram.messenger.AndroidUtilities.dp(10));
        open.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.openSeedVerify(dialogId);
            }
        });
        addButton(open);
    }

    private void bindResting(boolean mine, String peerName) {
        titleView.setText("Запрос на шифрование отменён");
        bodyView.setText(mine ? "" : (peerName.isEmpty() ? "" : peerName));
    }
}
