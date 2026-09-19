package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.runtime.hud.HudBuiltInImageButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInTextButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInTextFieldStyle;
import games.pixscape.runtime.hud.HudBuiltInSelectBoxStyle;
import games.pixscape.runtime.hud.HudBuiltInCheckBoxStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Strict, deterministic, GL-free validator for {@link HudDocumentV1}. */
public final class HudDocumentValidator {
    public HudValidationResult validate(HudDocumentV1 document) {
        return validate(document, null);
    }

    /**
     * Validates structural and semantic invariants, optionally resolving logical resource names.
     * Validation performs one preorder hierarchy traversal and builds the complete ID index during
     * that traversal. Invalid documents never publish an index.
     */
    public HudValidationResult validate(HudDocumentV1 document, HudResourceCatalog resources) {
        if (document == null) throw new IllegalArgumentException("HUD document is required.");

        ValidationState state = new ValidationState(resources);
        if (document.schemaVersion != HudDocumentV1.CURRENT_SCHEMA_VERSION) {
            state.add(HudValidationIssueCode.UNSUPPORTED_SCHEMA_VERSION,
                    "schemaVersion must be " + HudDocumentV1.CURRENT_SCHEMA_VERSION
                            + ", found " + document.schemaVersion + ".",
                    null, "$.schemaVersion");
            return state.result(document);
        }
        if (document.root == null) {
            state.add(HudValidationIssueCode.MISSING_ROOT,
                    "HUD document requires exactly one root node.", null, "$.root");
            return state.result(document);
        }

        state.visit(document.root, "$.root");
        return state.result(document);
    }

    private static final class ValidationState {
        private final HudResourceCatalog resources;
        private final List<HudValidationIssue> issues = new ArrayList<HudValidationIssue>();
        private final Map<String, HudNode> nodeIndex = new LinkedHashMap<String, HudNode>();
        private final ObjectSet<HudNode> visiting = new ObjectSet<HudNode>();
        private final ObjectSet<HudNode> visited = new ObjectSet<HudNode>();

        ValidationState(HudResourceCatalog resources) {
            this.resources = resources;
        }

        HudValidationResult result(HudDocumentV1 document) {
            ValidatedHudDocument validated = issues.isEmpty()
                    ? new ValidatedHudDocument(document, nodeIndex)
                    : null;
            return new HudValidationResult(issues, validated);
        }

