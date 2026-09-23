package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.runtime.hud.HudBuiltInImageButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInImageTextButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInTextButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInTextFieldStyle;
import games.pixscape.runtime.hud.HudBuiltInSelectBoxStyle;
import games.pixscape.runtime.hud.HudBuiltInCheckBoxStyle;
import games.pixscape.runtime.hud.HudBuiltInSliderStyle;
import games.pixscape.runtime.hud.HudBuiltInProgressBarStyle;
import games.pixscape.runtime.hud.HudBuiltInScrollPaneStyle;
import games.pixscape.runtime.hud.HudBuiltInWindowStyle;
import games.pixscape.runtime.hud.HudBuiltInTextTooltipStyle;

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

        state.root = document.root;
        state.visit(document.root, "$.root");
        state.validateWindowActions();
        return state.result(document);
    }

    private static final class ValidationState {
        private final HudResourceCatalog resources;
        private final List<HudValidationIssue> issues = new ArrayList<HudValidationIssue>();
        private final Map<String, HudNode> nodeIndex = new LinkedHashMap<String, HudNode>();
        private final Map<String, String> nodePaths = new LinkedHashMap<String, String>();
        private final Set<String> cellIds = new HashSet<String>();
        private final ObjectSet<HudNode> visiting = new ObjectSet<HudNode>();
        private final ObjectSet<HudNode> visited = new ObjectSet<HudNode>();
        private HudNode root;

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
            validateTooltip(node, path);
            validateDimensions(node, path);
            validateChildCount(node, path);
            if (!isTabular(node.kind) && node.table != null) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "Only TABLE, WINDOW, and DIALOG may declare explicit table data.",
                        usableId(node), path + ".table");
            }
            if (node == root && node.kind == HudNodeKind.DIALOG) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "DIALOG cannot be the HUD root; it requires an authored host and opener.",
                        usableId(node), path);
            }

            if (isTabular(node.kind)) {
                validateTableLayout(node, path);
                if (node.kind == HudNodeKind.DIALOG && node.dialog != null)
                    validateDialogButtons(node, path + ".dialog.resultButtons");
            } else if (node.children != null) {
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

        private void validateDialogButtons(HudNode dialog, String path) {
            if (dialog.dialog.resultButtons == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "Dialog resultButtons must be an ordered non-null list.", dialog.id, path);
                return;
            }
            Set<String> resultIds = new HashSet<String>();
            for (int i = 0; i < dialog.dialog.resultButtons.size(); i++) {
                HudDialogResultButton entry = dialog.dialog.resultButtons.get(i);
                String entryPath = path + "[" + i + "]";
                if (entry == null) {
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "Dialog result button entry must not be null.", dialog.id, entryPath);
                    continue;
                }
                if (!isNonBlank(entry.resultId) || !resultIds.add(entry.resultId))
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "Dialog resultId must be nonblank and unique within its Dialog.",
                            dialog.id, entryPath + ".resultId");
                if (entry.button == null) {
                    add(HudValidationIssueCode.INVALID_HIERARCHY,
                            "Dialog result button requires an authored button node.", dialog.id,
                            entryPath + ".button");
                    continue;
                }
                HudNodeKind kind = entry.button.kind;
                if (kind != HudNodeKind.TEXT_BUTTON && kind != HudNodeKind.IMAGE_BUTTON
                        && kind != HudNodeKind.IMAGE_TEXT_BUTTON)
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "Dialog result requires a TextButton, ImageButton, or ImageTextButton.",
                            usableId(entry.button), entryPath + ".button.kind");
                if (entry.button.windowActions != null && !entry.button.windowActions.isEmpty())
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "Dialog result button cannot also own Window/Dialog actions.",
                            usableId(entry.button), entryPath + ".button.windowActions");
                visit(entry.button, entryPath + ".button");
            }
        }

        private void validateTableLayout(HudNode node, String path) {
            if (node.children == null || !node.children.isEmpty()) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        "A tabular HUD parent must use explicit table data and no generic children.",
                        usableId(node), path + ".children");
            }
            HudTableLayout table = node.table;
            if (table == null || table.rows == null || table.columns < 1 || table.rows.isEmpty()) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        "A tabular HUD parent requires at least one explicit row and column.",
                        usableId(node), path + ".table");
                return;
            }
            for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
                HudTableRow row = table.rows.get(rowIndex);
                String rowPath = path + ".table.rows[" + rowIndex + "]";
                if (row == null || row.cells == null || row.cells.isEmpty()) {
                    add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                            "Every explicit table row requires cells.", usableId(node), rowPath);
                    continue;
                }
                int coveredColumns = 0;
                for (int cellIndex = 0; cellIndex < row.cells.size(); cellIndex++) {
                    HudTableCell cell = row.cells.get(cellIndex);
                    String cellPath = rowPath + ".cells[" + cellIndex + "]";
                    if (cell == null) {
                        add(HudValidationIssueCode.INVALID_HIERARCHY,
                                "A table row must not contain a null cell.", usableId(node), cellPath);
                        continue;
                    }
                    if (!isNonBlank(cell.id)) {
                        add(HudValidationIssueCode.MISSING_NODE_ID,
                                "Every HUD table cell requires a nonblank document-wide ID.",
                                usableId(node), cellPath + ".id");
                    } else if (nodeIndex.containsKey(cell.id) || !cellIds.add(cell.id)) {
                        add(HudValidationIssueCode.DUPLICATE_NODE_ID,
                                "HUD table cell ID '" + cell.id + "' is duplicated document-wide.",
                                cell.id, cellPath + ".id");
                    }
                    if (cell.colspan < 1) {
                        add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                                "Cell colspan must be strictly positive.", cell.id, cellPath + ".colspan");
                    } else {
                        coveredColumns += cell.colspan;
                    }
                    if (cell.constraints == null) {
                        add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                                "Every explicit table cell requires constraints.", cell.id,
                                cellPath + ".constraints");
                    } else {
                        validateCellConstraints(cell.constraints, cell.id, cellPath + ".constraints");
                    }
                    if (cell.content != null) visit(cell.content, cellPath + ".content");
                }
                if (coveredColumns != table.columns) {
                    add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                            "Explicit table row covers " + coveredColumns + " columns; expected "
                                    + table.columns + ".", usableId(node), rowPath);
                }
            }
        }

        private void validateIdentity(HudNode node, String path) {
            if (!isNonBlank(node.id)) {
                add(HudValidationIssueCode.MISSING_NODE_ID,
                        "Every HUD node requires a nonblank document-wide ID.", null,
                        path + ".id");
                return;
            }
            HudNode previous = nodeIndex.get(node.id);
            if (previous != null || cellIds.contains(node.id)) {
                add(HudValidationIssueCode.DUPLICATE_NODE_ID,
                        "HUD node ID '" + node.id + "' is duplicated document-wide.",
                        node.id, path + ".id");
                return;
            }
            nodeIndex.put(node.id, node);
            nodePaths.put(node.id, path);
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
                case SCROLL_PANE:
                    validPayload = node.scrollPane != null && payloadCount == 1;
                    break;
                case WINDOW:
                    validPayload = node.window != null && payloadCount == 1;
                    break;
                case DIALOG:
                    validPayload = node.dialog != null && payloadCount == 1;
                    break;
                case IMAGE:
                    validPayload = node.image != null && payloadCount == 1;
                    break;
                case LABEL:
                    validPayload = node.label != null && payloadCount == 1;
                    break;
                case TEXTRA_LABEL:
                    validPayload = node.textraLabel != null && payloadCount == 1;
                    break;
                case TEXT_BUTTON:
                    validPayload = node.textButton != null && payloadCount == 1;
                    break;
                case IMAGE_BUTTON:
                    validPayload = node.imageButton != null && payloadCount == 1;
                    break;
                case IMAGE_TEXT_BUTTON:
                    validPayload = node.imageTextButton != null && payloadCount == 1;
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
                case SLIDER:
                    validPayload = node.slider != null && payloadCount == 1;
                    break;
                case PROGRESS_BAR:
                    validPayload = node.progressBar != null && payloadCount == 1;
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
            } else if (node.kind == HudNodeKind.TEXTRA_LABEL && node.textraLabel != null) {
                validateTextraLabel(node, path);
            } else if (node.kind == HudNodeKind.TEXT_BUTTON && node.textButton != null) {
                validateTextButton(node, path);
            } else if (node.kind == HudNodeKind.IMAGE_BUTTON && node.imageButton != null) {
                validateImageButton(node, path);
            } else if (node.kind == HudNodeKind.IMAGE_TEXT_BUTTON && node.imageTextButton != null) {
                validateImageTextButton(node, path);
            } else if (node.kind == HudNodeKind.TEXT_FIELD && node.textField != null) {
                validateTextField(node, path);
            } else if (node.kind == HudNodeKind.SELECT_BOX && node.selectBox != null) {
                validateSelectBox(node, path);
            } else if (node.kind == HudNodeKind.CHECK_BOX && node.checkBox != null) {
                validateCheckBox(node, path);
            } else if (node.kind == HudNodeKind.SLIDER && node.slider != null) {
                validateSlider(node, path);
            } else if (node.kind == HudNodeKind.PROGRESS_BAR && node.progressBar != null) {
                validateProgressBar(node, path);
            } else if (node.kind == HudNodeKind.SCROLL_PANE && node.scrollPane != null) {
                validateScrollPane(node, path);
            } else if (node.kind == HudNodeKind.WINDOW && node.window != null) {
                validateWindow(node, node.window, path + ".window");
            } else if (node.kind == HudNodeKind.DIALOG && node.dialog != null) {
                validateWindow(node, node.dialog, path + ".dialog");
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
            String subject = node.kind == HudNodeKind.IMAGE ? "IMAGE" : node.kind + " " + field;
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
            Integer fontAssetId = validateFontAsset(node, node.label.fontAssetId,
                    path + ".label.fontAssetId");
            validateLabelStyle(node, node.label.styleName, fontAssetId != null,
                    path + ".label.styleName");
        }

        private void validateTextraLabel(HudNode node, String path) {
            if (node.textraLabel.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXTRA_LABEL text must not be null; an empty string is allowed.",
                        usableId(node), path + ".textraLabel.text");
            }
            Integer fontAssetId = validateFontAsset(node, node.textraLabel.fontAssetId,
                    path + ".textraLabel.fontAssetId");
            validateLabelStyle(node, node.textraLabel.styleName, fontAssetId != null,
                    path + ".textraLabel.styleName");
        }

        private void validateTextButton(HudNode node, String path) {
            if (node.textButton.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "TEXT_BUTTON text must not be null; an empty string is allowed.",
                        usableId(node), path + ".textButton.text");
            }
            Integer fontAssetId = validateFontAsset(node, node.textButton.fontAssetId,
                    path + ".textButton.fontAssetId");
            validateTextButtonStyle(node, node.textButton.styleName, fontAssetId != null,
                    path + ".textButton.styleName");
        }

        private void validateImageButton(HudNode node, String path) {
            validateImageButtonStyle(node, node.imageButton.styleName, path + ".imageButton.styleName");
            validateImages(node, path);
        }

        private void validateImageTextButton(HudNode node, String path) {
            if (node.imageTextButton.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "IMAGE_TEXT_BUTTON text must not be null; an empty string is allowed.",
                        usableId(node), path + ".imageTextButton.text");
            }
            Integer fontAssetId = validateFontAsset(node, node.imageTextButton.fontAssetId,
                    path + ".imageTextButton.fontAssetId");
            validateImageTextButtonStyle(node, node.imageTextButton.styleName, fontAssetId != null,
                    path + ".imageTextButton.styleName");
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
            Integer fontAssetId = validateFontAsset(node, node.textField.fontAssetId,
                    path + ".textField.fontAssetId");
            validateTextFieldStyle(node, node.textField.styleName, fontAssetId != null,
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
            Integer fontAssetId = validateFontAsset(node, node.selectBox.fontAssetId,
                    path + ".selectBox.fontAssetId");
            validateSelectBoxStyle(node, node.selectBox.styleName, fontAssetId != null,
                    path + ".selectBox.styleName");
        }

        private void validateCheckBox(HudNode node, String path) {
            if (node.checkBox.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "CHECK_BOX text must not be null; an empty string is allowed.",
                        usableId(node), path + ".checkBox.text");
            }
            Integer fontAssetId = validateFontAsset(node, node.checkBox.fontAssetId,
                    path + ".checkBox.fontAssetId");
            validateCheckBoxStyle(node, node.checkBox.styleName, fontAssetId != null,
                    path + ".checkBox.styleName");
        }

        private void validateSlider(HudNode node, String path) {
            HudSliderData slider = node.slider;
            if (slider.orientation == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER orientation must be HORIZONTAL or VERTICAL.",
                        usableId(node), path + ".slider.orientation");
            }
            if (!isFinite(slider.min)) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER min must be finite.", usableId(node), path + ".slider.min");
            }
            if (!isFinite(slider.max)) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER max must be finite.", usableId(node), path + ".slider.max");
            }
            if (isFinite(slider.min) && isFinite(slider.max) && slider.min > slider.max) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER min must be less than or equal to max.",
                        usableId(node), path + ".slider.min");
            }
            if (!isFinite(slider.stepSize) || slider.stepSize <= 0f) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER stepSize must be finite and greater than zero.",
                        usableId(node), path + ".slider.stepSize");
            }
            if (!isFinite(slider.value) || slider.value < slider.min || slider.value > slider.max) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "SLIDER value must be finite and within its authored range.",
                        usableId(node), path + ".slider.value");
            }
            validateSliderStyle(node, slider.styleName, path + ".slider.styleName");
        }

        private void validateProgressBar(HudNode node, String path) {
            HudProgressBarData progressBar = node.progressBar;
            if (progressBar.orientation == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "PROGRESS_BAR orientation must be HORIZONTAL or VERTICAL.",
                        usableId(node), path + ".progressBar.orientation");
            }
            if (!isFinite(progressBar.min)) add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                    "PROGRESS_BAR min must be finite.", usableId(node), path + ".progressBar.min");
            if (!isFinite(progressBar.max)) add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                    "PROGRESS_BAR max must be finite.", usableId(node), path + ".progressBar.max");
            if (isFinite(progressBar.min) && isFinite(progressBar.max) && progressBar.min > progressBar.max) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "PROGRESS_BAR min must be less than or equal to max.",
                        usableId(node), path + ".progressBar.min");
            }
            if (!isFinite(progressBar.stepSize) || progressBar.stepSize <= 0f) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "PROGRESS_BAR stepSize must be finite and greater than zero.",
                        usableId(node), path + ".progressBar.stepSize");
            }
            if (!isFinite(progressBar.value) || progressBar.value < progressBar.min || progressBar.value > progressBar.max) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "PROGRESS_BAR value must be finite and within its authored range.",
                        usableId(node), path + ".progressBar.value");
            }
            validateProgressBarStyle(node, progressBar.styleName, path + ".progressBar.styleName");
        }

        private void validateScrollPane(HudNode node, String path) {
            validateScrollPaneStyle(node, node.scrollPane.styleName, path + ".scrollPane.styleName");
        }

        private void validateWindow(HudNode node, HudWindowData data, String path) {
            if (data.title == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        node.kind + " title must not be null; an empty title is allowed.",
                        usableId(node), path + ".title");
            }
            Integer fontAssetId = validateFontAsset(node, data.fontAssetId,
                    path + ".fontAssetId");
            String styleName = data.styleName;
            if (HudBuiltInWindowStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInWindowStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            node.kind + " requires the built-in Default style, but it is unavailable.",
                            usableId(node), path + ".styleName");
                }
            } else if (resources != null && !resources.hasWindowStyle(styleName,
                    fontAssetId != null)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        node.kind + " Skin style '" + styleName + "' is missing or unusable.",
                        usableId(node), path + ".styleName");
            }
        }

        private Integer validateFontAsset(HudNode node, Integer fontAssetId, String path) {
            if (fontAssetId != null && fontAssetId <= 0) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        node.kind + " fontAssetId must be a positive Asset ID.",
                        usableId(node), path);
            } else if (fontAssetId != null && resources != null
                    && !resources.hasBitmapFont(fontAssetId)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        node.kind + " references unknown bitmap font Asset " + fontAssetId + ".",
                        usableId(node), path);
            }
            return fontAssetId;
        }

        private void validateTooltip(HudNode node, String path) {
            if (node.tooltip == null) return;
            String tooltipPath = path + ".tooltip";
            if (node.tooltip.text == null) {
                add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                        "Tooltip text must not be null; an empty string is allowed.",
                        usableId(node), tooltipPath + ".text");
            }
            Integer fontAssetId = validateFontAsset(node, node.tooltip.fontAssetId,
                    tooltipPath + ".fontAssetId");
            String styleName = node.tooltip.styleName;
            if (HudBuiltInTextTooltipStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInTextTooltipStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "Tooltip requires the built-in Default style, but it is unavailable.",
                            usableId(node), tooltipPath + ".styleName");
                }
            } else if (resources != null && !resources.hasTextTooltipStyle(styleName,
                    fontAssetId != null)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "Tooltip Skin style '" + styleName + "' is missing or unusable.",
                        usableId(node), tooltipPath + ".styleName");
            }
        }

        private void validateTextFieldStyle(HudNode node, String styleName,
                                            boolean hasFontOverride, String path) {
            if (HudBuiltInTextFieldStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInTextFieldStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "TEXT_FIELD requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasTextFieldStyle(styleName, hasFontOverride)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "TEXT_FIELD Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateSelectBoxStyle(HudNode node, String styleName,
                                            boolean hasFontOverride, String path) {
            if (HudBuiltInSelectBoxStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInSelectBoxStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "SELECT_BOX requires the complete built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasSelectBoxStyle(styleName, hasFontOverride)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "SELECT_BOX Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateCheckBoxStyle(HudNode node, String styleName,
                                           boolean hasFontOverride, String path) {
            if (HudBuiltInCheckBoxStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInCheckBoxStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "CHECK_BOX requires the complete built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasCheckBoxStyle(styleName, hasFontOverride)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "CHECK_BOX Skin style '" + styleName
                                + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateSliderStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInSliderStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInSliderStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "SLIDER requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasSliderStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "SLIDER Skin style '" + styleName + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateProgressBarStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInProgressBarStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInProgressBarStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "PROGRESS_BAR requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (resources != null && !resources.hasProgressBarStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "PROGRESS_BAR Skin style '" + styleName + "' is missing or unusable.",
                        usableId(node), path);
            }
        }

        private void validateScrollPaneStyle(HudNode node, String styleName, String path) {
            if (HudBuiltInScrollPaneStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInScrollPaneStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "SCROLL_PANE requires built-in Default style resources.", usableId(node), path);
                }
            } else if (resources != null && !resources.hasScrollPaneStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "SCROLL_PANE references unknown or unusable style '" + styleName + "'.",
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

        private void validateImageTextButtonStyle(HudNode node, String styleName,
                                                  boolean hasFontOverride, String path) {
            if (HudBuiltInImageTextButtonStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInImageTextButtonStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            "IMAGE_TEXT_BUTTON requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (!isNonBlank(styleName)) {
                add(HudValidationIssueCode.MISSING_RESOURCE_REFERENCE,
                        "IMAGE_TEXT_BUTTON requires a nonblank Skin style name.", usableId(node), path);
                return;
            }
            if (resources != null && !resources.hasImageTextButtonStyle(styleName, hasFontOverride)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        "IMAGE_TEXT_BUTTON references unknown Skin style '" + styleName + "'.",
                        usableId(node), path);
            }
        }

        private void validateLabelStyle(HudNode node, String styleName,
                                        boolean hasFontOverride, String path) {
            if (HudBuiltInLabelStyle.isSelected(styleName)) {
                if (resources != null && !resources.hasBuiltInLabelStyle()) {
                    add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                            node.kind + " requires the built-in Default style, but it is unavailable.",
                            usableId(node), path);
                }
                return;
            }
            if (!isNonBlank(styleName)) {
                add(HudValidationIssueCode.MISSING_RESOURCE_REFERENCE,
                        node.kind + " requires a nonblank Skin style name.", usableId(node), path);
                return;
            }
            if (resources == null) return;
            if (!resources.hasLabelStyle(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        node.kind + " references unknown Skin style '" + styleName + "'.",
                        usableId(node), path);
            } else if (!hasFontOverride && !resources.hasLabelStyleFont(styleName)) {
                add(HudValidationIssueCode.UNKNOWN_RESOURCE_REFERENCE,
                        node.kind + " Skin style '" + styleName + "' has no usable font.",
                        usableId(node), path);
            }
        }

        private void validateTextButtonStyle(HudNode node, String styleName,
                                             boolean hasFontOverride, String path) {
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
            boolean known = resources.hasTextButtonStyle(styleName, hasFontOverride);
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
            if (isTabular(node.kind)) return;
            if (node.children == null) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        "HUD node children must be an ordered non-null list.",
                        usableId(node), path + ".children");
                return;
            }
            int count = node.children.size();
            if ((node.kind == HudNodeKind.CONTAINER || node.kind == HudNodeKind.SCROLL_PANE) && count > 1) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        node.kind + " accepts at most one child, found " + count + ".",
                        usableId(node), path + ".children");
            } else if (isLeaf(node.kind) && count != 0) {
                add(HudValidationIssueCode.INVALID_CHILD_COUNT,
                        node.kind + " must not contain children.",
                        usableId(node), path + ".children");
            }
        }

        private void validatePlacement(HudNode parent, HudChild child, String path) {
            if (child.node != null && child.node.kind == HudNodeKind.DIALOG
                    && child.node.dialog != null && child.node.dialog.modal
                    && (parent != root || parent.kind != HudNodeKind.GROUP)) {
                add(HudValidationIssueCode.INVALID_HIERARCHY,
                        "A modal DIALOG must be a direct child of a GROUP HUD root; other dialogs must be nonmodal.",
                        childId(child), path + ".node.dialog.modal");
            }
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
            validateCellConstraints(child.cell, childId(child), path);
        }

        private void validateCellConstraints(HudCellConstraints cell, String ownerId, String path) {
            if (cell.horizontalAlign == null || cell.verticalAlign == null) {
                add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                        "Cell horizontal and vertical alignment are required.",
                        ownerId, path);
            }
            cellNumber(cell.minWidth, "minWidth", ownerId, path + ".minWidth");
            cellNumber(cell.minHeight, "minHeight", ownerId, path + ".minHeight");
            cellNumber(cell.prefWidth, "prefWidth", ownerId, path + ".prefWidth");
            cellNumber(cell.prefHeight, "prefHeight", ownerId, path + ".prefHeight");
            cellNumber(cell.padTop, "padTop", ownerId, path + ".padTop");
            cellNumber(cell.padRight, "padRight", ownerId, path + ".padRight");
            cellNumber(cell.padBottom, "padBottom", ownerId, path + ".padBottom");
            cellNumber(cell.padLeft, "padLeft", ownerId, path + ".padLeft");
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

        private void cellNumber(Float value, String field, String ownerId, String path) {
            if (value == null) return;
            if (!isFinite(value) || value < 0f) {
                add(HudValidationIssueCode.INVALID_CELL_CONSTRAINTS,
                        "Cell " + field + " must be finite and nonnegative.",
                        ownerId, path);
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

        void validateWindowActions() {
            for (HudNode node : nodeIndex.values()) {
                String actionsPath = nodePaths.get(node.id) + ".windowActions";
                if (node.windowActions == null) {
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "windowActions must be a non-null list.", usableId(node),
                            actionsPath);
                    continue;
                }
                if (!node.windowActions.isEmpty() && node.kind != HudNodeKind.TEXT_BUTTON
                        && node.kind != HudNodeKind.IMAGE_BUTTON
                        && node.kind != HudNodeKind.IMAGE_TEXT_BUTTON) {
                    add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                            "Only buttons can own windowActions.", usableId(node),
                            actionsPath);
                }
                Set<String> targets = new HashSet<String>();
                for (int i = 0; i < node.windowActions.size(); i++) {
                    HudWindowAction action = node.windowActions.get(i);
                    String path = actionsPath + "[" + i + "]";
                    if (action == null || action.action == null || !isNonBlank(action.targetId)) {
                        add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                                "Window action requires a target ID and SHOW, HIDE, or TOGGLE.",
                                usableId(node), path);
                        continue;
                    }
                    HudNode target = nodeIndex.get(action.targetId);
                    if (target == null || (target.kind != HudNodeKind.WINDOW
                            && target.kind != HudNodeKind.DIALOG)) {
                        add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                                "Window action target must identify an authored WINDOW or DIALOG.",
                                usableId(node), path + ".targetId");
                    }
                    if (!targets.add(action.targetId)) {
                        add(HudValidationIssueCode.INVALID_NODE_PAYLOAD,
                                "A button may target each WINDOW or DIALOG only once.",
                                usableId(node), path + ".targetId");
                    }
                }
            }
        }

        private static int payloadCount(HudNode node) {
            int count = 0;
            if (node.container != null) count++;
            if (node.scrollPane != null) count++;
            if (node.window != null) count++;
            if (node.dialog != null) count++;
            if (node.image != null) count++;
            if (node.label != null) count++;
            if (node.textraLabel != null) count++;
            if (node.textButton != null) count++;
            if (node.imageButton != null) count++;
            if (node.imageTextButton != null) count++;
            if (node.textField != null) count++;
            if (node.selectBox != null) count++;
            if (node.checkBox != null) count++;
            if (node.slider != null) count++;
            if (node.progressBar != null) count++;
            return count;
        }

        private static boolean isLeaf(HudNodeKind kind) {
            return kind == HudNodeKind.IMAGE || kind == HudNodeKind.LABEL
                    || kind == HudNodeKind.TEXTRA_LABEL
                    || kind == HudNodeKind.TEXT_BUTTON || kind == HudNodeKind.IMAGE_BUTTON
                    || kind == HudNodeKind.IMAGE_TEXT_BUTTON
                    || kind == HudNodeKind.TEXT_FIELD || kind == HudNodeKind.SELECT_BOX
                    || kind == HudNodeKind.CHECK_BOX || kind == HudNodeKind.SLIDER
                    || kind == HudNodeKind.PROGRESS_BAR;
        }

        private static boolean parentAccepts(HudNodeKind parent, HudPlacementKind placement) {
            if (isTabular(parent)) return false;
            if (parent == HudNodeKind.STACK || parent == HudNodeKind.CONTAINER
                    || parent == HudNodeKind.SCROLL_PANE) {
                return placement == HudPlacementKind.DIRECT;
            }
            if (parent == HudNodeKind.GROUP) {
                return placement == HudPlacementKind.DIRECT
                        || placement == HudPlacementKind.FREE;
            }
            return false;
        }

        private static boolean isTabular(HudNodeKind kind) {
            return kind == HudNodeKind.TABLE || kind == HudNodeKind.WINDOW
                    || kind == HudNodeKind.DIALOG;
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
