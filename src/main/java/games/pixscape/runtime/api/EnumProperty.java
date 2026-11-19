package games.pixscape.runtime.api;

import games.pixscape.runtime.property.PropertyType;

/** HIGH_LEVEL immutable enum value and its persisted definition; no source project required. */
public interface EnumProperty {
    String typeName();
    PropertyType storageType();
    boolean isFlags();
    int choiceCount();
    String choiceAt(int index);
    boolean isSelected(int index);
    /** Original STRING representation; throws for INTEGER storage. */
    String stringValue();
    /** Original INTEGER index or flags mask; throws for STRING storage. */
    int intValue();
}
