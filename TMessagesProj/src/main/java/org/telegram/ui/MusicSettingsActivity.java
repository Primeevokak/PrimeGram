package org.telegram.ui;

import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.music.MusicPlatform;
import org.telegram.messenger.music.MusicResources;
import org.telegram.messenger.music.MusicSettingsStore;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Settings screen for the native "Музыка" tab — platform selection, per-platform tokens, card font. No donation UI. */
public class MusicSettingsActivity extends UniversalFragment {

    private static final int ID_TAB_ENABLED = 1;
    private static final int ID_PLATFORM_BASE = 100; // + MusicPlatform.id
    private static final int ID_PLATFORM_VALUE = 200;
    private static final int ID_LASTFM_KEY = 201;
    private static final int ID_YANDEX_API_URL = 202;
    private static final int ID_COBALT_API_URL = 203;
    private static final int ID_YTM_LOGIN = 204;
    private static final int ID_FONT_BASE = 300; // + index into FONT_FAMILIES
    private static final int ID_DOWNLOAD_FONTS = 400;
    private static final int ID_CLEAR_FONTS = 401;

    @Override
    protected CharSequence getTitle() {
        return "Музыка";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        UItem enabledItem = UItem.asCheck(ID_TAB_ENABLED, "Показывать вкладку «Музыка»");
        enabledItem.checked = MusicSettingsStore.isTabEnabled();
        items.add(enabledItem);
        items.add(UItem.asShadow("Добавляет вкладку «Музыка» в панель эмодзи/стикеров для отправки текущего трека в чат."));

        items.add(UItem.asHeader("Платформа"));
        MusicPlatform selected = MusicSettingsStore.getSelectedPlatform();
        for (MusicPlatform platform : MusicPlatform.values()) {
            if (platform == MusicPlatform.NOT_SELECTED) continue;
            UItem item = UItem.asCheck(ID_PLATFORM_BASE + platform.id, platform.displayName);
            item.checked = selected == platform;
            items.add(item);
        }
        items.add(UItem.asShadow(platformHint(selected)));

        if (selected != MusicPlatform.NOT_SELECTED && selected != MusicPlatform.TG_MUSIC) {
            items.add(UItem.asHeader("Учётные данные"));
            String value = MusicSettingsStore.getPlatformValue(selected);
            items.add(UItem.asButton(ID_PLATFORM_VALUE, valueLabel(selected), value.isEmpty() ? "Не задано" : "•••" + lastChars(value)));

            if (selected == MusicPlatform.LASTFM) {
                String apiKey = MusicSettingsStore.getLastFmApiKey();
                items.add(UItem.asButton(ID_LASTFM_KEY, "Last.fm API key", apiKey.isEmpty() ? "Не задан" : "•••" + lastChars(apiKey)));
                items.add(UItem.asShadow("Бесплатный API-ключ можно получить на last.fm/api/account/create"));
            }
            if (selected == MusicPlatform.YANDEX_MUSIC) {
                String apiUrl = MusicSettingsStore.getYandexCustomApiUrl();
                items.add(UItem.asButton(ID_YANDEX_API_URL, "Свой API-хост (необязательно)", apiUrl.isEmpty() ? "По умолчанию" : apiUrl));
            }
            if (selected == MusicPlatform.YOUTUBE_MUSIC) {
                items.add(UItem.asButton(ID_YTM_LOGIN, "Войти через браузер", "получить cookie автоматически"));
                items.add(UItem.asShadow("Откроется обычный вход в аккаунт Google внутри приложения — cookie сессии YouTube Music сохранится и подставится сюда сама. Пароль или код нигде не сохраняются, только cookie сессии, как в браузере."));
            }
        }

        if (selected != MusicPlatform.NOT_SELECTED) {
            items.add(UItem.asHeader("Скачивание аудио"));
            items.add(UItem.asButton(ID_COBALT_API_URL, "Cobalt API", MusicSettingsStore.getCobaltApiUrl()));
            items.add(UItem.asShadow("Используется для скачивания аудиофайла трека при отправке «Аудио»."));
        }

        items.add(UItem.asHeader("Оформление карточки"));
        String currentFont = MusicSettingsStore.getFontFamily();
        for (int i = 0; i < MusicResources.FONT_FAMILIES.length; i++) {
            String family = MusicResources.FONT_FAMILIES[i];
            UItem item = UItem.asCheck(ID_FONT_BASE + i, family);
            item.checked = family.equals(currentFont);
            items.add(item);
        }
        boolean hasOverride = MusicResources.hasOverride();
        items.add(UItem.asShadow(hasOverride
                ? "Дополнительные шрифты (Onest, Circular, YS Text/Music, Noto Sans JP) встроены в приложение; сейчас используется ваш скачанный оверрайд вместо них."
                : "Дополнительные шрифты (Onest, Circular, YS Text/Music, Noto Sans JP) встроены в приложение и доступны сразу — скачивать ничего не нужно."));
        items.add(UItem.asButton(ID_DOWNLOAD_FONTS, "Заменить своими шрифтами", hasOverride ? "Оверрайд активен" : ""));
        if (hasOverride) {
            items.add(UItem.asButton(ID_CLEAR_FONTS, "Убрать свои шрифты", ""));
        }
    }

