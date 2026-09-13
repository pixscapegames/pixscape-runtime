package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudHorizontalAlign;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudVerticalAlign;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts a validated V1 construction document into one detached native Scene2D actor tree. */
public final class HudMaterializer {
    public MaterializedHud materialize(
            ValidatedHudDocument validatedDocument, HudResources resources) {
        if (validatedDocument == null) {
            throw new IllegalArgumentException("ValidatedHudDocument is required.");
        }
        if (resources == null) throw new IllegalArgumentException("HudResources is required.");
        if (resources.isDisposed()) {
            throw new IllegalStateException("HudResources has been disposed.");
        }

        Map<String, Actor> actorById = new LinkedHashMap<String, Actor>();
        Actor root = materializeNode(validatedDocument.document().root, resources, actorById);
        if (actorById.size() != validatedDocument.nodeIndex().size()) {
            throw new IllegalStateException(
                    "Validated HUD document changed after validation; validate it again.");
        }
        return new MaterializedHud(root, actorById);
    }

    private Actor materializeNode(
            HudNode node, HudResources resources, Map<String, Actor> actorById) {
        Actor actor = createActor(node, resources);
        actor.setName(node.id);
        applyAuthoredSize(actor, node.actor.width, node.actor.height);
        if (actorById.put(node.id, actor) != null) {
            throw new IllegalStateException(
                    "Validated HUD document contains duplicate node ID '" + node.id + "'.");
        }

        for (int i = 0; i < node.children.size(); i++) {
            HudChild child = node.children.get(i);
            Actor childActor = materializeNode(child.node, resources, actorById);
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

    private Actor createActor(HudNode node, HudResources resources) {
        Skin skin = resources.skin();
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
            case IMAGE:
                if (node.image.source == HudImageSource.REGION) {
                    AtlasRegion region = resources.atlas().findRegion(node.image.resourceName);
                    if (region == null) {
                        throw missing(node, "atlas region", node.image.resourceName);
                    }
                    return new Image(region);
                }
                if (!resources.hasDrawable(node.image.resourceName)) {
                    throw missing(node, "Skin drawable", node.image.resourceName);
                }
                Drawable drawable = skin.getDrawable(node.image.resourceName);
                return new Image(drawable);
            case LABEL:
                Label.LabelStyle labelStyle =
                        skin.optional(node.label.styleName, Label.LabelStyle.class);
                if (labelStyle == null) {
                    throw missing(node, "Label style", node.label.styleName);
                }
                return new Label(node.label.text, labelStyle);
            case TEXT_BUTTON:
                TextButton.TextButtonStyle buttonStyle =
                        skin.optional(node.textButton.styleName, TextButton.TextButtonStyle.class);
                if (buttonStyle == null) {
                    throw missing(node, "TextButton style", node.textButton.styleName);
                }
                return new TextButton(node.textButton.text, buttonStyle);
            default:
                throw new IllegalStateException(
                        "Unsupported validated HUD node kind: " + node.kind + ".");
        }
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
        cell.minSize(constraints.minWidth, constraints.minHeight);
        cell.prefSize(constraints.prefWidth, constraints.prefHeight);
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

    private static IllegalStateException missing(
            HudNode node, String resourceKind, String resourceName) {
        return new IllegalStateException("Validated HUD node '" + node.id + "' requires "
                + resourceKind + " '" + resourceName + "' in the prepared HudResources.");
    }
}
