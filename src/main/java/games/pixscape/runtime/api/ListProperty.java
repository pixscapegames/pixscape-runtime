package games.pixscape.runtime.api;

import games.pixscape.runtime.property.PropertyType;

/**
 * HIGH_LEVEL read-only ordered, individually typed values. Access is O(1), without
 * copying the list. Index outside [0, size) throws IndexOutOfBoundsException;
 * a wrong typed accessor throws IllegalStateException. OBJECT -1 means no target;
 * positive IDs must be resolved through EntitiesAPI, whose handle may be unavailable.
 * Like ClassProperty, this borrowed view belongs to the captured value: reacquire
 * it after replacement of the owning property or component.
 */
public interface ListProperty {
    int size();
    PropertyType typeAt(int index);
    String getString(int index);
    boolean getBoolean(int index);
    int getInt(int index);
    float getFloat(int index);
    double getDouble(int index);
    int getColorRgba8888(int index);
    int getObjectStableId(int index);
    ClassProperty getClassValue(int index);
    ListProperty getListValue(int index);
    EnumProperty getEnumValue(int index);
    ImportedProperty getImportedValue(int index);
}
