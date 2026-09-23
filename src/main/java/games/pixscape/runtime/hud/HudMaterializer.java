package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
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
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableLayout;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudVerticalAlign;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudWindowAction;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
import games.pixscape.runtime.hud.document.HudDialogResultButton;

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

    /** Studio authoring omits runtime interactions and keeps authored Dialogs visible for editing. */
    public MaterializedHud materialize(
            ValidatedHudDocument validatedDocument, HudVisualResources resources,
            boolean interactive) {
        if (validatedDocument == null) {
            throw new IllegalArgumentException("ValidatedHudDocument is required.");
        }
        if (resources == null) throw new IllegalArgumentException("HudVisualResources is required.");

        Map<String, Actor> actorById = new LinkedHashMap<String, Actor>();
        Map<String, Cell<?>> cellById = new LinkedHashMap<String, Cell<?>>();
        List<Disposable> ownedResources = new ArrayList<Disposable>();
        Map<Actor, TextTooltip> tooltips = new LinkedHashMap<Actor, TextTooltip>();
        TooltipManager tooltipManager = interactive ? new TooltipManager() : null;
        if (tooltipManager != null) {
            tooltipManager.initialTime = 0.5f;
            tooltipManager.hideAll(); // Reset the manager's current delay before the first tooltip.
        }
        try {
            Actor root = materializeNode(validatedDocument.document().root, resources,
                    actorById, cellById, ownedResources, tooltipManager, tooltips, true, interactive);
            if (actorById.size() != validatedDocument.nodeIndex().size()) {
                throw new IllegalStateException(
                        "Validated HUD document changed after validation; validate it again.");
            }
            if (interactive) bindWindowActions(validatedDocument, actorById);
            return new MaterializedHud(root, actorById, cellById, ownedResources, tooltipManager, tooltips);
        } catch (RuntimeException failure) {
            MaterializedHud.releaseTooltips(tooltipManager, tooltips);
            disposeOwned(ownedResources);
            throw failure;
        }
    }

    private Actor materializeNode(
            HudNode node, HudVisualResources resources, Map<String, Actor> actorById,
            Map<String, Cell<?>> cellById,
            List<Disposable> ownedResources, TooltipManager tooltipManager,
            Map<Actor, TextTooltip> tooltips, boolean stageRoot, boolean interactive) {
        Actor actor = createActor(node, resources, ownedResources, interactive, stageRoot);
        actor.setName(node.id);
        actor.setVisible(node.kind == HudNodeKind.DIALOG ? !interactive : node.visible);
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

        if (isTabular(node.kind)) {
            Table table = actor instanceof HudDialog
                    ? ((HudDialog) actor).getContentTable() : (Table) actor;
            addExplicitTable(table, node.table, resources, actorById, cellById, ownedResources,
                    tooltipManager, tooltips, interactive);
            if (actor instanceof HudDialog) {
                HudDialog dialog = (HudDialog) actor;
                for (HudDialogResultButton entry : node.dialog.resultButtons) {
                    Button button = (Button) materializeNode(entry.button, resources, actorById,
                            cellById, ownedResources, tooltipManager, tooltips, false, interactive);
                    dialog.addResultButton(button, entry.resultId, entry.closeAfterActivation,
                            interactive);
                }
            }
            return actor;
        }

        for (int i = 0; i < node.children.size(); i++) {
            HudChild child = node.children.get(i);
            Actor childActor = materializeNode(child.node, resources, actorById, cellById, ownedResources,
                    tooltipManager, tooltips, false, interactive);
            if (childActor instanceof HudDialog) {
                HudDialog dialog = (HudDialog) childActor;
                HudDialogSlot slot = new HudDialogSlot(dialog);
                dialog.attach(slot, stageRoot && node.kind == HudNodeKind.GROUP, interactive
                        && child.node.dialog.keepWithinStage);
                childActor = slot;
            }
            switch (child.placementKind) {
                case DIRECT:
                    addDirect(actor, childActor, node.id);
                    break;
                case CELL:
                    addCell(actor instanceof HudDialog
                            ? ((HudDialog) actor).getContentTable() : (Table) actor,
                            childActor, child.cell);
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

    private void addExplicitTable(Table table, HudTableLayout layout, HudVisualResources resources,
                                  Map<String, Actor> actorById, Map<String, Cell<?>> cellById,
                                  List<Disposable> ownedResources, TooltipManager tooltipManager,
                                  Map<Actor, TextTooltip> tooltips, boolean interactive) {
        for (int rowIndex = 0; rowIndex < layout.rows.size(); rowIndex++) {
            HudTableRow row = layout.rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < row.cells.size(); cellIndex++) {
                HudTableCell source = row.cells.get(cellIndex);
                Actor content = source.content != null
                        ? materializeNode(source.content, resources, actorById, cellById, ownedResources,
                        tooltipManager, tooltips, false, interactive) : null;
                if (content instanceof HudDialog) {
                    HudDialog dialog = (HudDialog) content;
                    HudDialogSlot slot = new HudDialogSlot(dialog);
                    dialog.attach(slot, false, interactive && source.content.dialog.keepWithinStage);
                    content = slot;
                }
                Cell<Actor> cell = table.add(content);
                applyCellConstraints(cell, source.constraints);
                cell.colspan(source.colspan);
                if (cellById.put(source.id, cell) != null) {
                    throw new IllegalStateException("Validated HUD document contains duplicate cell ID '"
                            + source.id + "'.");
                }
            }
            table.row();
        }
    }

    private Actor createActor(HudNode node, HudVisualResources resources,
                              List<Disposable> ownedResources, boolean interactivePreview,
                              boolean stageRoot) {
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
                return createWindow(node, node.window, resources, interactivePreview, stageRoot, false);
            }
            case DIALOG: {
                return createWindow(node, node.dialog, resources, interactivePreview, stageRoot, true);
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
            case LIST: {
                com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle sharedStyle =
                        HudBuiltInSelectBoxStyle.isSelected(node.list.styleName)
                                ? resources.builtInListStyle() : resources.listStyle(node.list.styleName);
                if (sharedStyle == null) {
                    throw missing(node, "List style", HudBuiltInSelectBoxStyle.isSelected(node.list.styleName)
                            ? "built-in Default" : node.list.styleName);
                }
                com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle style = sharedStyle;
                if (node.list.fontAssetId != null) {
                    style = new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(sharedStyle);
                    style.font = requireFont(node, node.list.fontAssetId, resources);
                }
                com.badlogic.gdx.scenes.scene2d.ui.List<String> list =
                        new com.badlogic.gdx.scenes.scene2d.ui.List<String>(style);
                list.getSelection().setMultiple(false);
                list.getSelection().setRequired(node.list.required);
                list.setItems(node.list.items.toArray(new String[node.list.items.size()]));
                list.setSelectedIndex(node.list.selectedIndex);
                // List's constructor sizes before items exist; the initial free/root size must use
                // the preferred size after setItems. Parent layout may still override this size.
                list.setSize(list.getPrefWidth(), list.getPrefHeight());
                return list;
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

    private static Window createWindow(HudNode node, HudWindowData data,
                                       HudVisualResources resources, boolean interactivePreview,
                                       boolean stageRoot, boolean dialogKind) {
        Window.WindowStyle sharedStyle = HudBuiltInWindowStyle.isSelected(data.styleName)
                ? resources.builtInWindowStyle() : resources.windowStyle(data.styleName);
        if (!HudStyleUsability.isUsableWindowStyle(sharedStyle, data.fontAssetId != null)) {
            throw missing(node, "Window style",
                    HudBuiltInWindowStyle.isSelected(data.styleName)
                            ? "built-in Default" : data.styleName);
        }
        Window.WindowStyle style = sharedStyle;
        if (data.fontAssetId != null) {
            style = new Window.WindowStyle(sharedStyle);
            style.titleFont = requireFont(node, data.fontAssetId, resources);
        }
        Window window = dialogKind ? new HudDialog(data.title, style)
                : new Window(data.title, style);
        window.setMovable(interactivePreview && data.movable);
        window.setResizable(interactivePreview && data.resizable);
        window.setModal(interactivePreview && data.modal);
        // Nested windows use parent-local coordinates, not Stage coordinates.
        window.setKeepWithinStage(interactivePreview && stageRoot && data.keepWithinStage);
        return window;
    }

    private static void bindWindowActions(ValidatedHudDocument document,
                                          Map<String, Actor> actors) {
        for (HudNode node : document.nodeIndex().values()) {
            if (node.windowActions.isEmpty()) continue;
            Button source = (Button) actors.get(node.id);
            for (HudWindowAction association : node.windowActions) {
                final Actor target = actors.get(association.targetId);
                final HudWindowActionKind action = association.action;
                source.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent event, float x, float y) {
                        if (source.isDisabled()) return;
                        if (target instanceof HudDialog) {
                            HudDialog dialog = (HudDialog) target;
                            switch (action) {
                                case SHOW: dialog.open(); break;
                                case HIDE: dialog.close(); break;
                                case TOGGLE: if (dialog.isOpen()) dialog.close(); else dialog.open(); break;
                            }
                        } else {
                            Window window = (Window) target;
                            boolean visible = action == HudWindowActionKind.SHOW
                                    || action == HudWindowActionKind.TOGGLE
                                    && !window.isVisible();
                            window.setVisible(visible);
                            if (!visible && window.getStage() != null) window.getStage().unfocus(window);
                        }
                    }
                });
            }
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
        applyCellConstraints(cell, constraints);
    }

    private static void applyCellConstraints(Cell<Actor> cell, HudCellConstraints constraints) {
        if (constraints.minWidth != null) cell.minWidth(constraints.minWidth);
        if (constraints.minHeight != null) cell.minHeight(constraints.minHeight);
        if (constraints.prefWidth != null) cell.prefWidth(constraints.prefWidth);
        if (constraints.prefHeight != null) cell.prefHeight(constraints.prefHeight);
        cell.pad(constraints.padTop, constraints.padLeft,
                constraints.padBottom, constraints.padRight);
        cell.fill(constraints.fillX, constraints.fillY);
        cell.expand(constraints.expandX, constraints.expandY);
        cell.align(cellAlign(constraints.horizontalAlign, constraints.verticalAlign));
    }

    private static boolean isTabular(HudNodeKind kind) {
        return kind == HudNodeKind.TABLE || kind == HudNodeKind.WINDOW || kind == HudNodeKind.DIALOG;
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