        void visit(HudNode node, String path) {
            if (node == null) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "HUD hierarchy contains a null node.", null, path);
                return;
            }
            if (visiting.contains(node)) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "HUD hierarchy contains a recursive node reference.", usableId(node), path);
                return;
            }
            if (visited.contains(node)) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "HUD hierarchy reuses one node object under multiple child placements.",
                        usableId(node), path);
                return;
            }
            visiting.add(node);
            visited.add(node);

            validateIdentity(node, path);
            validateKindAndPayload(node, path);
            validateDimensions(node, path);
            validateChildCount(node, path);

            if (node.children != null) {
                for (int i = 0; i < node.children.size(); i++) {
                    HudChild child = node.children.get(i);
                    String childPath = path + ".children[" + i + "]";
                    if (child == null) {
                        add(HudValidationIssueCode.INVALID_HIERARCHY,
                                "HUD hierarchy contains a null child placement.",
                                usableId(node), childPath);
                        continue;
                    }
                    validatePlacement(node, child, childPath);
                    visit(child.node, childPath + ".node");
                }
            }

            visiting.remove(node);
        }

        private void validateIdentity(HudNode node, String path) {
            if (!isNonBlank(node.id)) {
                add(HudValidationIssueCode.MISSING_NODE_ID,
                        "Every HUD node requires a nonblank document-wide ID.", null,
                        path + ".id");
                return;
            }
            HudNode previous = nodeIndex.get(node.id);
            if (previous != null) {
                add(HudValidationIssueCode.DUPLICATE_NODE_ID,
                        "HUD node ID '" + node.id + "' is duplicated document-wide.",
                        node.id, path + ".id");
                return;
            }
            nodeIndex.put(node.id, node);
        }

        private void validateKindAndPayload(HudNode node, String path) {
            if (node.kind == null) {
                add(HudValidationIssueCode.UNKNOWN_NODE_KIND,
                        "HUD node kind is required.", usableId(node), path + ".kind");
                return;
            }

            int payloadCount = payloadCount(node);
            boolean validPayload;
            switch (node.kind) {
                case GROUP:
                case TABLE:
                case STACK:
                    validPayload = payloadCount == 0;
                    break;
                case CONTAINER:
                    validPayload = node.container != null && payloadCount == 1;
                    break;
                case IMAGE:
                    validPayload = node.image != null && payloadCount == 1;
                    break;
                case LABEL:
                    validPayload = node.label != null && payloadCount == 1;
                    break;
                case TEXT_BUTTON:
                    validPayload = node.textButton != null && payloadCount == 1;
                    break;
                case IMAGE_BUTTON:
                    validPayload = node.imageButton != null && payloadCount == 1;
                    break;
                case TEXT_FIELD:
                    validPayload = node.textField != null && payloadCount == 1;
                    break;
                case SELECT_BOX:
                    validPayload = node.selectBox != null && payloadCount == 1;
                    break;
                case CHECK_BOX:
                    validPayload = node.checkBox != null && payloadCount == 1;
                    break;
                default:
                    validPayload = false;
                    break;
            }
            if (!validPayload) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "HUD payloads do not match node kind " + node.kind + ".",
                        usableId(node), path);
            }

            if (node.kind == HudNodeKind.IMAGE && node.image != null) {
                validateImages(node, path);
            } else if (node.kind == HudNodeKind.LABEL && node.label != null) {
                validateLabel(node, path);
            } else if (node.kind == HudNodeKind.TEXT_BUTTON && node.textButton != null) {
                validateTextButton(node, path);
            } else if (node.kind == HudNodeKind.IMAGE_BUTTON && node.imageButton != null) {
                validateImageButton(node, path);
            } else if (node.kind == HudNodeKind.TEXT_FIELD && node.textField != null) {
                validateTextField(node, path);
            } else if (node.kind == HudNodeKind.SELECT_BOX && node.selectBox != null) {
                validateSelectBox(node, path);
            } else if (node.kind == HudNodeKind.CHECK_BOX && node.checkBox != null) {
                validateCheckBox(node, path);
            }
        }

        private void validateImages(final HudNode node, final String path) {
            HudImageReferences.visit(node, new HudImageReferences.Visitor() {
                @Override
                public void visit(HudNode ignored, String fieldPath, HudImageData image) {
                    validateImage(node, image, fieldPath, path);
                }
            });
        }

        private void validateImage(HudNode node, HudImageData image, String fieldPath,
                                   String path) {
            String imagePath = path + "." + fieldPath;
            String field = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
            String subject = node.kind == HudNodeKind.IMAGE
                    ? "IMAGE"
                    : "IMAGE_BUTTON " + field;
            if (image.source == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        subject + " source must be REGION or DRAWABLE.", usableId(node),
                        imagePath + ".source");
                return;
            }
            if (!isNonBlank(image.resourceName)) {
                add(HudValidationIssueCode.MISSING_RESOURCE_REFERENCE,
                        subject + " requires a nonblank logical resource name.", usableId(node),
                        imagePath + ".resourceName");
                return;
            }
            if (resources == null) return;

            boolean known = image.source == HudImageSource.REGION
                    ? resources.hasRegion(image.resourceName)
                    : resources.hasDrawable(image.resourceName);
            if (!known) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        subject + " references unknown " + image.source + " resource '"
                                + image.resourceName + "'.",
                        usableId(node), imagePath + ".resourceName");
            }
        }

        private void validateLabel(HudNode node, String path) {
            if (node.label.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "LABEL text must not be null; an empty string is allowed.",
                        usableId(node), path + ".label.text");
            }
            Integer fontAssetId = node.label.fontAssetId;
            if (fontAssetId != null && fontAssetId <= 0) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "LABEL fontAssetId must be a positive Asset ID.", usableId(node),
                        path + ".label.fontAssetId");
            } else if (fontAssetId != null && resources != null
                    && !resources.hasBitmapFont(fontAssetId)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "LABEL references unknown bitmap font Asset " + fontAssetId + ".",
                        usableId(node), path + ".label.fontAssetId");
            }
            validateLabelStyle(node, node.label.styleName, fontAssetId != null,
                    path + ".label.styleName");
        }

        private void validateTextButton(HudNode node, String path) {
            if (node.textButton.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXT_BUTTON text must not be null; an empty string is allowed.",
                        usableId(node), path + ".textButton.text");
            }
            validateStyle(node, node.textButton.styleName,
                    path + ".textButton.styleName");
        }

        private void validateImageButton(HudNode node, String path) {
            validateImageButtonStyle(node, node.imageButton.styleName, path + ".imageButton.styleName");
            validateImages(node, path);
        }

        private void validateTextField(HudNode node, String path) {
            if (node.textField.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXT_FIELD text must not be null; an empty string is allowed.",
                        usableId(node), path + ".textField.text");
            }
            if (node.textField.messageText == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXT_FIELD messageText must not be null; an empty string is allowed.",
                        usableId(node), path + ".textField.messageText");
            }
            if (node.textField.maxLength < 0) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXT_FIELD maxLength must be zero or a positive integer.",
                        usableId(node), path + ".textField.maxLength");
            }
            validateTextFieldStyle(node, node.textField.styleName,
                    path + ".textField.styleName");
        }

        private void validateSelectBox(HudNode node, String path) {
            if (node.selectBox.items == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SELECT_BOX items must be an ordered non-null list.", usableId(node),
                        path + ".selectBox.items");
            } else {
                Set<String> values = new HashSet<String>();
                for (int index = 0; index < node.selectBox.items.size(); index++) {
                    String item = node.selectBox.items.get(index);
                    if (item == null) {
                        add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                                "SELECT_BOX items must not contain null values.", usableId(node),
                                path + ".selectBox.items[" + index + "]");
                    } else if (!values.add(item)) {
                        add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                                "SELECT_BOX items must be distinct because native Scene2D selection uses value equality.",
                                usableId(node), path + ".selectBox.items[" + index + "]");
                    }
                }
                int minimum = node.selectBox.items.isEmpty() ? -1 : 0;
                if (node.selectBox.selectedIndex < minimum
                        || node.selectBox.selectedIndex >= node.selectBox.items.size()) {
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "SELECT_BOX selectedIndex must be -1 for an empty list or a valid item index.",
                            usableId(node), path + ".selectBox.selectedIndex");
                }
            }
            if (node.selectBox.maxListCount < 0) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SELECT_BOX maxListCount must be zero or a positive integer.",
                        usableId(node), path + ".selectBox.maxListCount");
            }
            validateSelectBoxStyle(node, node.selectBox.styleName,
                    path + ".selectBox.styleName");
        }

        private void validateCheckBox(HudNode node, String path) {
            if (node.checkBox.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "CHECK_BOX text must not be null; an empty string is allowed.",
                        usableId(node), path + ".checkBox.text");
            }
            validateCheckBoxStyle(node, node.checkBox.styleName, path + ".checkBox.styleName");
        }

        private void validateTextFieldStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInTextFieldStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInTextFieldStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "TEXT_FIELD requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasTextFieldStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "TEXT_FIELD Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateSelectBoxStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInSelectBoxStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInSelectBoxStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "SELECT_BOX requires the complete built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasSelectBoxStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "SELECT_BOX Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateCheckBoxStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInCheckBoxStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInCheckBoxStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "CHECK_BOX requires the complete built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasCheckBoxStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "CHECK_BOX Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateImageButtonStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInImageButtonStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInImageButtonStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "IMAGE_BUTTON requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasImageButtonStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "IMAGE_BUTTON references unknown Skin style '" + styleName + "'.",
                        usableId(node), path);
            }
        }

        private void validateLabelStyle(HudNode node, String styleName,
                                        boolean hasFontOverride, String path) {
            if (HudBuiltInLabelStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInLabelStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "LABEL requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (!isNonBlank(styleName)) {
                add(HudValidationIssueCode.MISSING_RESOURCE_REFERENCE,
                        "LABEL requires a nonblank Skin style name.", usableId(node), path);
                return;
            }
            if (resources == null) return;
            if (!resources.hasLabelStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "LABEL references unknown Skin style '" + styleName + "'.",
                        usableId(node), path);
            } else if (!hasFontOverride && !resources.hasLabelStyleFont(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "LABEL Skin style '" + styleName + "' has no usable font.",
                        usableId(node), path);
            }
        }

        private void validateStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInTextButtonStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInTextButtonStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "TEXT_BUTTON requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (!isNonBlank(styleName)) {
                add(HudValidationIssueCode.MISSING_RESOURCE_REFERENCE,
                        node.kind + " requires a nonblank Skin style name.",
                        usableId(node), path);
                return;
            }
            if (resources == null) return;
            boolean known = resources.hasTextButtonStyle(styleName);
            if (!known) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        node.kind + " references unknown Skin style '" + styleName + "'.",
                        usableId(node), path);
            }
        }

        private void validateDimensions(HudNode node, String path) {
            if (node.actor == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "HUD node requires Actor construction properties.",
                        usableId(node), path + ".actor");
                return;
            }
            nonNegativeFinite(node.actor.width, "width", node, path + ".actor.width",
                    HudValidationIssueCode.INVALID_DIMENSION);
            nonNegativeFinite(node.actor.height, "height", node, path + ".actor.height",
                    HudValidationIssueCode.INVALID_DIMENSION);
        }

        private void validateChildCount(HudNode node, String path) {
            if (node.children == null) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        "HUD node children must be an ordered non-null list.",
                        usableId(node), path + ".children");
                return;
            }
            int count = node.children.size();
            if (node.kind == HudNodeKind.CONTAINER && count > 1) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        "CONTAINER accepts at most one child, found " + count + ".",
                        usableId(node), path + ".children");
            } else if (isLeaf(node.kind) && count != 0) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        node.kind + " must not contain children.",
                        usableId(node), path + ".children");
            }
        }

        private void validatePlacement(HudNode parent, HudChild child, String path) {
            HudPlacementKind placement = child.placementKind;
            if (placement == null) {
                add(HudValidationIssueCode.INVALID_CHILD_PLACEMENT,
                        "HUD child placementKind is required.", childId(child),
                        path + ".placementKind");
            } else {
                validatePlacementPayload(child, placement, path);
                if (!parentAccepts(parent.kind, placement)) {
                    add(HudValidationIssueCode.INVALID_CHILD_PLACEMENT,
                            parentKind(parent) + " does not accept " + placement
                                    + " child placement.",
                            childId(child), path + ".placementKind");
                }
            }
        }

        private void validatePlacementPayload(HudChild child, HudPlacementKind placement,
                                              String path) {
            switch (placement) {
                case DIRECT:
                    if (child.cell != null || child.free != null) {
                        add(HudValidationIssueCode.INVALID_CHILD_PLACEMENT,
                                "DIRECT placement must not contain Cell or free-placement data.",
                                childId(child), path);
                    }
                    break;
                case CELL:
                    if (child.cell == null || child.free != null) {
                        add(HudValidationIssueCode.INVALID_CHILD_PLACEMENT,
                                "CELL placement requires Cell data and no free-placement data.",
                                childId(child), path);
                    }
                    if (child.cell != null) validateCell(child, path + ".cell");
                    break;
                case FREE:
                    if (child.free == null || child.cell != null) {
                        add(HudValidationIssueCode.INVALID_CHILD_PLACEMENT,
                                "FREE placement requires free-placement data and no Cell data.",
                                childId(child), path);
                    }
                    if (child.free != null) validateFree(child, path + ".free");
                    break;
                default:
                    break;
            }
        }

        private void validateCell(HudChild child, String path) {
            HudCellConstraints cell = child.cell;
            if (cell.horizontalAlign == null || cell.verticalAlign == null) {
                add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                        "Cell horizontal and vertical alignment are required.",
                        childId(child), path);
            }
            cellNumber(cell.minWidth, "minWidth", child, path + ".minWidth");
            cellNumber(cell.minHeight, "minHeight", child, path + ".minHeight");
            cellNumber(cell.prefWidth, "prefWidth", child, path + ".prefWidth");
            cellNumber(cell.prefHeight, "prefHeight", child, path + ".prefHeight");
            cellNumber(cell.padTop, "padTop", child, path + ".padTop");
            cellNumber(cell.padRight, "padRight", child, path + ".padRight");
            cellNumber(cell.padBottom, "padBottom", child, path + ".padBottom");
            cellNumber(cell.padLeft, "padLeft", child, path + ".padLeft");
        }

        private void validateFree(HudChild child, String path) {
            HudFreePlacement free = child.free;
            if (free.horizontalAnchor == null || free.verticalAnchor == null) {
                add(HudValidationIssueCode.INVALID_FREE_PLACEMENT,
                        "Free placement requires horizontal and vertical anchors.",
                        childId(child), path);
            }
            pivot(free.pivotX, "pivotX", child, path + ".pivotX");
            pivot(free.pivotY, "pivotY", child, path + ".pivotY");
            finite(free.offsetX, "offsetX", child, path + ".offsetX",
                    HudValidationIssueCode.INVALID_FREE_PLACEMENT);
            finite(free.offsetY, "offsetY", child, path + ".offsetY",
                    HudValidationIssueCode.INVALID_FREE_PLACEMENT);
        }

        private void cellNumber(Float value, String field, HudChild child, String path) {
            if (value == null) return;
            if (!isFinite(value) || value < 0f) {
                add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                        "Cell " + field + " must be finite and nonnegative.",
                        childId(child), path);
            }
        }

        private void pivot(float value, String field, HudChild child, String path) {
            if (!isFinite(value) || value < 0f || value > 1f) {
                add(HudValidationIssueCode.INVALID_FREE_PLACEMENT,
                        "Free-placement " + field + " must be finite and within [0,1].",
                        childId(child), path);
            }
        }

        private void finite(float value, String field, HudChild child, String path,
                            HudValidationIssueCode code) {
            if (!isFinite(value)) {
                add(code, "Free-placement " + field + " must be finite.",
                        childId(child), path);
            }
        }

        private void nonNegativeFinite(float value, String field, HudNode node, String path,
                                       HudValidationIssueCode code) {
            if (!isFinite(value) || value < 0f) {
                add(code, "Actor " + field + " must be finite and nonnegative.",
                        usableId(node), path);
            }
        }

        void add(HudValidationIssueCode code, String message, String nodeId, String path) {
            issues.add(new HudValidationIssue(code, message, nodeId, path));
        }

        private static int payloadCount(HudNode node) {
            int count = 0;
            if (node.container != null) count++;
            if (node.image != null) count++;
            if (node.label != null) count++;
            if (node.textButton != null) count++;
            if (node.imageButton != null) count++;
            if (node.textField != null) count++;
            if (node.selectBox != null) count++;
            if (node.checkBox != null) count++;
            return count;
        }

        private static boolean isLeaf(HudNodeKind kind) {
            return kind == HudNodeKind.IMAGE || kind == HudNodeKind.LABEL
                    || kind == HudNodeKind.TEXT_BUTTON || kind == HudNodeKind.IMAGE_BUTTON
                    || kind == HudNodeKind.TEXT_FIELD || kind == HudNodeKind.SELECT_BOX
                    || kind == HudNodeKind.CHECK_BOX;
        }

        private static boolean parentAccepts(HudNodeKind parent, HudPlacementKind placement) {
            if (parent == HudNodeKind.TABLE) return placement == HudPlacementKind.CELL;
            if (parent == HudNodeKind.STACK || parent == HudNodeKind.CONTAINER) {
                return placement == HudPlacementKind.DIRECT;
            }
            if (parent == HudNodeKind.GROUP) {
                return placement == HudPlacementKind.DIRECT
                        || placement == HudPlacementKind.FREE;
            }
            return false;
        }

        private static String parentKind(HudNode parent) {
            return parent.kind != null ? parent.kind.name() : "A node without a kind";
        }

        private static String childId(HudChild child) {
            return child != null ? usableId(child.node) : null;
        }

        private static String usableId(HudNode node) {
            return node != null && isNonBlank(node.id) ? node.id : null;
        }

        private static boolean isNonBlank(String value) {
            return value != null && value.trim().length() != 0;
        }

        private static boolean isFinite(float value) {
            return !Float.isNaN(value) && !Float.isInfinite(value);
        }
    }
}
