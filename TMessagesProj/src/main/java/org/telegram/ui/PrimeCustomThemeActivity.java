package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PrimeCustomWallpapers;
import org.telegram.messenger.PrimeGradientPalettes;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.GradientTools;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.WallpaperUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: build-your-own chat theme. Full-bleed edge-to-edge layout, matching the approved
 * mockup exactly: a vertical pattern ribbon along the left edge, a vertical color-palette ribbon
 * along the right edge, a real chat-bubble live preview filling the center (the SAME cell stock
 * Telegram uses for its own settings previews - {@link ThemePreviewMessagesCell} - not a custom
 * mockup), and a collapsible RGB picker along the bottom that only appears once "+ Свой цвет" is
 * tapped. Saves into {@link PrimeCustomWallpapers}; shows up back in
 * {@link WallpapersListActivity}'s own grid through the exact same {@code ColorWallpaper}
 * rendering every stock wallpaper already uses (see
 * {@code WallpapersListActivity.insertPrimeCustomThemes()}).
 */
public class PrimeCustomThemeActivity extends BaseFragment {

    private final ArrayList<Object> builtinPatterns;

    private FrameLayout previewFrame;
    private MotionBackgroundDrawable previewDrawable;
    private ThemePreviewMessagesCell previewCell;
    private WallpaperUpdater updater;

    private FrameLayout colorPickerContainer;
    private ColorPicker colorPicker;
    private boolean pickerOpen;

    private PatternAdapter patternAdapter;
    private PaletteAdapter paletteAdapter;

    // Selection state
    private Object selectedPattern; // null (none), TLRPC.TL_wallPaper (built-in), or File (custom image)
    private int color1 = 0xFFFF9CE3, color2 = 0xFF7DD3FC, color3 = 0xFFB388FF;
    private boolean customColorActive;

    /** Non-null when this is "Изменить тему" rather than "Создать тему" - saving then replaces
     *  this same entry (same id) instead of adding a new one, see PrimeCustomWallpapers.save(). */
    private final PrimeCustomWallpapers.Entry editing;

    private static final int done_button = 1;

