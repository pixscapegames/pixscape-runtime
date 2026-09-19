package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
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
        if (validatedDocument == null) {
            throw new IllegalArgumentException("ValidatedHudDocument is required.");
        }
        if (resources == null) throw new IllegalArgumentException("HudVisualResources is required.");

        Map<String, Actor> actorById = new LinkedHashMap<String, Actor>();
        List<Disposable> ownedResources = new ArrayList<Disposable>();
        try {
            Actor root = materializeNode(validatedDocument.document().root, resources,
                    actorById, ownedResources);
            if (actorById.size() != validatedDocument.nodeIndex().size()) {
                throw new IllegalStateException(
                        "Validated HUD document changed after validation; validate it again.");
            }
            return new MaterializedHud(root, actorById, ownedResources);
        } catch (RuntimeException failure) {
            disposeOwned(ownedResources);
            throw failure;
        }
    }

    private Actor materializeNode(
            HudNode node, HudVisualResources resources, Map<String, Actor> actorById,
            List<Disposable> ownedResources) {
        Actor actor = createActor(node, resources, ownedResources);
        actor.setName(node.id);
        applyAuthoredSize(actor, node.actor.width, node.actor.height);
        if (actorById.put(node.id, actor) != null) {
            throw new IllegalStateException(
                    "Validated HUD document contains duplicate node ID '" + node.id + "'.");
        }

        for (int i = 0; i < node.children.size(); i++) {
            HudChild child = node.children.get(i);
            Actor childActor = materializeNode(child.node, resources, actorById, ownedResources);
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
                              List<Disposable> ownedResources) {
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
