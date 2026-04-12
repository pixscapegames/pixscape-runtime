package games.pixscape.runtime.api;

public interface AnimationFacade {
    boolean exists();

    AnimationFacade play();
    AnimationFacade pause();
    AnimationFacade stop();
    AnimationFacade restart();

    AnimationFacade play(String clipName);
    AnimationFacade setClip(String clipName);

    AnimationFacade setLoop(boolean loop);
    AnimationFacade setFps(float fps);
    AnimationFacade setStateTime(float stateTime);

    boolean isPlaying();
    boolean isLooping();
    float fps();
}
