package games.pixscape.runtime.api;

/**
 * High-level sprite/material access for one entity.
 *
 * <p>Operations affect an existing complete authored sprite capability only.
 * They do not convert an arbitrary entity into a sprite.</p>
 */
public interface SpriteFacade {
    /**
     * Returns the current sprite asset id, or {@code -1} when the entity has no asset reference.
     */
    int assetId();

    /**
     * Changes the sprite asset within the current atlas tag.
     *
     * <p>The new asset must be available in the current atlas tag. An unavailable or invalid
     * asset causes {@link IllegalArgumentException} before mutation, preserving the previous
     * authored asset and live render binding.</p>
     *
     * @param assetId asset id to assign
     * @return this facade for chaining
     */
    SpriteFacade setAssetId(int assetId);

    /**
     * Changes the sprite asset and atlas tag.
     *
     * <p>The asset must exist in the requested atlas tag. A blank tag resolves to {@code main}.
     * An unavailable or invalid asset causes {@link IllegalArgumentException} before mutation,
     * preserving the previous authored asset and live render binding.</p>
     *
     * @param assetId asset id to assign
     * @param atlasTag atlas tag to resolve against, or blank for {@code main}
     * @return this facade for chaining
     */
    SpriteFacade setAsset(int assetId, String atlasTag);

    /**
     * Sets whether the sprite participates in rendering.
     *
     * @param visible {@code true} to render the sprite, {@code false} to hide it
     * @return this facade for chaining
     */
    SpriteFacade setVisible(boolean visible);

    /**
     * Sets the sprite tint color.
     *
     * <p>Each color component is clamped to the {@code [0, 1]} range.</p>
     *
     * @param r red channel
     * @param g green channel
     * @param b blue channel
     * @param a alpha channel
     * @return this facade for chaining
     */
    SpriteFacade setTint(float r, float g, float b, float a);

    /**
     * Sets the sprite tint alpha.
     *
     * <p>The value is clamped to the {@code [0, 1]} range.</p>
     *
     * @param alpha alpha channel value
     * @return this facade for chaining
     */
    SpriteFacade setAlpha(float alpha);

    /**
     * Sets the sprite render size in world units.
     *
     * @param width render width in world units
     * @param height render height in world units
     * @return this facade for chaining
     */
    SpriteFacade setSize(float width, float height);

    /**
     * Returns whether horizontal repeat is configured for this sprite.
     */
    boolean repeatsX();

    /**
     * Returns whether vertical repeat is configured for this sprite.
     */
    boolean repeatsY();

    /**
     * Configures axis-aligned Repeat V1 rendering on the X and Y axes.
     *
     * <p>Repeat configuration is effective only for non-animated, axis-aligned
     * sprites. Animated sprites retain the configuration but Runtime does not
     * submit repeated draws for them. Rotated sprites fall back to one draw.</p>
     *
     * @param repeatX whether to repeat horizontally
     * @param repeatY whether to repeat vertically
     * @return this facade for chaining
     */
    SpriteFacade setRepeat(boolean repeatX, boolean repeatY);
}
