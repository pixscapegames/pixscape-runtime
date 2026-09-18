package games.pixscape.runtime.hud.document;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HudImageReferencesTest {
    @Test
    public void visitsImageAndEveryExplicitImageButtonStateWithFieldContext() {
        HudNode image = new HudNode("image", HudNodeKind.IMAGE);
        image.image = image(HudImageSource.REGION, "standalone");
        Assert.assertEquals(Arrays.asList("image:REGION:standalone"), visit(image));

        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = image(HudImageSource.REGION, "up");
        button.imageButton.imageDown = image(HudImageSource.DRAWABLE, "down");
        button.imageButton.imageOver = image(HudImageSource.REGION, "over");
        button.imageButton.imageDisabled = image(HudImageSource.DRAWABLE, "disabled");
        button.imageButton.imageChecked = image(HudImageSource.REGION, "checked");
        button.imageButton.imageCheckedDown = image(HudImageSource.DRAWABLE, "checked-down");
        button.imageButton.imageCheckedOver = image(HudImageSource.REGION, "checked-over");

        Assert.assertEquals(Arrays.asList(
                "imageButton.imageUp:REGION:up",
                "imageButton.imageDown:DRAWABLE:down",
                "imageButton.imageOver:REGION:over",
                "imageButton.imageDisabled:DRAWABLE:disabled",
                "imageButton.imageChecked:REGION:checked",
                "imageButton.imageCheckedDown:DRAWABLE:checked-down",
                "imageButton.imageCheckedOver:REGION:checked-over"), visit(button));
    }

    @Test
    public void ignoresAbsentOptionalStates() {
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageChecked = image(HudImageSource.REGION, "checked");

        Assert.assertEquals(Arrays.asList("imageButton.imageChecked:REGION:checked"),
                visit(button));
    }

    private static List<String> visit(HudNode node) {
        final List<String> references = new ArrayList<String>();
        HudImageReferences.visit(node, new HudImageReferences.Visitor() {
            @Override
            public void visit(HudNode ignored, String fieldPath, HudImageData image) {
                references.add(fieldPath + ":" + image.source + ":" + image.resourceName);
            }
        });
        return references;
    }

    private static HudImageData image(HudImageSource source, String resourceName) {
        HudImageData image = new HudImageData();
        image.source = source;
        image.resourceName = resourceName;
        return image;
    }
}
