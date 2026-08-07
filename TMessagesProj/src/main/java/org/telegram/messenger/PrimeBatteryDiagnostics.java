package org.telegram.messenger;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PrimeGram: what this app can honestly say about its own battery use, for pasting into a bug
 * report.
 *
 * <p>Android has no API that hands a normal app the per-mAh figures the system's own "Battery
 * usage" screen shows for another app - that number comes from {@code BatteryStatsService}, gated
 * behind the signature-level {@code android.permission.BATTERY_STATS}, which no app installed
 * outside {@code /system} can hold. Faking that number from something else would be worse than not
 * showing it: a person reporting a battery complaint would be handed a plausible-looking figure
 * with no real meaning behind it. What this collects instead is everything a normal app is
 * actually allowed to read - the instantaneous battery snapshot, whether Android is letting this
 * process run unrestricted in the background, and every PrimeGram-specific background loop that
 * could plausibly be the reason - plus, if the person has separately granted Usage Access, this
 * app's own real foreground/background time from {@link UsageStatsManager}, which is the one
 * genuinely comparable number to what that system screen shows.
 */
public final class PrimeBatteryDiagnostics {

    private PrimeBatteryDiagnostics() {
    }

    public static boolean hasUsageAccess(Context context) {
        try {
            final AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            final int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String dump(Context context) {
        final StringBuilder sb = new StringBuilder();
        sb.append("PrimeGram — диагностика энергопотребления\n");
        sb.append(LocaleController.getInstance().getFormatterStats().format(System.currentTimeMillis())).append("\n");
        sb.append("Версия: ").append(BuildVars.BUILD_VERSION_STRING).append('\n');
        sb.append("Устройство: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append('\n');

        appendBatterySnapshot(context, sb);
        sb.append('\n');
        appendBackgroundState(context, sb);
        sb.append('\n');
        appendUsageStats(context, sb);

        sb.append("\nПояснение: Android не позволяет обычному приложению читать точную статистику мА·ч по процессам "
                + "(это системное разрешение BATTERY_STATS) — этих цифр здесь нет намеренно, чтобы не показывать "
                + "правдоподобные, но выдуманные числа. Разделы выше — то, что действительно доступно этому приложению.");
        return sb.toString();
    }

    private static void appendBatterySnapshot(Context context, StringBuilder sb) {
        sb.append("== Батарея (снимок сейчас) ==\n");
        try {
            final Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                sb.append("недоступно\n");
                return;
            }
            final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            final int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            final int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            final int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            if (level >= 0 && scale > 0) {
                sb.append("Заряд: ").append(level * 100 / scale).append("%\n");
            }
            sb.append("Состояние: ").append(batteryStatusName(status)).append('\n');
            if (plugged != 0) {
                sb.append("Питание: ").append(batteryPluggedName(plugged)).append('\n');
            }
            if (temp >= 0) {
                sb.append("Температура: ").append(temp / 10.0).append(" °C\n");
            }
            if (voltage > 0) {
                sb.append("Напряжение: ").append(voltage).append(" мВ\n");
            }
        } catch (Throwable t) {
            sb.append("ошибка чтения: ").append(t).append('\n');
        }
    }

    private static String batteryStatusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "заряжается";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "разряжается";
            case BatteryManager.BATTERY_STATUS_FULL: return "полный";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "не заряжается";
            default: return "неизвестно";
        }
    }

    private static String batteryPluggedName(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "сеть";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "беспроводная зарядка";
            default: return "нет";
        }
    }

    /** Every PrimeGram-specific loop that keeps running while the app is backgrounded - the
     *  actual candidates for "why is this draining battery", as opposed to the OS-level snapshot
     *  above which says nothing about which of our own features is responsible. */
    private static void appendBackgroundState(Context context, StringBuilder sb) {
        sb.append("== Фоновые подсистемы PrimeGram ==\n");
        try {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            final boolean ignoringOptimizations = pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
            sb.append("Игнорирует оптимизацию батареи: ").append(ignoringOptimizations ? "да" : "нет").append('\n');
        } catch (Throwable t) {
            sb.append("Игнорирует оптимизацию батареи: ошибка чтения\n");
        }

        try {
            final boolean vpnProxyRunning = vpn.sdk.VpnSDK.isProxyRunning();
            sb.append("VLESS-прокси (xray): ").append(vpnProxyRunning ? "запущен" : "выключен").append('\n');
        } catch (Throwable t) {
            sb.append("VLESS-прокси (xray): не инициализирован\n");
        }

        final boolean tgwsEnabled = MessagesController.getGlobalMainSettings().getBoolean("primegram_tgws_enabled", true);
        sb.append("TgWs-прокси: ").append(tgwsEnabled ? "включён" : "выключен")
                .append(", сокет ").append(TgWsProxyService.isSocketBound ? "занят" : "свободен").append('\n');

        final boolean vpnGuardOn = PrimeVpnGuard.isEnabled();
        sb.append("Отключение прокси при VPN: ").append(vpnGuardOn ? "включено (слушает смену сети)" : "выключено").append('\n');

        try {
            final int pluginCount = org.telegram.messenger.plugins.PrimePluginsController.getInstance().count();
            final boolean pluginsRunning = org.telegram.messenger.plugins.PrimePluginsController.getInstance().hasEnabledPlugins();
            sb.append("Плагины: ").append(pluginCount).append(" установлено, ")
                    .append(pluginsRunning ? "есть активные (Python-движок запущен)" : "все выключены").append('\n');
        } catch (Throwable t) {
            sb.append("Плагины: ошибка чтения\n");
        }

        try {
            final long uptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
            sb.append("Процесс работает: ").append(formatDuration(uptimeMs)).append('\n');
        } catch (Throwable t) {
            // getStartElapsedRealtime() needs API 24; below that, just omit the line.
        }
    }

    private static void appendUsageStats(Context context, StringBuilder sb) {
        sb.append("== Реальное время работы (за последние 24ч) ==\n");
        if (!hasUsageAccess(context)) {
            sb.append("Недоступно: разрешение \"Доступ к использованию\" не выдано. "
                    + "Без него это единственный раздел, который android не даёт заполнить настоящими цифрами.\n");
            return;
        }
        try {
            final UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                sb.append("недоступно (нет UsageStatsManager)\n");
                return;
            }
            final long end = System.currentTimeMillis();
            final long start = end - 24L * 60 * 60 * 1000;
            final List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end);
            if (stats == null || stats.isEmpty()) {
                sb.append("нет данных за период\n");
                return;
            }
            long totalForegroundMs = 0;
            final String ownPackage = context.getPackageName();
            for (UsageStats s : stats) {
                if (ownPackage.equals(s.getPackageName())) {
                    totalForegroundMs += s.getTotalTimeInForeground();
                }
            }
            sb.append("На переднем плане: ").append(formatDuration(totalForegroundMs)).append('\n');
            sb.append("(Android не даёт отдельно фоновое время через этот API - только суммарное "
                    + "\"на переднем плане\"; всё остальное время процесс либо не запущен, либо в фоне.)\n");
        } catch (Throwable t) {
            sb.append("ошибка чтения: ").append(t).append('\n');
        }
    }

    private static String formatDuration(long ms) {
        final long totalSeconds = ms / 1000;
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + " ч " + minutes + " мин";
        }
        if (minutes > 0) {
            return minutes + " мин " + seconds + " с";
        }
        return seconds + " с";
    }
}
