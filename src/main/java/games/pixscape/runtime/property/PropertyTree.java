package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.ObjectMap;

/** Cold model operations. Explicit stacks keep valid nesting independent of the Java stack. */
final class PropertyTree {
    private PropertyTree() {
    }

    static void validate(Object root) {
        validate(root, null);
    }

    static void validate(Object root, PropertySet.ObjectReferenceVisitor visitor) {
        Array<Visit> pending = new Array<Visit>();
        IdentityMap<Object, Boolean> active = new IdentityMap<Object, Boolean>();
        IdentityMap<Object, Boolean> complete = new IdentityMap<Object, Boolean>();
        pending.add(new Visit(root, false));
        while (pending.size > 0) {
            Visit visit = pending.pop();
            Object node = visit.node;
            if (node == null) {
                throw new IllegalStateException("Property tree must not contain null values.");
            }
            if (visit.exit) {
                active.remove(node);
                complete.put(node, Boolean.TRUE);
                continue;
            }
            if (active.containsKey(node)) {
                throw new IllegalStateException("Custom properties must not contain cycles.");
            }
            if (complete.containsKey(node)) {
                continue;
            }
            active.put(node, Boolean.TRUE);
            pending.add(new Visit(node, true));
            if (node instanceof PropertySet) {
                PropertySet set = (PropertySet) node;
                if (set.values == null) {
                    throw new IllegalStateException("PropertySet backing map must not be null.");
                }
                for (ObjectMap.Entry<String, PropertyValue> entry : set.values) {
                    PropertySet.requireName(entry.key);
                    if (entry.value == null) {
                        throw new IllegalStateException("Property '" + entry.key + "' must not have a null value.");
                    }
                    pending.add(new Visit(entry.value, false));
                }
            } else {
                PropertyValue value = (PropertyValue) node;
                value.validateShallow();
                if (value.type == PropertyType.OBJECT && visitor != null) {
                    visitor.visit(value.integerValue);
                }
                if (value.type == PropertyType.CLASS) {
                    pending.add(new Visit(value.classProperties, false));
                } else if (value.type == PropertyType.LIST) {
                    for (int i = value.listValues.size - 1; i >= 0; i--) {
                        if (value.listValues.get(i) == null) {
                            throw new IllegalStateException("List item [" + i + "] must not be null.");
                        }
                        pending.add(new Visit(value.listValues.get(i), false));
                    }
                }
            }
        }
    }

    static Object copy(Object root, PropertySet.ObjectReferenceMapper mapper) {
        validate(root);
        Object result = root instanceof PropertySet ? new PropertySet(((PropertySet) root).size()) : new PropertyValue();
        Array<Pair> pending = new Array<Pair>();
        pending.add(new Pair(root, result));
        while (pending.size > 0) {
            Pair pair = pending.pop();
            if (pair.source instanceof PropertySet) {
                PropertySet from = (PropertySet) pair.source;
                PropertySet to = (PropertySet) pair.target;
                for (ObjectMap.Entry<String, PropertyValue> entry : from.values) {
                    PropertyValue copied = new PropertyValue();
                    to.values.put(entry.key, copied);
                    pending.add(new Pair(entry.value, copied));
                }
            } else {
                PropertyValue from = (PropertyValue) pair.source;
                PropertyValue to = (PropertyValue) pair.target;
                to.type = from.type;
                to.stringValue = from.stringValue;
                to.booleanValue = from.booleanValue;
                to.integerValue = from.integerValue;
                to.floatValue = from.floatValue;
                to.className = from.className;
                to.importedType = from.importedType;
                to.importedCustomType = from.importedCustomType;
                if (from.type == PropertyType.OBJECT && mapper != null) {
                    to.integerValue = mapper.map(from.integerValue);
                    to.validateShallow();
                } else if (from.type == PropertyType.CLASS) {
                    to.classProperties = new PropertySet(from.classProperties.size());
                    pending.add(new Pair(from.classProperties, to.classProperties));
                } else if (from.type == PropertyType.LIST) {
                    to.listValues = new Array<PropertyValue>(from.listValues.size);
                    for (int i = 0; i < from.listValues.size; i++) {
                        PropertyValue child = new PropertyValue();
                        to.listValues.add(child);
                        pending.add(new Pair(from.listValues.get(i), child));
                    }
                } else if (from.type == PropertyType.ENUM) {
                    to.enumDefinition = from.enumDefinition.copy();
                    to.enumSelection = from.enumSelection;
                }
            }
        }
        return result;
    }

