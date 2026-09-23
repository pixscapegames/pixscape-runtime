package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

/** Native Scene2D style requirements shared by HUD Runtime and Studio authoring. */
public final class HudStyleUsability {
    private HudStyleUsability() {
    }

    public static boolean isUsableLabelStyle(Label.LabelStyle style,
                                             boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null);
    }

    public static boolean isUsableTextButtonStyle(TextButton.TextButtonStyle style,
                                                  boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null);
    }

    public static boolean isUsableImageTextButtonStyle(ImageTextButton.ImageTextButtonStyle style,
                                                       boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null);
    }

    public static boolean isUsableTextFieldStyle(TextField.TextFieldStyle style,
                                                 boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null)
                && style.fontColor != null;
    }

    public static boolean isUsableSelectBoxStyle(SelectBox.SelectBoxStyle style,
                                                  boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null)
                && style.fontColor != null
                && style.listStyle != null
                && (hasFontOverride || style.listStyle.font != null)
                && style.listStyle.selection != null
                && style.scrollStyle != null;
    }

    public static boolean isUsableListStyle(com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle style,
                                            boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null)
                && style.selection != null && style.fontColorSelected != null
                && style.fontColorUnselected != null;
    }

    public static boolean isUsableCheckBoxStyle(CheckBox.CheckBoxStyle style,
                                                boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null)
                && style.checkboxOn != null && style.checkboxOff != null;
    }

    public static boolean isUsableSliderStyle(Slider.SliderStyle style) {
        return style != null && style.background != null;
    }

    /** Scene2D accepts every non-null ProgressBarStyle; all drawables are optional. */
    public static boolean isUsableProgressBarStyle(ProgressBar.ProgressBarStyle style) {
        return style != null;
    }

    /** Scene2D accepts every non-null ScrollPaneStyle; its drawables are optional. */
    public static boolean isUsableScrollPaneStyle(ScrollPane.ScrollPaneStyle style) {
        return style != null;
    }

    public static boolean isUsableTextTooltipStyle(TextTooltip.TextTooltipStyle style,
                                                   boolean hasFontOverride) {
        return style != null && style.label != null
                && (hasFontOverride || style.label.font != null);
    }

    /** Window requires a title font; background, color, and stage background are optional. */
    public static boolean isUsableWindowStyle(Window.WindowStyle style,
                                              boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.titleFont != null);
    }
}