    public PrimeCustomThemeActivity(ArrayList<Object> patterns, PrimeCustomWallpapers.Entry editing) {
        super();
        this.builtinPatterns = patterns != null ? patterns : new ArrayList<>();
        this.editing = editing;
        if (editing != null) {
            color1 = editing.color1;
            color2 = editing.color2;
            color3 = editing.color3;
            if (editing.patternKind == PrimeCustomWallpapers.PATTERN_CUSTOM_IMAGE && editing.patternRef != null) {
                selectedPattern = new File(editing.patternRef);
            }
            // A PATTERN_BUILTIN patternRef (a slug) is resolved against builtinPatterns once the
            // view is up, in createView() below - that list isn't available yet at construction
            // time here.
        }
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(editing != null ? "Изменить тему" : "Своя тема");
        if (editing != null && editing.patternKind == PrimeCustomWallpapers.PATTERN_BUILTIN && editing.patternRef != null) {
            for (Object p : builtinPatterns) {
                if (p instanceof TLRPC.TL_wallPaper && editing.patternRef.equals(((TLRPC.TL_wallPaper) p).slug)) {
                    selectedPattern = p;
                    break;
                }
            }
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    promptNameAndSave();
                }
            }
        });
        actionBar.createMenu().addItem(done_button, R.drawable.ic_ab_done);

        updater = new WallpaperUpdater(getParentActivity(), this, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap, boolean gallery) {
                selectedPattern = file;
                patternAdapter.notifyDataSetChanged();
                updatePreview();
            }

            @Override
            public void needOpenColorPicker() {
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // --- middle row: left ribbon | preview | right ribbon, all edge to edge ---
        LinearLayout middleRow = new LinearLayout(context);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(middleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        RecyclerView patternList = new RecyclerView(context);
        patternList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        patternAdapter = new PatternAdapter();
        patternList.setAdapter(patternAdapter);
        patternList.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        middleRow.addView(patternList, LayoutHelper.createLinear(64, LayoutHelper.MATCH_PARENT));

        previewFrame = new FrameLayout(context);
        previewDrawable = new MotionBackgroundDrawable(color1, color2, color3, 0, 45, false);
        previewDrawable.setIndeterminateAnimation(true);
        previewDrawable.setParentView(previewFrame);
        previewFrame.setBackground(previewDrawable);
        // The standard Telegram dialog-preview cell, same one settings screens use for theme/
        // wallpaper previews elsewhere - real message bubbles, not a hand-drawn mockup.
        previewCell = new ThemePreviewMessagesCell(context, getParentLayout(), 0);
        previewCell.setOverrideBackground(null);
        previewFrame.addView(previewCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        middleRow.addView(previewFrame, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        RecyclerView paletteList = new RecyclerView(context);
        paletteList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        paletteAdapter = new PaletteAdapter();
        paletteList.setAdapter(paletteAdapter);
        paletteList.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        middleRow.addView(paletteList, LayoutHelper.createLinear(64, LayoutHelper.MATCH_PARENT));

        // --- bottom row: collapsible custom-color picker, hidden until "+ Свой цвет" ---
        colorPickerContainer = new FrameLayout(context);
        colorPickerContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        colorPickerContainer.setVisibility(View.GONE);
        colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {
            @Override
            public void setColor(int color, int num, boolean applyNow) {
                if (num == 0) {
                    color1 = color;
                } else if (num == 1) {
                    color2 = color;
                } else if (num == 2) {
                    color3 = color;
                }
                customColorActive = true;
                updatePreview();
                paletteAdapter.notifyDataSetChanged();
            }
        });
        colorPickerContainer.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        column.addView(colorPickerContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updatePreview();

        fragmentView = root;
        return fragmentView;
    }

    private void setPickerOpen(boolean open) {
        if (pickerOpen == open) {
            return;
        }
        pickerOpen = open;
        colorPicker.setType(0, false, 4, 3, false, 45, false);
        colorPicker.setColor(color1, 0);
        colorPicker.setColor(color2, 1);
        colorPicker.setColor(color3, 2);
        colorPickerContainer.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private void updatePreview() {
        previewDrawable.setColors(color1, color2, color3, 0);
        if (selectedPattern instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper p = (TLRPC.TL_wallPaper) selectedPattern;
            int intensity = p.settings != null ? p.settings.intensity : 50;
            // Best-effort: the pattern document streams in asynchronously elsewhere in the app
            // the same as any wallpaper thumbnail; this shows it immediately if already cached
            // locally, and just the gradient otherwise (matches how WallpaperCell itself behaves).
            File path = FileLoader.getInstance(currentAccount).getPathToAttach(p.document, true);
            if (path != null && path.exists()) {
                Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path.getAbsolutePath());
                if (bmp != null) {
                    previewDrawable.setPatternBitmap(intensity, bmp);
                }
            }
        } else if (selectedPattern instanceof File) {
            Bitmap bmp = android.graphics.BitmapFactory.decodeFile(((File) selectedPattern).getAbsolutePath());
            if (bmp != null) {
                previewDrawable.setPatternBitmap(50, bmp);
            }
        } else {
            previewDrawable.setPatternBitmap(0, null);
        }
        previewFrame.invalidate();
    }

    private void promptNameAndSave() {
        if (getParentActivity() == null) {
            return;
        }
        EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Моя тема");
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        input.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(6));
        if (editing != null && editing.name != null) {
            input.setText(editing.name);
            input.setSelection(input.getText().length());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Название темы");
        builder.setView(input);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String name = input.getText() != null ? input.getText().toString().trim() : "";
            saveEntry(name.isEmpty() ? "Моя тема" : name);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void saveEntry(String name) {
        PrimeCustomWallpapers.Entry entry = new PrimeCustomWallpapers.Entry();
        // Carrying the id forward is what makes PrimeCustomWallpapers.save() replace this entry
        // in place instead of adding a second one - see its own doc.
        entry.id = editing != null ? editing.id : null;
        entry.name = name;
        entry.color1 = color1;
        entry.color2 = color2;
        entry.color3 = color3;
        entry.rotation = 45;
        entry.intensity = 1.0f;
        if (selectedPattern instanceof TLRPC.TL_wallPaper) {
            entry.patternKind = PrimeCustomWallpapers.PATTERN_BUILTIN;
            entry.patternRef = ((TLRPC.TL_wallPaper) selectedPattern).slug;
        } else if (selectedPattern instanceof File) {
            entry.patternKind = PrimeCustomWallpapers.PATTERN_CUSTOM_IMAGE;
            entry.patternRef = ((File) selectedPattern).getAbsolutePath();
        } else {
            entry.patternKind = PrimeCustomWallpapers.PATTERN_NONE;
        }
        PrimeCustomWallpapers.save(entry);
        finishFragment();
    }

    // ─── Pattern ribbon (left edge) ─────────────────────────────────────────

    private class PatternAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_NONE = 0, TYPE_PATTERN = 1, TYPE_ADD = 2;

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_NONE;
            if (position == 1 + builtinPatterns.size()) return TYPE_ADD;
            return TYPE_PATTERN;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            FrameLayout tile = new FrameLayout(context);
            tile.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            if (viewType == TYPE_PATTERN) {
                BackupImageView imageView = new BackupImageView(context);
                imageView.setRoundRadius(AndroidUtilities.dp(11));
                tile.addView(imageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
                tile.setTag(imageView);
            } else {
                View swatch = new View(context) {
                    final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    @Override
                    protected void onDraw(Canvas canvas) {
                        super.onDraw(canvas);
                        if (viewType == TYPE_NONE) {
                            linePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
                            linePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                            float pad = AndroidUtilities.dp(12);
                            canvas.drawLine(pad, pad, getWidth() - pad, getHeight() - pad, linePaint);
                            canvas.drawLine(getWidth() - pad, pad, pad, getHeight() - pad, linePaint);
                        }
                    }
                };
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                bg.setCornerRadius(AndroidUtilities.dp(11));
                if (viewType == TYPE_ADD) {
                    bg.setStroke(AndroidUtilities.dp(1.5f), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                } else {
                    bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                }
                swatch.setBackground(bg);
                tile.addView(swatch, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
                if (viewType == TYPE_ADD) {
                    TextView plus = new TextView(context);
                    plus.setText("+");
                    plus.setTextSize(20);
                    plus.setGravity(Gravity.CENTER);
                    plus.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    tile.addView(plus, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
                }
            }
            return new RecyclerView.ViewHolder(tile) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            FrameLayout tile = (FrameLayout) holder.itemView;
            int type = getItemViewType(position);
            if (type == TYPE_NONE) {
                tile.setAlpha(selectedPattern == null ? 1f : 0.6f);
                tile.setOnClickListener(v -> {
                    selectedPattern = null;
                    patternAdapter.notifyDataSetChanged();
                    updatePreview();
                });
            } else if (type == TYPE_ADD) {
                tile.setAlpha(selectedPattern instanceof File ? 1f : 0.6f);
                tile.setOnClickListener(v -> updater.openGallery());
            } else {
                TLRPC.TL_wallPaper pattern = (TLRPC.TL_wallPaper) builtinPatterns.get(position - 1);
                BackupImageView imageView = (BackupImageView) tile.getTag();
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(pattern.document.thumbs, 100);
                long size = thumb != null ? thumb.size : pattern.document.size;
                imageView.setImage(ImageLocation.getForDocument(thumb, pattern.document), "44_44", null, null, "jpg", size, 1, pattern);
                tile.setAlpha(pattern.equals(selectedPattern) ? 1f : 0.6f);
                tile.setOnClickListener(v -> {
                    selectedPattern = pattern;
                    patternAdapter.notifyDataSetChanged();
                    updatePreview();
                });
            }
        }

        @Override
        public int getItemCount() {
            return 2 + builtinPatterns.size();
        }
    }

    // ─── Palette ribbon (right edge) ─────────────────────────────────────────

    private class PaletteSwatchView extends View {
        private final GradientTools gradientTools = new GradientTools();
        private final RectF rect = new RectF();
        private boolean isAddTile;

        PaletteSwatchView(Context context) {
            super(context);
        }

        void setPalette(PrimeGradientPalettes.Palette p) {
            isAddTile = false;
            if (p.color3 != 0) {
                gradientTools.setColors(p.color1, p.color2, p.color3);
            } else {
                gradientTools.setColors(p.color1, p.color2);
            }
            invalidate();
        }

        void setAddTile() {
            isAddTile = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            rect.set(0, 0, getWidth(), getHeight());
            if (isAddTile) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(11), AndroidUtilities.dp(11), p);
                Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                linePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
                linePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                float cx = getWidth() / 2f, cy = getHeight() / 2f, arm = AndroidUtilities.dp(8);
                canvas.drawLine(cx - arm, cy, cx + arm, cy, linePaint);
                canvas.drawLine(cx, cy - arm, cx, cy + arm, linePaint);
                return;
            }
            gradientTools.setBounds(rect);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(11), AndroidUtilities.dp(11), gradientTools.paint);
        }
    }

    private class PaletteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PrimeGradientPalettes.Palette> palettes = PrimeGradientPalettes.all();

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            FrameLayout tile = new FrameLayout(context);
            tile.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            PaletteSwatchView swatch = new PaletteSwatchView(context);
            tile.addView(swatch, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
            tile.setTag(swatch);
            return new RecyclerView.ViewHolder(tile) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            FrameLayout tile = (FrameLayout) holder.itemView;
            PaletteSwatchView swatch = (PaletteSwatchView) tile.getTag();
            if (position == palettes.size()) {
                swatch.setAddTile();
                tile.setAlpha(customColorActive ? 1f : 0.6f);
                tile.setOnClickListener(v -> setPickerOpen(true));
            } else {
                PrimeGradientPalettes.Palette p = palettes.get(position);
                swatch.setPalette(p);
                boolean selected = !customColorActive && color1 == p.color1 && color2 == p.color2 && color3 == p.color3;
                tile.setAlpha(selected ? 1f : 0.6f);
                tile.setOnClickListener(v -> {
                    color1 = p.color1;
                    color2 = p.color2;
                    color3 = p.color3;
                    customColorActive = false;
                    setPickerOpen(false);
                    updatePreview();
                    paletteAdapter.notifyDataSetChanged();
                });
            }
        }

        @Override
        public int getItemCount() {
            return palettes.size() + 1;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }
}
