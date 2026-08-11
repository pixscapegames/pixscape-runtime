package games.pixscape.runtime.api;

/**
 * High-level sprite clip animation controls for one entity.
 *
 * <p>Operations affect an existing animated-sprite capability only. They do not
 * create animation or render components on an arbitrary entity.</p>
 */
public interface AnimationFacade {
    /**
     * Returns whether the entity has a complete animated-sprite capability.
     */
    boolean exists();

    /**
     * Returns the selected clip name, or an empty string when animation is absent.
     */
    String clip();

    /**
     * Returns whether the animation defines the supplied non-blank clip name.
     */
    boolean hasClip(String clipName);

    /**
     * Returns the resolved animation/atlas frame index held by Runtime, or
     * {@code -1} when no frame has been resolved.
     */
    int frame();

    /**
     * Returns the existing animation playback time, or {@code 0} when absent.
     */
    float stateTime();

    AnimationFacade play();

    AnimationFacade pause();

    AnimationFacade stop();

    AnimationFacade restart();

    /**
     * Atomically selects an animation owned by this entity, restoring the definition's authored
     * default clip and fps while preserving playback and loop state.
     *
     * @throws IllegalArgumentException when the animation is unknown, unavailable, or not owned
     * by this entity; the previous animation state remains unchanged
     */
    AnimationFacade setAnimation(int assetId);

    /**
     * Atomically selects an animation owned by this entity, restoring the definition's authored
     * default clip and fps while preserving playback and loop state.
     *
     * @throws IllegalArgumentException when the animation is unknown, unavailable, or not owned
     * by this entity; the previous animation state remains unchanged
     */
    AnimationFacade setAnimation(String animationName);

    /**
     * Selects and starts an existing clip.
     *
     * @throws IllegalArgumentException when the clip name is blank or unknown; the previous
     * animation state remains unchanged
     */
    AnimationFacade play(String clipName);

    /**
     * Atomically selects an owned animation and starts one of its clips.
     *
     * @throws IllegalArgumentException when the animation or clip is unknown, the animation is
     * unavailable, or the animation is not owned by this entity; previous state remains unchanged
     */
    AnimationFacade play(int animationAssetId, String clipName);

    /**
     * Atomically selects an owned animation and starts one of its clips.
     *
     * @throws IllegalArgumentException when the animation or clip is unknown, the animation is
     * unavailable, or the animation is not owned by this entity; previous state remains unchanged
     */
    AnimationFacade play(String animationName, String clipName);

    /**
     * Selects an existing clip and resets its frame and playback time.
     *
     * @throws IllegalArgumentException when the clip name is blank or unknown; the previous
     * animation state remains unchanged
     */
    AnimationFacade setClip(String clipName);

    AnimationFacade setLoop(boolean loop);

    /**
     * Sets playback frames per second. Zero is allowed as a non-advancing rate.
     *
     * @throws IllegalArgumentException when {@code fps} is negative, NaN, or infinite
     */
    AnimationFacade setFps(float fps);

    /**
     * Sets playback time, clamping negative finite values to zero.
     *
     * @throws IllegalArgumentException when the value is NaN or infinite
     */
    AnimationFacade setStateTime(float stateTime);

    boolean isPlaying();

    boolean isLooping();

    float fps();

    /**
     * Returns whether the selected non-looping clip has consumed its complete
     * playback duration. Looping clips and missing or invalid animation state
     * never report finished.
     */
    boolean isFinished();
}
