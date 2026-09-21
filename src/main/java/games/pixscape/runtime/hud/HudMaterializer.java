package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.Styles;
import com.github.tommyettinger.textra.TypingLabel;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudHorizontalAlign;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudVerticalAlign;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts a validated V1 construction document into one detached native Scene2D actor tree. */
public final class HudMaterializer {
    /** Converts a validated HUD document using borrowed visual resources. */
    public MaterializedHud materialize(
            ValidatedHudDocument validatedDocument, HudVisualResources resources) {
        return materialize(validatedDocument, resources, true);
    }

    /** Studio authoring can omit hover listeners while keeping native preview/runtime behavior. */
    public MaterializedHud materialize(
            ValidatedHudDocument validatedDocument, HudVisualResources resources,
            boolean attachTooltips) {
        if (validatedDocument == null) {
            throw new IllegalArgumentException("ValidatedHudDocument is required.");
        }
        if (resources == null) throw new IllegalArgumentException("HudVisualResources is required.");

        Map<String, Actor> actorById = new LinkedHashMap<String, Actor>();
        List<Disposable> ownedResources = new ArrayList<Disposable>();
        Map<Actor, TextTooltip> tooltips = new LinkedHashMap<Actor, TextTooltip>();
        TooltipManager tooltipManager = attachTooltips ? new TooltipManager() : null;
        try {
            Actor root = materializeNode(validatedDocument.document().root, resources,
                    actorById, ownedResources, tooltipManager, tooltips);
            if (actorById.size() != validatedDocument.nodeIndex().size()) {
                throw new IllegalStateException(
                        "Validated HUD document changed after validation; validate it again.");
            }
            return new MaterializedHud(root, actorById, ownedResources, tooltipManager, tooltips);
        } catch (RuntimeException failure) {
            MaterializedHud.releaseTooltips(tooltipManager, tooltips);
            disposeOwned(ownedResources);
            throw failure;
        }
    }

    private Actor materializeNode(
            HudNode node, HudVisualResources resources, Map<String, Actor> actorById,
            List<Disposable> ownedResources, TooltipManager tooltipManager,
            Map<Actor, TextTooltip> tooltips) {
        Actor actor = createActor(node, resources, ownedResources, tooltipManager != null);
        actor.setName(node.id);
        applyAuthoredSize(actor, node.actor.width, node.actor.height);
        if (actorById.put(node.id, actor) != null) {
            throw new IllegalStateException(
                    "Validated HUD document contains duplicate node ID '" + node.id + "'.");
        }
        if (tooltipManager != null && node.tooltip != null) {
            TextTooltip.TextTooltipStyle shared =
                    HudBuiltInTextTooltipStyle.isSelected(node.tooltip.styleName)
                            ? resources.builtInTextTooltipStyle()
                            : resources.textTooltipStyle(node.tooltip.styleName);
            if (shared == null) {
                throw missing(node, "TextTooltip style",
                        HudBuiltInTextTooltipStyle.isSelected(node.tooltip.styleName)
                                ? "built-in Default" : node.tooltip.styleName);
            }
            TextTooltip.TextTooltipStyle style = shared;
            if (node.tooltip.fontAssetId != null) {
                style = new TextTooltip.TextTooltipStyle(shared);
                style.label.font = requireFont(node, node.tooltip.fontAssetId, resources);
            }
            TextTooltip tooltip = new TextTooltip(node.tooltip.text, tooltipManager, style);
            // Native layout groups default to childrenOnly. Make only this authored tooltip
            // surface hittable; Group.hit still gives visible children first refusal.
            if (actor.getTouchable() == Touchable.childrenOnly) {
                actor.setTouchable(Touchable.enabled);
            }
            actor.addListener(tooltip);
            tooltips.put(actor, tooltip);
        }

        for (int i = 0; i < node.children.size(); i++) {
            HudChild child = node.children.get(i);
            Actor childActor = materializeNode(child.node, resources, actorById, ownedResources,
                    tooltipManager, tooltips);
            switch (child.placementKind) {
                case DIRECT:
                    addDirect(actor, childActor, node.id);
                    break;
                case CELL:
                    addCell((Table) actor, childActor, child.cell);
                    break;
                case FREE:
                    ((HudFreeGroup) actor).addFreeActor(childActor, child.free);
                    break;
                default:
                    throw new IllegalStateException("Unsupported validated HUD placement kind: "
                            + child.placementKind + ".");
            }
        }
        return actor;
    }

