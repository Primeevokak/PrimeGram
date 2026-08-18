package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.music.PrimeEqualizer;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

/**
 * PrimeGram Music: equalizer + sound settings for the built-in player. Bands are
 * only known once {@code PrimeEqualizer} has attached to a live playback session
 * (mirrors {@code VideoPlayer}'s own lazy audio-session-id resolution) - if nothing
 * has played yet, this screen shows a hint asking the user to start a track first.
 */
public class PrimeMusicPlayerSettingsActivity extends BaseFragment {

    private LinearLayout bandsContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Эквалайзер и звук");
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
        root.setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        LinearLayout enableRow = new LinearLayout(context);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView enableLabel = new TextView(context);
        enableLabel.setText("Эквалайзер");
        enableLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_MEDIUM);
        enableLabel.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_PRIMARY);
        enableRow.addView(enableLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch enableSwitch = new Switch(context);
        enableSwitch.setChecked(PrimeEqualizer.isEnabled(), false);
        enableSwitch.setOnClickListener(v -> {
            boolean newState = !PrimeEqualizer.isEnabled();
            PrimeEqualizer.setEnabled(newState);
            enableSwitch.setChecked(newState, true);
            updateBandsEnabled();
        });
        enableRow.addView(enableSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(enableRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        bandsContainer = new LinearLayout(context);
        bandsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(bandsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        buildBands(context);

        scrollView.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = scrollView;
        return fragmentView;
    }

    private void buildBands(Context context) {
        bandsContainer.removeAllViews();
        short bandCount = PrimeEqualizer.getBandCount();
        if (bandCount <= 0) {
            TextView hint = new TextView(context);
            hint.setText("Запустите воспроизведение трека, чтобы настроить полосы эквалайзера.");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
            hint.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
            bandsContainer.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        short[] range = PrimeEqualizer.getBandLevelRange();
        int min = range[0];
        int max = range[1];
        int span = Math.max(1, max - min);
        for (short band = 0; band < bandCount; band++) {
            int freqHz = PrimeEqualizer.getCenterFreqHz(band);
            TextView label = new TextView(context);
            label.setText(freqHz >= 1000 ? (freqHz / 1000) + " кГц" : freqHz + " Гц");
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.music.PrimeMusicTheme.SP_SMALL);
            label.setTextColor(org.telegram.messenger.music.PrimeMusicTheme.TEXT_TERTIARY);
            bandsContainer.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            SeekBar seekBar = new SeekBar(context);
            seekBar.setMax(span);
            short current = PrimeEqualizer.getBandLevel(band);
            seekBar.setProgress(current - min);
            final short bandFinal = band;
            final int minFinal = min;
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        PrimeEqualizer.setBandLevel(bandFinal, (short) (progress + minFinal));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            bandsContainer.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void updateBandsEnabled() {
        bandsContainer.setAlpha(PrimeEqualizer.isEnabled() ? 1f : 0.5f);
        for (int i = 0; i < bandsContainer.getChildCount(); i++) {
            bandsContainer.getChildAt(i).setEnabled(PrimeEqualizer.isEnabled());
        }
    }
}
