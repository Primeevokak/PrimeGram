package org.telegram.messenger;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.MusicSettingsActivity;
import org.telegram.ui.PrimeGramSettingsActivity;
import org.telegram.ui.PrimeTgWsActivity;
import org.telegram.ui.ProfileActivity;

/**
 * PrimeGram: feeds this fork's own settings rows into stock Telegram's in-app settings search
 * ({@code ProfileActivity.SearchAdapter}) - without this, none of it is findable by typing in
 * that search box, only by knowing the row exists and where to scroll for it.
 *
 * <p>Deliberately its own file, appended to the stock array from one line in
 * {@code ProfileActivity.onCreateSearchArray()} rather than interleaved into that (huge, and
 * upstream-owned) literal - a stock-update merge only has to deal with the one appended line.
 *
 * <p>Row-level highlighting on arrival is intentionally NOT wired up here: stock's mechanism
 * ({@code SearchResult.rowName} + {@code AndroidUtilities.scrollToFragmentRow}) resolves the row
 * by reflecting on a private {@code int} field named after a legacy {@code rowCount++}-style
 * adapter - none of PrimeGram's settings screens are built that way (they're all
 * {@code UniversalFragment}/{@code UniversalAdapter} with stable {@code UItem} ids instead), so
 * that reflection would just silently fail to find the field and highlight nothing. These entries
 * still navigate to the exact right screen; they just don't flash a specific row once there.
 *
 * <p>{@code guid}s are kept in the 90000+ range, well clear of stock's own (which top out in the
 * low thousands as of this writing) - {@code SearchResult.equals()} keys off {@code guid} alone,
 * used for the "recent searches" list, so a collision there would misfile a recent search under
 * the wrong result.
 */
public final class PrimeSettingsSearch {

    private PrimeSettingsSearch() {
    }

    private static final String ROOT = "PrimeGram";

    public static ProfileActivity.SearchAdapter.SearchResult[] appendTo(
            ProfileActivity.SearchAdapter.SearchResult[] stock, BaseFragment f) {
        ProfileActivity.SearchAdapter.SearchResult[] own = build(f);
        ProfileActivity.SearchAdapter.SearchResult[] combined =
                new ProfileActivity.SearchAdapter.SearchResult[stock.length + own.length];
        System.arraycopy(stock, 0, combined, 0, stock.length);
        System.arraycopy(own, 0, combined, stock.length, own.length);
        return combined;
    }

    private static ProfileActivity.SearchAdapter.SearchResult[] build(BaseFragment f) {
        return new ProfileActivity.SearchAdapter.SearchResult[]{
                new ProfileActivity.SearchAdapter.SearchResult(90000, "PrimeGram", 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90001, "Боковая панель", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90002, "Форма аватарок", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90003, "Мини-аватарки отправителей", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90004, "Плагины", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90005, "Блоки (автоматизация)", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90006, "Показывать ID и DC", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90007, "Показывать кнопки админа", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90010, "TgWs-прокси", ROOT, 0,
                        () -> f.presentFragment(new PrimeTgWsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90011, "Работа в фоне (TgWs)", ROOT, 0,
                        () -> f.presentFragment(new PrimeTgWsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90012, "Свои Cloudflare Workers", ROOT, 0,
                        () -> f.presentFragment(new PrimeTgWsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90013, "Аварийный прокси", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90020, "Скрытая зона (Grey Zone)", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90021, "Скрыть номер телефона", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90030, "PIN-код", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90031, "Аварийный PIN", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90040, "Музыка", ROOT, 0,
                        () -> f.presentFragment(new MusicSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90041, "Подпись к треку", ROOT, 0,
                        () -> f.presentFragment(new MusicSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90050, "Переводчик", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90051, "Панель инструментов текста", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90052, "Временные подписки", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90053, "Метки сообщений", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),

                new ProfileActivity.SearchAdapter.SearchResult(90060, "Оптимизации", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90061, "Монитор производительности", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
                new ProfileActivity.SearchAdapter.SearchResult(90062, "Журнал крашей", ROOT, 0,
                        () -> f.presentFragment(new PrimeGramSettingsActivity())),
        };
    }
}