    static boolean same(Object first, Object second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        validate(first);
        validate(second);
        Array<Pair> pending = new Array<Pair>();
        pending.add(new Pair(first, second));
        while (pending.size > 0) {
            Pair pair = pending.pop();
            if (pair.source instanceof PropertySet) {
                PropertySet a = (PropertySet) pair.source;
                PropertySet b = (PropertySet) pair.target;
                if (a.size() != b.size()) {
                    return false;
                }
                for (ObjectMap.Entry<String, PropertyValue> entry : a.values) {
                    PropertyValue other = b.values.get(entry.key);
                    if (other == null) {
                        return false;
                    }
                    pending.add(new Pair(entry.value, other));
                }
            } else {
                PropertyValue a = (PropertyValue) pair.source;
                PropertyValue b = (PropertyValue) pair.target;
                if (a.type != b.type) {
                    return false;
                }
                switch (a.type) {
                    case STRING:
                        if (!a.stringValue.equals(b.stringValue)) {
                            return false;
                        }
                        break;
                    case BOOLEAN:
                        if (a.booleanValue != b.booleanValue) {
                            return false;
                        }
                        break;
                    case INTEGER:
                    case COLOR:
                    case OBJECT:
                        if (a.integerValue != b.integerValue) {
                            return false;
                        }
                        break;
                    case FLOAT:
                        if (Double.compare(a.floatValue, b.floatValue) != 0) {
                            return false;
                        }
                        break;
                    case CLASS:
                        if (!a.className.equals(b.className)) {
                            return false;
                        }
                        pending.add(new Pair(a.classProperties, b.classProperties));
                        break;
                    case LIST:
                        if (a.listValues.size != b.listValues.size) {
                            return false;
                        }
                        for (int i = 0; i < a.listValues.size; i++) {
                            pending.add(new Pair(a.listValues.get(i), b.listValues.get(i)));
                        }
                        break;
                    case ENUM:
                        EnumDefinition ad = a.enumDefinition;
                        EnumDefinition bd = b.enumDefinition;
                        if (!ad.name().equals(bd.name()) || ad.storageType() != bd.storageType()
                                || ad.isFlags() != bd.isFlags() || ad.size() != bd.size()) {
                            return false;
                        }
                        for (int i = 0; i < ad.size(); i++) {
                            if (!ad.choiceAt(i).equals(bd.choiceAt(i))) {
                                return false;
                            }
                        }
                        if (ad.storageType() == PropertyType.STRING
                                ? !a.stringValue.equals(b.stringValue) : a.integerValue != b.integerValue) {
                            return false;
                        }
                        break;
                    case IMPORTED:
                        if (!a.importedType.equals(b.importedType) || !a.importedCustomType.equals(b.importedCustomType)
                                || !a.stringValue.equals(b.stringValue)) {
                            return false;
                        }
                        break;
                }
            }
        }
        return true;
    }

    static void compact(PropertySet root) {
        validate(root);
        Array<Object> pending = new Array<Object>();
        pending.add(root);
        while (pending.size > 0) {
            Object node = pending.pop();
            if (node instanceof PropertySet) {
                PropertySet set = (PropertySet) node;
                set.values.shrink(set.size());
                for (ObjectMap.Entry<String, PropertyValue> entry : set.values) {
                    pending.add(entry.value);
                }
            } else {
                PropertyValue value = (PropertyValue) node;
                if (value.type == PropertyType.CLASS) {
                    pending.add(value.classProperties);
                } else if (value.type == PropertyType.LIST) {
                    value.listValues.shrink();
                    for (int i = 0; i < value.listValues.size; i++) {
                        pending.add(value.listValues.get(i));
                    }
                }
            }
        }
    }

    private static final class Visit {
        final Object node;
        final boolean exit;
        Visit(Object node, boolean exit) {
            this.node = node;
            this.exit = exit;
        }
    }

    private static final class Pair {
        final Object source;
        final Object target;
        Pair(Object source, Object target) {
            this.source = source;
            this.target = target;
        }
    }
}
