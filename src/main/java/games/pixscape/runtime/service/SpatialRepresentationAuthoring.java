package games.pixscape.runtime.service;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.AnimationSystem;

/** Explicit creation preparation only. Never called by loading, getters or frame processing. */
public final class SpatialRepresentationAuthoring {
    private SpatialRepresentationAuthoring() {
    }

    /** One immediate API mutation; preparation is O(affected subtree), using published topology. */
    public static PreparedSubtree prepareSubtree(World world, int root,
            games.pixscape.runtime.hierarchy.GameObjectTopologyState topology) {
        PreparedSubtree result = new PreparedSubtree(world);
        com.badlogic.gdx.utils.IntArray pending = new com.badlogic.gdx.utils.IntArray();
        pending.add(root);
        while (pending.size > 0) {
            int entity = pending.pop();
            ActorSpatialRectangles candidate = prepare(world, entity);
            if (candidate != null) {
                SpatialRepresentationComponent previous = world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null);
                result.entities.add(entity);
                result.present.add(previous != null);
                result.before.add(previous == null ? null : previous.rectangles);
                result.after.add(candidate);
            }
            if (topology != null && entity < topology.getEntityCapacity()) {
                for (int child = topology.firstChildEntityId[entity]; child >= 0; child = topology.nextSiblingEntityId[child]) {
                    pending.add(child);
                }
            }
        }
        return result;
    }

    /** Short-lived preparation/rollback state; Studio owns durable HistoryId-based snapshots separately. */
    public static final class PreparedSubtree {
        private final World world;
        private final com.badlogic.gdx.utils.IntArray entities = new com.badlogic.gdx.utils.IntArray();
        private final com.badlogic.gdx.utils.BooleanArray present = new com.badlogic.gdx.utils.BooleanArray();
        private final Array<ActorSpatialRectangles> before = new Array<ActorSpatialRectangles>();
        private final Array<ActorSpatialRectangles> after = new Array<ActorSpatialRectangles>();

        private PreparedSubtree(World world) {
            this.world = world;
        }

        public void apply() {
            for (int i = 0; i < entities.size; i++) {
                world.getMapper(SpatialRepresentationComponent.class).create(entities.get(i)).rectangles = after.get(i);
            }
        }

        public void restore() {
            for (int i = 0; i < entities.size; i++) {
                if (present.get(i)) {
                    world.getMapper(SpatialRepresentationComponent.class).create(entities.get(i)).rectangles = before.get(i);
                } else {
                    world.getMapper(SpatialRepresentationComponent.class).remove(entities.get(i));
                }
            }
        }
    }

    /** Returns an independently owned candidate, or null if no image/settings need initialization. */
    public static ActorSpatialRectangles prepare(World world, int entity) {
        if (!world.getMapper(TextureRegionComponent.class).has(entity)) {
            return null;
        }
        SpatialRepresentationComponent current = world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null);
        ActorSpatialRectangles candidate = current == null || current.rectangles == null
                ? new ActorSpatialRectangles() : current.rectangles.copy();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).getSafe(entity, null);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).getSafe(entity, null);
        boolean changed = initialize(candidate, asset == null ? 0 : asset.assetId,
                animation == null ? null : animation.currentClip);
        AnimationSystem animations = world.getSystem(AnimationSystem.class);
        if (animation != null && animations != null) {
            Array<String> names = new Array<String>();
            for (int i = 0; i < animation.animationAssetIds.size; i++) {
                int assetId = animation.animationAssetIds.get(i);
                AnimationDef definition = animations.definition(assetId);
                if (definition == null) {
                    throw new IllegalArgumentException("Spatial initialization requires animation asset " + assetId + ".");
                }
                names.clear();
                definition.copyClipNames(names);
                for (String name : names) {
                    changed |= initialize(candidate, assetId, name);
                }
            }
        }
        if (!changed) {
            return null;
        }
        candidate.validate();
        return candidate;
    }

    private static boolean initialize(ActorSpatialRectangles candidate, int asset, String clip) {
        if (candidate.get(asset, clip) != null) {
            return false;
        }
        candidate.put(asset, clip, new ActorSpatialRectangleData());
        return true;
    }
}