    private Actor createActor(HudNode node, HudVisualResources resources,
                              List<Disposable> ownedResources, boolean interactivePreview) {
        switch (node.kind) {
            case GROUP:
                return new HudFreeGroup(node.actor.width, node.actor.height);
            case TABLE:
                return new Table();
            case STACK:
                return new Stack();
            case CONTAINER:
                Container<Actor> container = new Container<Actor>();
                container.setClip(node.container.clip);
                return container;
            case SCROLL_PANE: {
                ScrollPane.ScrollPaneStyle style = HudBuiltInScrollPaneStyle.isSelected(node.scrollPane.styleName)
                        ? resources.builtInScrollPaneStyle() : resources.scrollPaneStyle(node.scrollPane.styleName);
                if (style == null) throw missing(node, "ScrollPane style",
                        HudBuiltInScrollPaneStyle.isSelected(node.scrollPane.styleName)
                                ? "built-in Default" : node.scrollPane.styleName);
                ScrollPane pane = new ScrollPane(null, style);
                pane.setScrollingDisabled(node.scrollPane.scrollingDisabledX, node.scrollPane.scrollingDisabledY);
                pane.setFadeScrollBars(node.scrollPane.fadeScrollBars);
                pane.setFlickScroll(node.scrollPane.flickScroll);
                pane.setSmoothScrolling(node.scrollPane.smoothScrolling);
                pane.setOverscroll(node.scrollPane.overscrollX, node.scrollPane.overscrollY);
                return pane;
            }
            case WINDOW: {
                Window.WindowStyle sharedStyle = HudBuiltInWindowStyle.isSelected(node.window.styleName)
                        ? resources.builtInWindowStyle() : resources.windowStyle(node.window.styleName);
                if (!HudStyleUsability.isUsableWindowStyle(sharedStyle,
                        node.window.fontAssetId != null)) {
                    throw missing(node, "Window style",
                            HudBuiltInWindowStyle.isSelected(node.window.styleName)
                                    ? "built-in Default" : node.window.styleName);
                }
                Window.WindowStyle style = sharedStyle;
                if (node.window.fontAssetId != null) {
                    style = new Window.WindowStyle(sharedStyle);
                    style.titleFont = requireFont(node, node.window.fontAssetId, resources);
                }
                Window window = new Window(node.window.title, style);
                window.setMovable(interactivePreview && node.window.movable);
                window.setResizable(interactivePreview && node.window.resizable);
                window.setModal(interactivePreview && node.window.modal);
                window.setKeepWithinStage(interactivePreview && node.window.keepWithinStage);
                return window;
            }
            case IMAGE: {
                if (node.image.source == HudImageSource.REGION) {
                    TextureRegion region = resources.region(node.image.resourceName);
                    if (region == null) {
                        throw missing(node, "atlas region", node.image.resourceName);
                    }
                    return new Image(region);
                }
                Drawable drawable = resources.drawable(node.image.resourceName);
                if (drawable == null) {
                    throw missing(node, "Skin drawable", node.image.resourceName);
                }
                return new Image(drawable);
            }
            case LABEL: {
                Label.LabelStyle sharedStyle = HudBuiltInLabelStyle.isSelected(node.label.styleName)
                        ? resources.builtInLabelStyle()
                        : resources.labelStyle(node.label.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "Label style",
                            HudBuiltInLabelStyle.isSelected(node.label.styleName)
                                    ? "built-in Default" : node.label.styleName);
                }
                Label.LabelStyle labelStyle = sharedStyle;
                if (node.label.fontAssetId != null) {
                    labelStyle = new Label.LabelStyle(sharedStyle);
                    labelStyle.font = requireFont(node, node.label.fontAssetId, resources);
                }
                return new Label(node.label.text, labelStyle);
            }
            case TEXTRA_LABEL: {
                Label.LabelStyle sharedStyle = HudBuiltInLabelStyle.isSelected(
                        node.textraLabel.styleName)
                        ? resources.builtInLabelStyle()
                        : resources.labelStyle(node.textraLabel.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "Label style",
                            HudBuiltInLabelStyle.isSelected(node.textraLabel.styleName)
                                    ? "built-in Default" : node.textraLabel.styleName);
                }
                BitmapFont bitmapFont = node.textraLabel.fontAssetId == null
                        ? sharedStyle.font
                        : requireFont(node, node.textraLabel.fontAssetId, resources);
                Font prepared = resources.textraFont(bitmapFont);
                if (prepared == null) {
                    throw missing(node, "prepared TextraTypist font",
                            node.textraLabel.fontAssetId == null
                                    ? "selected Label style" : String.valueOf(node.textraLabel.fontAssetId));
                }
                Font actorFont = new Font(prepared);
                ownedResources.add(actorFont);
                Styles.LabelStyle style = new Styles.LabelStyle(actorFont,
                        sharedStyle.fontColor == null ? null
                                : new com.badlogic.gdx.graphics.Color(sharedStyle.fontColor),
                        sharedStyle.background);
                TypingLabel label = new TypingLabel(node.textraLabel.text, style);
                if (!node.textraLabel.typingEnabled) label.skipToTheEnd(true, false);
                return label;
            }
            case TEXT_BUTTON: {
                TextButton.TextButtonStyle sharedStyle =
                        HudBuiltInTextButtonStyle.isSelected(node.textButton.styleName)
                                ? resources.builtInTextButtonStyle()
                                : resources.textButtonStyle(node.textButton.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "TextButton style",
                            HudBuiltInTextButtonStyle.isSelected(node.textButton.styleName)
                                    ? "built-in Default" : node.textButton.styleName);
                }
                TextButton.TextButtonStyle style = sharedStyle;
                if (node.textButton.fontAssetId != null) {
                    style = new TextButton.TextButtonStyle(sharedStyle);
                    style.font = requireFont(node, node.textButton.fontAssetId, resources);
                }
                return new TextButton(node.textButton.text, style);
            }
            case IMAGE_BUTTON: {
                ImageButton.ImageButtonStyle sharedStyle =
                        HudBuiltInImageButtonStyle.isSelected(node.imageButton.styleName)
                                ? resources.builtInImageButtonStyle()
                                : resources.imageButtonStyle(node.imageButton.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "ImageButton style",
                            HudBuiltInImageButtonStyle.isSelected(node.imageButton.styleName)
                                    ? "built-in Default" : node.imageButton.styleName);
                }
                ImageButton.ImageButtonStyle style = hasImageButtonOverrides(node)
                        ? new ImageButton.ImageButtonStyle(sharedStyle) : sharedStyle;
                if (style != sharedStyle) applyImageButtonOverrides(node, resources, style);
                return new ImageButton(style);
            }
            case IMAGE_TEXT_BUTTON: {
                ImageTextButton.ImageTextButtonStyle sharedStyle =
                        HudBuiltInImageTextButtonStyle.isSelected(node.imageTextButton.styleName)
                                ? resources.builtInImageTextButtonStyle()
                                : resources.imageTextButtonStyle(node.imageTextButton.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "ImageTextButton style",
                            HudBuiltInImageTextButtonStyle.isSelected(node.imageTextButton.styleName)
                                    ? "built-in Default" : node.imageTextButton.styleName);
                }
                ImageTextButton.ImageTextButtonStyle style = hasImageTextButtonOverrides(node)
                        ? new ImageTextButton.ImageTextButtonStyle(sharedStyle) : sharedStyle;
                if (style != sharedStyle) applyImageTextButtonOverrides(node, resources, style);
                if (node.imageTextButton.fontAssetId != null) {
                    if (style == sharedStyle) style = new ImageTextButton.ImageTextButtonStyle(sharedStyle);
                    style.font = requireFont(node, node.imageTextButton.fontAssetId, resources);
                }
                return new ImageTextButton(node.imageTextButton.text, style);
            }
            case TEXT_FIELD: {
                TextField.TextFieldStyle sharedStyle =
                        HudBuiltInTextFieldStyle.isSelected(node.textField.styleName)
                                ? resources.builtInTextFieldStyle()
                                : resources.textFieldStyle(node.textField.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "TextField style",
                            HudBuiltInTextFieldStyle.isSelected(node.textField.styleName)
                                    ? "built-in Default" : node.textField.styleName);
                }
                TextField.TextFieldStyle style = sharedStyle;
                if (node.textField.fontAssetId != null) {
                    BitmapFont font = requireFont(node, node.textField.fontAssetId, resources);
                    style = new TextField.TextFieldStyle(sharedStyle);
                    style.font = font;
                    style.messageFont = font;
                }
                TextField field = new TextField("", style);
                field.setMaxLength(node.textField.maxLength);
                field.setText(node.textField.text);
                field.setMessageText(node.textField.messageText);
                field.setPasswordMode(node.textField.passwordMode);
                return field;
            }
            case SELECT_BOX: {
                SelectBox.SelectBoxStyle sharedStyle =
                        HudBuiltInSelectBoxStyle.isSelected(node.selectBox.styleName)
                                ? resources.builtInSelectBoxStyle()
                                : resources.selectBoxStyle(node.selectBox.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "SelectBox style",
                            HudBuiltInSelectBoxStyle.isSelected(node.selectBox.styleName)
                                    ? "built-in Default" : node.selectBox.styleName);
                }
                SelectBox.SelectBoxStyle style = sharedStyle;
                if (node.selectBox.fontAssetId != null) {
                    BitmapFont font = requireFont(node, node.selectBox.fontAssetId, resources);
                    style = new SelectBox.SelectBoxStyle(sharedStyle);
                    style.font = font;
                    style.listStyle = new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(
                            sharedStyle.listStyle);
                    style.listStyle.font = font;
                }
                SelectBox<String> box = new SelectBox<String>(style);
                box.setItems(node.selectBox.items.toArray(new String[node.selectBox.items.size()]));
                box.setMaxListCount(node.selectBox.maxListCount);
                box.setDisabled(node.selectBox.disabled);
                if (node.selectBox.selectedIndex >= 0) box.setSelectedIndex(node.selectBox.selectedIndex);
                return box;
            }
            case CHECK_BOX: {
                CheckBox.CheckBoxStyle sharedStyle = HudBuiltInCheckBoxStyle.isSelected(node.checkBox.styleName)
                        ? resources.builtInCheckBoxStyle() : resources.checkBoxStyle(node.checkBox.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "CheckBox style", HudBuiltInCheckBoxStyle.isSelected(node.checkBox.styleName)
                            ? "built-in Default" : node.checkBox.styleName);
                }
                CheckBox.CheckBoxStyle style = sharedStyle;
                if (node.checkBox.fontAssetId != null) {
                    style = new CheckBox.CheckBoxStyle(sharedStyle);
                    style.font = requireFont(node, node.checkBox.fontAssetId, resources);
                }
                CheckBox box = new CheckBox(node.checkBox.text, style);
                box.setChecked(node.checkBox.checked);
                box.setDisabled(node.checkBox.disabled);
                return box;
            }
            case SLIDER: {
                Slider.SliderStyle style = HudBuiltInSliderStyle.isSelected(node.slider.styleName)
                        ? resources.builtInSliderStyle()
                        : resources.sliderStyle(node.slider.styleName);
                if (style == null) {
                    throw missing(node, "Slider style",
                            HudBuiltInSliderStyle.isSelected(node.slider.styleName)
                                    ? "built-in Default" : node.slider.styleName);
                }
                Slider slider = new Slider(node.slider.min, node.slider.max,
                        node.slider.stepSize,
                        node.slider.orientation == HudSliderOrientation.VERTICAL, style);
                slider.setValue(node.slider.value);
                slider.setDisabled(node.slider.disabled);
                return slider;
            }
            case PROGRESS_BAR: {
                ProgressBar.ProgressBarStyle style = HudBuiltInProgressBarStyle.isSelected(node.progressBar.styleName)
                        ? resources.builtInProgressBarStyle()
                        : resources.progressBarStyle(node.progressBar.styleName);
                if (style == null) {
                    throw missing(node, "ProgressBar style",
                            HudBuiltInProgressBarStyle.isSelected(node.progressBar.styleName)
                                    ? "built-in Default" : node.progressBar.styleName);
                }
                ProgressBar progressBar = new ProgressBar(node.progressBar.min, node.progressBar.max,
                        node.progressBar.stepSize,
                        node.progressBar.orientation == HudSliderOrientation.VERTICAL, style);
                progressBar.setValue(node.progressBar.value);
                progressBar.setDisabled(node.progressBar.disabled);
                return progressBar;
            }
            default:
                throw new IllegalStateException(
                        "Unsupported validated HUD node kind: " + node.kind + ".");
        }
    }

    private static BitmapFont requireFont(HudNode node, int assetId,
                                          HudVisualResources resources) {
        BitmapFont font = resources.bitmapFont(assetId);
        if (font == null) {
            throw missing(node, "bitmap font Asset", String.valueOf(assetId));
        }
        return font;
    }

    private static void addDirect(Actor parent, Actor child, String parentId) {
        if (parent instanceof Container) {
            @SuppressWarnings("unchecked")
            Container<Actor> container = (Container<Actor>) parent;
            container.setActor(child);
        } else if (parent instanceof ScrollPane) {
            ((ScrollPane) parent).setActor(child);
        } else if (parent instanceof Group) {
            ((Group) parent).addActor(child);
        } else {
            throw new IllegalStateException("Validated HUD node '" + parentId
                    + "' cannot accept DIRECT placement.");
        }
    }

    private static void addCell(Table table, Actor actor, HudCellConstraints constraints) {
        Cell<Actor> cell = table.add(actor);
        if (constraints.minWidth != null) cell.minWidth(constraints.minWidth);
        if (constraints.minHeight != null) cell.minHeight(constraints.minHeight);
        if (constraints.prefWidth != null) cell.prefWidth(constraints.prefWidth);
        if (constraints.prefHeight != null) cell.prefHeight(constraints.prefHeight);
        cell.pad(constraints.padTop, constraints.padLeft,
                constraints.padBottom, constraints.padRight);
        cell.fill(constraints.fillX, constraints.fillY);
        cell.expand(constraints.expandX, constraints.expandY);
        cell.align(cellAlign(constraints.horizontalAlign, constraints.verticalAlign));
        if (constraints.rowAfter) table.row();
    }

    private static int cellAlign(
            HudHorizontalAlign horizontal, HudVerticalAlign vertical) {
        int align = 0;
        if (horizontal == HudHorizontalAlign.LEFT) align |= Align.left;
        else if (horizontal == HudHorizontalAlign.RIGHT) align |= Align.right;
        if (vertical == HudVerticalAlign.BOTTOM) align |= Align.bottom;
        else if (vertical == HudVerticalAlign.TOP) align |= Align.top;
        return align == 0 ? Align.center : align;
    }

    private static void applyAuthoredSize(Actor actor, float width, float height) {
        if (width > 0f) actor.setWidth(width);
        if (height > 0f) actor.setHeight(height);
    }

    private static void applyImageButtonOverrides(HudNode node, HudVisualResources resources,
                                                  ImageButton.ImageButtonStyle style) {
        if (node.imageButton.imageUp != null) {
            style.imageUp = resolveImage(node, resources, node.imageButton.imageUp, "imageUp");
        }
        if (node.imageButton.imageDown != null) {
            style.imageDown = resolveImage(node, resources, node.imageButton.imageDown, "imageDown");
        }
        if (node.imageButton.imageOver != null) {
            style.imageOver = resolveImage(node, resources, node.imageButton.imageOver, "imageOver");
        }
        if (node.imageButton.imageDisabled != null) {
            style.imageDisabled = resolveImage(node, resources, node.imageButton.imageDisabled, "imageDisabled");
        }
        if (node.imageButton.imageChecked != null) {
            style.imageChecked = resolveImage(node, resources, node.imageButton.imageChecked, "imageChecked");
        }
        if (node.imageButton.imageCheckedDown != null) {
            style.imageCheckedDown = resolveImage(node, resources, node.imageButton.imageCheckedDown,
                    "imageCheckedDown");
        }
        if (node.imageButton.imageCheckedOver != null) {
            style.imageCheckedOver = resolveImage(node, resources, node.imageButton.imageCheckedOver,
                    "imageCheckedOver");
        }
    }

    private static boolean hasImageButtonOverrides(HudNode node) {
        return node.imageButton.imageUp != null || node.imageButton.imageDown != null
                || node.imageButton.imageOver != null || node.imageButton.imageDisabled != null
                || node.imageButton.imageChecked != null || node.imageButton.imageCheckedDown != null
                || node.imageButton.imageCheckedOver != null;
    }

    private static void applyImageTextButtonOverrides(HudNode node, HudVisualResources resources,
                                                      ImageTextButton.ImageTextButtonStyle style) {
        if (node.imageTextButton.imageUp != null) style.imageUp = resolveImage(node, resources, node.imageTextButton.imageUp, "imageUp");
        if (node.imageTextButton.imageDown != null) style.imageDown = resolveImage(node, resources, node.imageTextButton.imageDown, "imageDown");
        if (node.imageTextButton.imageOver != null) style.imageOver = resolveImage(node, resources, node.imageTextButton.imageOver, "imageOver");
        if (node.imageTextButton.imageDisabled != null) style.imageDisabled = resolveImage(node, resources, node.imageTextButton.imageDisabled, "imageDisabled");
        if (node.imageTextButton.imageChecked != null) style.imageChecked = resolveImage(node, resources, node.imageTextButton.imageChecked, "imageChecked");
        if (node.imageTextButton.imageCheckedDown != null) style.imageCheckedDown = resolveImage(node, resources, node.imageTextButton.imageCheckedDown, "imageCheckedDown");
        if (node.imageTextButton.imageCheckedOver != null) style.imageCheckedOver = resolveImage(node, resources, node.imageTextButton.imageCheckedOver, "imageCheckedOver");
    }

    private static boolean hasImageTextButtonOverrides(HudNode node) {
        return node.imageTextButton.imageUp != null || node.imageTextButton.imageDown != null
                || node.imageTextButton.imageOver != null || node.imageTextButton.imageDisabled != null
                || node.imageTextButton.imageChecked != null || node.imageTextButton.imageCheckedDown != null
                || node.imageTextButton.imageCheckedOver != null;
    }

    private static Drawable resolveImage(HudNode node, HudVisualResources resources,
                                         games.pixscape.runtime.hud.document.HudImageData image,
                                         String field) {
        if (image.source == HudImageSource.REGION) {
            TextureRegion region = resources.region(image.resourceName);
            if (region != null) return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(region);
            throw missing(node, "atlas region for " + field, image.resourceName);
        }
        Drawable drawable = resources.drawable(image.resourceName);
        if (drawable != null) return drawable;
        throw missing(node, "Skin drawable for " + field, image.resourceName);
    }

    private static IllegalStateException missing(
            HudNode node, String resourceKind, String resourceName) {
        return new IllegalStateException("Validated HUD node '" + node.id + "' requires "
                + resourceKind + " '" + resourceName + "' in the provided HUD visual resources.");
    }

    private static void disposeOwned(List<Disposable> resources) {
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).dispose();
            } catch (RuntimeException ignored) {
                // Preserve the materialization failure while cleaning candidate resources.
            }
        }
    }
}
