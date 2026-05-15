package games.pixscape.runtime.render;

import games.pixscape.runtime.helper.RuntimeFs;

public enum ShaderVariant {
    DESKTOP_GL30(RuntimeFs.SHADER_VARIANT_DESKTOP_GL30, "GL30"),
    ES3_WEBGL2(RuntimeFs.SHADER_VARIANT_ES3_WEBGL2, "GL30");

    final String dirName;
    final String glProfile;

    ShaderVariant(String dirName, String glProfile) {
        this.dirName = dirName;
        this.glProfile = glProfile;
    }
}