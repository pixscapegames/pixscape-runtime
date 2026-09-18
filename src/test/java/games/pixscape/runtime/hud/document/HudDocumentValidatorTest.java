package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class HudDocumentValidatorTest {
    private static final String FIXTURES =
            "src/test/resources/games/pixscape/runtime/hud/document/v1/";

    private final HudDocumentCodec codec = new HudDocumentCodec();
    private final HudDocumentValidator validator = new HudDocumentValidator();

    @Test
    public void allPositiveFixturesValidateWithoutResourceCatalog() {
        String[] names = {
                "managed-table.json", "stack.json", "free-layout.json",
                "mixed-layout.json", "resources.json", "clipped-container.json"
        };

        for (int i = 0; i < names.length; i++) {
            HudValidationResult result = validator.validate(read(names[i]));
            Assert.assertTrue(names[i] + issues(result), result.isValid());
            Assert.assertTrue(result.issues().isEmpty());
            Assert.assertNotNull(result.validatedDocument());
        }
    }

    @Test
    public void duplicateIdProducesTypedIssueAndNoIndex() {
        HudValidationResult result = validator.validate(read("invalid-duplicate-node-id.json"));

        HudValidationIssue issue = requireIssue(result, HudValidationIssueCode.DUPLICATE_NODE_ID);
        Assert.assertEquals("duplicate", issue.nodeId());
        Assert.assertEquals("$.root.children[1].node.id", issue.path());
        Assert.assertNull(result.validatedDocument());
    }

    @Test
    public void blankAndMissingIdsAreRejectedAtTheirStructuralPaths() {
        HudNode root = new HudNode();
        root.kind = HudNodeKind.STACK;
        HudNode blank = label("   ");
        root.children.add(HudChild.direct(blank));

        HudValidationResult result = validator.validate(new HudDocumentV1(root));

        Assert.assertEquals(HudValidationIssueCode.MISSING_NODE_ID,
                result.issues().get(0).code());
        Assert.assertEquals("$.root.id", result.issues().get(0).path());
        Assert.assertEquals(HudValidationIssueCode.MISSING_NODE_ID,
                result.issues().get(1).code());
        Assert.assertEquals("$.root.children[0].node.id", result.issues().get(1).path());
    }

    @Test
    public void validDocumentPublishesCompleteReadOnlyPreorderIndex() {
        HudValidationResult result = validator.validate(read("resources.json"));

        Assert.assertTrue(result.isValid());
        ValidatedHudDocument validated = result.validatedDocument();
        Assert.assertSame(validated.document().root, validated.node("resource-stack"));
        Assert.assertEquals("resource-label", validated.node("resource-label").id);
        Assert.assertNull(validated.node("missing"));
        Assert.assertEquals(Arrays.asList("resource-stack", "scene-art", "ninepatch-panel",
                        "resource-label", "resource-button"),
                new ArrayList<String>(validated.nodeIndex().keySet()));
        try {
            validated.nodeIndex().put("other", label("other"));
            Assert.fail("Expected node index to be read-only.");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void committedPlacementMatrixAcceptsItsPositiveCases() {
        Assert.assertTrue(validator.validate(read("managed-table.json")).isValid());
        Assert.assertTrue(validator.validate(read("free-layout.json")).isValid());
        Assert.assertTrue(validator.validate(read("stack.json")).isValid());
        Assert.assertTrue(validator.validate(read("clipped-container.json")).isValid());
    }

    @Test
    public void tableRejectsDirectPlacement() {
        HudValidationResult result = validator.validate(read("invalid-table-placement.json"));

        HudValidationIssue issue = requireIssue(result,
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
        Assert.assertEquals("direct-label", issue.nodeId());
        Assert.assertEquals("$.root.children[0].placementKind", issue.path());
    }

    @Test
    public void cellAndFreePlacementAreRejectedOutsideTheirParentKinds() {
        HudNode stackWithCell = new HudNode("stack-cell", HudNodeKind.STACK);
        stackWithCell.children.add(HudChild.cell(label("cell-child"),
                new HudCellConstraints()));

        HudNode stackWithFree = new HudNode("stack-free", HudNodeKind.STACK);
        stackWithFree.children.add(HudChild.free(label("free-child"),
                new HudFreePlacement()));

        HudNode groupWithCell = new HudNode("group-cell", HudNodeKind.GROUP);
        groupWithCell.children.add(HudChild.cell(label("group-cell-child"),
                new HudCellConstraints()));

        requireIssue(validator.validate(new HudDocumentV1(stackWithCell)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
        requireIssue(validator.validate(new HudDocumentV1(stackWithFree)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
        requireIssue(validator.validate(new HudDocumentV1(groupWithCell)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
    }

    @Test
    public void placementRejectsContradictoryTypedData() {
        HudNode stack = new HudNode("stack", HudNodeKind.STACK);
        HudChild child = HudChild.direct(label("label"));
        child.cell = new HudCellConstraints();
        stack.children.add(child);

        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        HudChild cell = HudChild.cell(label("cell-label"), new HudCellConstraints());
        cell.free = new HudFreePlacement();
        table.children.add(cell);

        HudNode group = new HudNode("group", HudNodeKind.GROUP);
        HudChild free = HudChild.free(label("free-label"), new HudFreePlacement());
        free.cell = new HudCellConstraints();
        group.children.add(free);

        requireIssue(validator.validate(new HudDocumentV1(stack)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
        requireIssue(validator.validate(new HudDocumentV1(table)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
        requireIssue(validator.validate(new HudDocumentV1(group)),
                HudValidationIssueCode.INVALID_CHILD_PLACEMENT);
    }

    @Test
    public void leafChildrenAndCrowdedContainerAreRejected() {
        HudNode leaf = label("leaf");
        leaf.children.add(HudChild.direct(label("nested")));

        HudNode crowdedContainer = new HudNode("crowded", HudNodeKind.CONTAINER);
        crowdedContainer.container = new HudContainerData();
        crowdedContainer.children.add(HudChild.direct(label("first")));
        crowdedContainer.children.add(HudChild.direct(label("second")));

        HudValidationResult leafResult = validator.validate(new HudDocumentV1(leaf));

        requireIssue(leafResult, HudValidationIssueCode.INVALID_CHILD_COUNT);
        requireIssue(validator.validate(new HudDocumentV1(crowdedContainer)),
                HudValidationIssueCode.INVALID_CHILD_COUNT);
    }

    @Test
    public void containerAcceptsZeroOrOneDirectChild() {
        HudNode empty = new HudNode("empty", HudNodeKind.CONTAINER);
        empty.container = new HudContainerData();
        HudValidationResult emptyResult = validator.validate(new HudDocumentV1(empty));

        HudNode occupied = new HudNode("occupied", HudNodeKind.CONTAINER);
        occupied.container = new HudContainerData();
        occupied.children.add(HudChild.direct(new HudNode("group", HudNodeKind.GROUP)));
        HudValidationResult occupiedResult = validator.validate(new HudDocumentV1(occupied));

        Assert.assertTrue(emptyResult.issues().toString(), emptyResult.isValid());
        Assert.assertTrue(occupiedResult.issues().toString(), occupiedResult.isValid());
    }

    @Test
    public void recursiveAndReusedProgrammaticNodesAreRejected() {
        HudNode recursive = new HudNode("recursive", HudNodeKind.GROUP);
        recursive.children.add(HudChild.direct(recursive));

        HudNode shared = label("shared");
        HudNode stack = new HudNode("stack", HudNodeKind.STACK);
        stack.children.add(HudChild.direct(shared));
        stack.children.add(HudChild.direct(shared));

        requireIssue(validator.validate(new HudDocumentV1(recursive)),
                HudValidationIssueCode.INVALID_HIERARCHY);
        requireIssue(validator.validate(new HudDocumentV1(stack)),
                HudValidationIssueCode.INVALID_HIERARCHY);
    }

    @Test
    public void requiredAndExclusivePayloadRulesAreEnforced() {
        HudValidationResult fixture = validator.validate(read("invalid-node-payload.json"));

        HudNode image = new HudNode("image", HudNodeKind.IMAGE);
        image.image = imageData(HudImageSource.REGION, "icon");
        image.label = labelData("extra", "hud-body");
        HudValidationResult extra = validator.validate(new HudDocumentV1(image));

        requireIssue(fixture, HudValidationIssueCode.INVALID_NODE_PAYLOAD);
        requireIssue(extra, HudValidationIssueCode.INVALID_NODE_PAYLOAD);
    }

    @Test
    public void missingCommonActorBlockIsRejected() {
        HudNode node = label("label");
        node.actor = null;

        HudValidationResult result = validator.validate(new HudDocumentV1(node));

        requireIssue(result, HudValidationIssueCode.INVALID_NODE_PAYLOAD);
    }

    @Test
    public void missingKindIsValidationData() {
        HudNode node = new HudNode();
        node.id = "missing-kind";

        HudValidationIssue issue = requireIssue(
                validator.validate(new HudDocumentV1(node)),
                HudValidationIssueCode.UNKNOWN_NODE_KIND);

        Assert.assertEquals("missing-kind", issue.nodeId());
        Assert.assertEquals("$.root.kind", issue.path());
    }

    @Test
    public void nullTextIsRejectedButEmptyTextIsAllowed() {
        HudNode empty = label("empty");
        empty.label.text = "";
        HudNode missing = label("missing");
        missing.label.text = null;

        Assert.assertTrue(validator.validate(new HudDocumentV1(empty)).isValid());
        requireIssue(validator.validate(new HudDocumentV1(missing)),
                HudValidationIssueCode.INVALID_NODE_PAYLOAD);
    }

    @Test
    public void allAnchorCombinationsAndPivotEdgesAreAccepted() {
        HudNode group = new HudNode("root", HudNodeKind.GROUP);
        int id = 0;
        for (HudHorizontalAnchor horizontal : HudHorizontalAnchor.values()) {
            for (HudVerticalAnchor vertical : HudVerticalAnchor.values()) {
                HudFreePlacement placement = new HudFreePlacement();
                placement.horizontalAnchor = horizontal;
                placement.verticalAnchor = vertical;
                placement.pivotX = id % 2 == 0 ? 0f : 1f;
                placement.pivotY = id % 2 == 0 ? 1f : 0f;
                group.children.add(HudChild.free(label("label-" + id), placement));
                id++;
            }
        }

        HudValidationResult result = validator.validate(new HudDocumentV1(group));
        Assert.assertTrue(issues(result), result.isValid());
    }

    @Test
    public void pivotsOutsideInclusiveRangeAreRejected() {
        HudValidationResult result = validator.validate(read("invalid-free-pivot.json"));

        Assert.assertEquals(2, count(result, HudValidationIssueCode.INVALID_FREE_PLACEMENT));
    }

    @Test
    public void nonFiniteFreeAndActorValuesAreRejected() {
        HudNode group = new HudNode("root", HudNodeKind.GROUP);
        HudNode childNode = label("label");
        childNode.actor.width = Float.POSITIVE_INFINITY;
        HudFreePlacement free = new HudFreePlacement();
        free.pivotX = Float.NaN;
        free.offsetY = Float.NEGATIVE_INFINITY;
        group.children.add(HudChild.free(childNode, free));

        HudValidationResult result = validator.validate(new HudDocumentV1(group));

        requireIssue(result, HudValidationIssueCode.INVALID_FREE_PLACEMENT);
        requireIssue(result, HudValidationIssueCode.INVALID_DIMENSION);
    }

    @Test
    public void negativeAndNonFiniteCellNumbersAreRejected() {
        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        HudCellConstraints cell = new HudCellConstraints();
        cell.minWidth = -1f;
        cell.padTop = Float.NaN;
        table.children.add(HudChild.cell(label("label"), cell));

        HudValidationResult result = validator.validate(new HudDocumentV1(table));

        Assert.assertEquals(2, count(result, HudValidationIssueCode.INVALID_CELL_CONSTRAINTS));
    }

    @Test
    public void missingResourceNameIsRejectedWithoutCatalog() {
        HudValidationResult result = validator.validate(read("invalid-missing-resource.json"));

        requireIssue(result, HudValidationIssueCode.MISSING_RESOURCE_REFERENCE);
    }

    @Test
    public void builtInStylesNeedNoNamesWhileMissingImageSourceIsRejected() {
        HudNode root = new HudNode("root", HudNodeKind.STACK);
        HudNode image = new HudNode("image", HudNodeKind.IMAGE);
        image.image = imageData(null, "icon");
        HudNode label = label("label");
        label.label.styleName = "";
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "";
        button.textButton.styleName = null;
        root.children.add(HudChild.direct(image));
        root.children.add(HudChild.direct(label));
        root.children.add(HudChild.direct(button));

        HudValidationResult result = validator.validate(new HudDocumentV1(root));

        requireIssue(result, HudValidationIssueCode.INVALID_NODE_PAYLOAD);
        Assert.assertEquals(0,
                count(result, HudValidationIssueCode.MISSING_RESOURCE_REFERENCE));
    }

    @Test
    public void builtInLabelStyleIsExplicitlyValidatedWithoutAStyleName() {
        HudNode label = label("label");
        label.label.styleName = null;

        HudValidationResult missing = validator.validate(
                new HudDocumentV1(label), new EmptyResourceCatalog());
        HudValidationResult available = validator.validate(
                new HudDocumentV1(label), new FixtureResourceCatalog());

        requireIssue(missing, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE);
        Assert.assertTrue(issues(available), available.isValid());
    }

    @Test
    public void builtInTextButtonStyleIsExplicitlyValidatedWithoutAStyleName() {
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Button";

        HudValidationResult missing = validator.validate(
                new HudDocumentV1(button), new EmptyResourceCatalog());
        HudValidationResult available = validator.validate(
                new HudDocumentV1(button), new FixtureResourceCatalog());

        requireIssue(missing, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE);
        Assert.assertTrue(issues(available), available.isValid());
    }

    @Test
    public void textFieldValidatesDefaultsCustomStyleAndMaxLength() {
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();

        HudValidationResult missingDefault = validator.validate(
                new HudDocumentV1(field), new EmptyResourceCatalog());
        HudValidationResult availableDefault = validator.validate(
                new HudDocumentV1(field), new FixtureResourceCatalog());
        field.textField.styleName = "compact";
        HudValidationResult custom = validator.validate(
                new HudDocumentV1(field), new FixtureResourceCatalog());
        field.textField.styleName = "missing";
        field.textField.maxLength = -1;
        HudValidationResult invalid = validator.validate(
                new HudDocumentV1(field), new FixtureResourceCatalog());

        requireIssue(missingDefault, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE);
        Assert.assertTrue(issues(availableDefault), availableDefault.isValid());
        Assert.assertTrue(issues(custom), custom.isValid());
        Assert.assertEquals(1, count(invalid, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE));
        Assert.assertEquals(1, count(invalid, HudValidationIssueCode.INVALID_NODE_PAYLOAD));
    }

    @Test
    public void imageButtonValidatesEachConfiguredNativeImageState() {
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = imageData(HudImageSource.REGION, "inventory-art");
        button.imageButton.imageOver = imageData(HudImageSource.DRAWABLE, "inventory-panel");
        button.imageButton.imageChecked = imageData(HudImageSource.REGION, "missing");

        HudValidationResult result = validator.validate(
                new HudDocumentV1(button), new FixtureResourceCatalog());

        Assert.assertEquals(1, count(result, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE));
        Assert.assertEquals("$.root.imageButton.imageChecked.resourceName", result.issues().get(0).path());
    }

    @Test
    public void catalogAcceptsKnownFixtureReferences() {
        HudValidationResult result = validator.validate(read("resources.json"),
                new FixtureResourceCatalog());

        Assert.assertTrue(issues(result), result.isValid());
    }

    @Test
    public void catalogReportsUnknownRegionDrawableAndStyles() {
        HudValidationResult result = validator.validate(read("resources.json"),
                new EmptyResourceCatalog());

        Assert.assertEquals(4,
                count(result, HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE));
        Assert.assertEquals("scene-art", result.issues().get(0).nodeId());
        Assert.assertEquals("$.root.children[0].node.image.resourceName",
                result.issues().get(0).path());
    }

    @Test
    public void unsupportedProgrammaticSchemaAndMissingRootAreValidationData() {
        HudDocumentV1 unsupported = new HudDocumentV1(label("root"));
        unsupported.schemaVersion = 2;
        HudDocumentV1 missingRoot = new HudDocumentV1();

        HudValidationResult unsupportedResult = validator.validate(unsupported);
        HudValidationResult missingRootResult = validator.validate(missingRoot);

        Assert.assertEquals(HudValidationIssueCode.UNSUPPORTED_SCHEMA_VERSION,
                unsupportedResult.issues().get(0).code());
        Assert.assertEquals("$.schemaVersion", unsupportedResult.issues().get(0).path());
        Assert.assertEquals(HudValidationIssueCode.MISSING_ROOT,
                missingRootResult.issues().get(0).code());
        Assert.assertEquals("$.root", missingRootResult.issues().get(0).path());
    }

    @Test
    public void issueOrderingIsStableAndFollowsPreorderChecks() {
        HudNode root = new HudNode();
        root.id = " ";
        root.kind = HudNodeKind.LABEL;
        root.actor.width = -1f;
        root.children.add(HudChild.direct(label("nested")));

        HudValidationResult result = validator.validate(new HudDocumentV1(root));

        Assert.assertEquals(HudValidationIssueCode.MISSING_NODE_ID,
                result.issues().get(0).code());
        Assert.assertEquals(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                result.issues().get(1).code());
        Assert.assertEquals(HudValidationIssueCode.INVALID_DIMENSION,
                result.issues().get(2).code());
        Assert.assertEquals(HudValidationIssueCode.INVALID_CHILD_COUNT,
                result.issues().get(3).code());
        Assert.assertEquals("$.root.children", result.issues().get(3).path());
    }

    private HudDocumentV1 read(String name) {
        return codec.read(new FileHandle(FIXTURES + name));
    }

    private static HudNode label(String id) {
        HudNode node = new HudNode();
        node.id = id;
        node.kind = HudNodeKind.LABEL;
        node.label = labelData("Label", "hud-body");
        return node;
    }

    private static HudLabelData labelData(String text, String style) {
        HudLabelData data = new HudLabelData();
        data.text = text;
        data.styleName = style;
        return data;
    }

    private static HudImageData imageData(HudImageSource source, String name) {
        HudImageData data = new HudImageData();
        data.source = source;
        data.resourceName = name;
        return data;
    }

    private static HudValidationIssue requireIssue(HudValidationResult result,
                                                   HudValidationIssueCode code) {
        for (int i = 0; i < result.issues().size(); i++) {
            HudValidationIssue issue = result.issues().get(i);
            if (issue.code() == code) return issue;
        }
        Assert.fail("Expected issue " + code + issues(result));
        return null;
    }

    private static int count(HudValidationResult result, HudValidationIssueCode code) {
        int count = 0;
        for (int i = 0; i < result.issues().size(); i++) {
            if (result.issues().get(i).code() == code) count++;
        }
        return count;
    }

    private static String issues(HudValidationResult result) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < result.issues().size(); i++) {
            HudValidationIssue issue = result.issues().get(i);
            out.append("\n").append(issue.code()).append(" at ")
                    .append(issue.path()).append(": ").append(issue.message());
        }
        return out.toString();
    }

    private static class EmptyResourceCatalog implements HudResourceCatalog {
        @Override public boolean hasRegion(String name) { return false; }
        @Override public boolean hasDrawable(String name) { return false; }
        @Override public boolean hasLabelStyle(String name) { return false; }
        @Override public boolean hasTextButtonStyle(String name) { return false; }
    }

    private static final class FixtureResourceCatalog extends EmptyResourceCatalog {
        @Override public boolean hasBuiltInLabelStyle() { return true; }
        @Override public boolean hasBuiltInTextButtonStyle() { return true; }
        @Override public boolean hasBuiltInImageButtonStyle() { return true; }
        @Override public boolean hasBuiltInTextFieldStyle() { return true; }

        @Override
        public boolean hasRegion(String name) {
            return "inventory-art".equals(name);
        }

        @Override
        public boolean hasDrawable(String name) {
            return "inventory-panel".equals(name);
        }

        @Override
        public boolean hasLabelStyle(String name) {
            return "hud-body-bitmap".equals(name);
        }

        @Override
        public boolean hasTextButtonStyle(String name) {
            return "hud-primary".equals(name);
        }

        @Override
        public boolean hasImageButtonStyle(String name) {
            return "hud-primary".equals(name);
        }

        @Override
        public boolean hasTextFieldStyle(String name) {
            return "compact".equals(name);
        }
    }
}
