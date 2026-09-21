package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.github.tommyettinger.textra.Font;

/**
 * Borrowed visual resources needed to materialize a HUD document.
 *
 * <p>Providers retain ownership of the returned objects and remain responsible for their
 * lifetime. A missing named resource is reported by returning {@code null}.</p>
 */
public interface HudVisualResources {
    TextureRegion region(String name);

    Drawable drawable(String name);

    Label.LabelStyle labelStyle(String name);

    /** Borrowed standalone bitmap font resolved by its project Asset ID. */
    default BitmapFont bitmapFont(int assetId) { return null; }

    /** Borrowed prepared TextraTypist base Font for this effective BitmapFont. */
    default Font textraFont(BitmapFont bitmapFont) { return null; }

    /** Skin-independent style used when a Label has no authored custom style reference. */
    default Label.LabelStyle builtInLabelStyle() { return null; }

    TextButton.TextButtonStyle textButtonStyle(String name);

    /** Skin-independent style used when a TextButton has no authored custom style reference. */
    default TextButton.TextButtonStyle builtInTextButtonStyle() { return null; }

    default ImageButton.ImageButtonStyle imageButtonStyle(String name) { return null; }

    /** Skin-independent style used when an ImageButton has no authored custom style reference. */
    default ImageButton.ImageButtonStyle builtInImageButtonStyle() { return null; }

    default ImageTextButton.ImageTextButtonStyle imageTextButtonStyle(String name) { return null; }

    /** Skin-independent style used when an ImageTextButton has no authored custom style reference. */
    default ImageTextButton.ImageTextButtonStyle builtInImageTextButtonStyle() { return null; }

    default TextField.TextFieldStyle textFieldStyle(String name) { return null; }

    /** Skin-independent style used when a TextField has no authored custom style reference. */
    default TextField.TextFieldStyle builtInTextFieldStyle() { return null; }

    default SelectBox.SelectBoxStyle selectBoxStyle(String name) { return null; }

    /** Skin-independent style used when a SelectBox has no authored custom style reference. */
    default SelectBox.SelectBoxStyle builtInSelectBoxStyle() { return null; }

    default CheckBox.CheckBoxStyle checkBoxStyle(String name) { return null; }

    /** Skin-independent style used when a CheckBox has no authored custom style reference. */
    default CheckBox.CheckBoxStyle builtInCheckBoxStyle() { return null; }

    default Slider.SliderStyle sliderStyle(String name) { return null; }

    /** Skin-independent style used when a Slider has no authored custom style reference. */
    default Slider.SliderStyle builtInSliderStyle() { return null; }

    default ProgressBar.ProgressBarStyle progressBarStyle(String name) { return null; }

    /** Skin-independent style used when a ProgressBar has no authored custom style reference. */
    default ProgressBar.ProgressBarStyle builtInProgressBarStyle() { return null; }

    default ScrollPane.ScrollPaneStyle scrollPaneStyle(String name) { return null; }

    default ScrollPane.ScrollPaneStyle builtInScrollPaneStyle() { return null; }

    default TextTooltip.TextTooltipStyle textTooltipStyle(String name) { return null; }

    default TextTooltip.TextTooltipStyle builtInTextTooltipStyle() { return null; }

    default Window.WindowStyle windowStyle(String name) { return null; }

    default Window.WindowStyle builtInWindowStyle() { return null; }
}