    private String platformHint(MusicPlatform platform) {
        switch (platform) {
            case SPOTIFY:
                return "Через stats.fm — укажите публичный юзернейм (не пароль).";
            case YANDEX_MUSIC:
                return "Нужен токен доступа Яндекс.Музыки.";
            case SOUNDCLOUD:
                return "Нужен OAuth-токен SoundCloud.";
            case VK_MUSIC:
                return "Нужен access_token ВКонтакте с доступом к статусу. Скачивание аудио для VK недоступно.";
            case TG_MUSIC:
                return "Показывает то, что сейчас играет прямо в Telegram — отдельная настройка не нужна.";
            case LASTFM:
                return "Нужны юзернейм Last.fm и свой API-ключ. Скачивание аудио недоступно.";
            case YOUTUBE_MUSIC:
                return "Нужен cookie сессии YouTube Music — войдите через браузер ниже. Показывает последний трек из истории прослушиваний (у YouTube Music нет публичного API «играет прямо сейчас»). Скачивание аудио недоступно.";
            default:
                return "Выберите платформу, с которой брать текущий трек.";
        }
    }

    private String valueLabel(MusicPlatform platform) {
        switch (platform) {
            case SPOTIFY:
                return "Юзернейм stats.fm";
            case YANDEX_MUSIC:
                return "Токен Яндекс.Музыки";
            case SOUNDCLOUD:
                return "OAuth-токен SoundCloud";
            case VK_MUSIC:
                return "Access token VK";
            case LASTFM:
                return "Юзернейм Last.fm";
            case YOUTUBE_MUSIC:
                return "Cookie YouTube Music";
            default:
                return "Значение";
        }
    }

    private String lastChars(String s) {
        return s.length() <= 4 ? s : s.substring(s.length() - 4);
    }

    @Override
    protected void onClick(UItem item, android.view.View view, int position, float x, float y) {
        if (item.id == ID_TAB_ENABLED) {
            MusicSettingsStore.setTabEnabled(!MusicSettingsStore.isTabEnabled());
            listView.adapter.update(true);
        } else if (item.id >= ID_PLATFORM_BASE && item.id < ID_PLATFORM_BASE + 100) {
            MusicPlatform platform = MusicPlatform.fromId(item.id - ID_PLATFORM_BASE);
            MusicSettingsStore.setSelectedPlatform(platform);
            listView.adapter.update(true);
        } else if (item.id == ID_PLATFORM_VALUE) {
            MusicPlatform platform = MusicSettingsStore.getSelectedPlatform();
            showTextInput(valueLabel(platform), MusicSettingsStore.getPlatformValue(platform), false,
                    newValue -> { MusicSettingsStore.setPlatformValue(platform, newValue); listView.adapter.update(true); });
        } else if (item.id == ID_LASTFM_KEY) {
            showTextInput("Last.fm API key", MusicSettingsStore.getLastFmApiKey(), false,
                    newValue -> { MusicSettingsStore.setLastFmApiKey(newValue); listView.adapter.update(true); });
        } else if (item.id == ID_YANDEX_API_URL) {
            showTextInput("Свой API-хост", MusicSettingsStore.getYandexCustomApiUrl(), false,
                    newValue -> { MusicSettingsStore.setYandexCustomApiUrl(newValue); listView.adapter.update(true); });
        } else if (item.id == ID_COBALT_API_URL) {
            showTextInput("Cobalt API", MusicSettingsStore.getCobaltApiUrl(), false,
                    newValue -> { MusicSettingsStore.setCobaltApiUrl(newValue.isEmpty() ? org.telegram.messenger.music.CobaltDownloader.DEFAULT_API_URL : newValue); listView.adapter.update(true); });
        } else if (item.id >= ID_FONT_BASE && item.id < ID_FONT_BASE + MusicResources.FONT_FAMILIES.length) {
            String family = MusicResources.FONT_FAMILIES[item.id - ID_FONT_BASE];
            MusicSettingsStore.setFontFamily(family);
            listView.adapter.update(true);
        } else if (item.id == ID_DOWNLOAD_FONTS) {
            showTextInput("Адрес ресурсов (URL со шрифтами)", "", false, baseUrl -> {
                if (baseUrl.isEmpty()) return;
                MusicResources.downloadResources(baseUrl, success -> {
                    if (getParentActivity() == null) return;
                    android.widget.Toast.makeText(getParentActivity(), success ? "Шрифты скачаны" : "Не удалось скачать часть файлов", android.widget.Toast.LENGTH_SHORT).show();
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                });
            });
        } else if (item.id == ID_CLEAR_FONTS) {
            MusicResources.clearResources();
            listView.adapter.update(true);
        } else if (item.id == ID_YTM_LOGIN) {
            presentFragment(new MusicYtmLoginActivity(cookie -> {
                MusicSettingsStore.setPlatformValue(MusicPlatform.YOUTUBE_MUSIC, cookie);
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            }));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, android.view.View view, int position, float x, float y) {
        return false;
    }

    private interface TextInputCallback {
        void onResult(String value);
    }

    private void showTextInput(String title, String currentValue, boolean numeric, TextInputCallback callback) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        EditText editText = new EditText(getParentActivity());
        editText.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        editText.setText(currentValue);
        editText.setSelection(editText.getText().length());
        builder.setView(editText);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> callback.onResult(editText.getText().toString().trim()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
