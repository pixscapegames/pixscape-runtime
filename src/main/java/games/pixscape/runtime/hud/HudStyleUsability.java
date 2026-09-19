package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;

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

    public static boolean isUsableCheckBoxStyle(CheckBox.CheckBoxStyle style,
                                                boolean hasFontOverride) {
        return style != null && (hasFontOverride || style.font != null)
                && style.checkboxOn != null && style.checkboxOff != null;
    }
}
