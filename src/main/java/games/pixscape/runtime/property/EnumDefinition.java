package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.ObjectSet;

/** SUPPORTED_EXPERT immutable definition embedded with enum values for portable editing. */
public final class EnumDefinition {
    private String name = "";
    private PropertyType storageType = PropertyType.STRING;
    private boolean flags;
    private String[] choices = new String[0];

    public EnumDefinition() {
    }

    public EnumDefinition(String name, PropertyType storageType, boolean flags, String... choices) {
        this.name = name;
        this.storageType = storageType;
        this.flags = flags;
        this.choices = choices == null ? null : new String[choices.length];
        if (choices != null) {
            System.arraycopy(choices, 0, this.choices, 0, choices.length);
        }
        validate();
    }

    public String name() {
        return name;
    }
    public PropertyType storageType() {
        return storageType;
    }
    public boolean isFlags() {
        return flags;
    }
    public int size() {
        return choices.length;
    }
    public String choiceAt(int index) {
        return choices[index];
    }

    public EnumDefinition copy() {
        return new EnumDefinition(name, storageType, flags, choices);
    }

    void validate() {
        if (name == null || name.trim().isEmpty() || choices == null
                || (storageType != PropertyType.STRING && storageType != PropertyType.INTEGER)) {
            throw new IllegalArgumentException("Enum requires a type name, choices and STRING or INTEGER storage.");
        }
        // Tiled encodes flags using a positive signed 32-bit integer (31 flags).
        if (flags && choices.length > 31) {
            throw new IllegalArgumentException("Tiled enums support at most 31 flags.");
        }
        ObjectSet<String> seen = new ObjectSet<String>();
        for (String choice : choices) {
            if (choice == null || !seen.add(choice) || (flags && (choice.isEmpty() || choice.indexOf(',') >= 0))) {
                throw new IllegalArgumentException("Enum '" + name + "' has ambiguous or invalid choices.");
            }
        }
    }

    int selection(String text, int number) {
        if (storageType == PropertyType.INTEGER) {
            if (flags ? number < 0 || (choices.length < 31 && (number >>> choices.length) != 0)
                    : number < 0 || number >= choices.length) {
                throw new IllegalArgumentException("Unknown enum value " + number + " for '" + name + "'.");
            }
            return number;
        }
        if (text == null) {
            throw new IllegalArgumentException("Enum string value is required.");
        }
        if (!flags) {
            for (int i = 0; i < choices.length; i++) {
                if (choices[i].equals(text)) {
                    return i;
                }
            }
        } else {
            if (text.isEmpty()) {
                return 0;
            }
            int mask = 0;
            for (String part : text.split(",", -1)) {
                boolean found = false;
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i].equals(part)) {
                        mask |= 1 << i;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException("Unknown enum flag '" + part + "' for '" + name + "'.");
                }
            }
            return mask;
        }
        throw new IllegalArgumentException("Unknown enum value '" + text + "' for '" + name + "'.");
    }
}
