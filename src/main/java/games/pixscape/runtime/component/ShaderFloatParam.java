package games.pixscape.runtime.component;

public class ShaderFloatParam {
    public String name;
    public float value;

    public ShaderFloatParam() {
    }

    public ShaderFloatParam(String name, float value) {
        this.name = name;
        this.value = value;
    }
}