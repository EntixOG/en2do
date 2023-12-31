package eu.koboo.en2do.utility;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A utility class for everything related to fields.
 */
@UtilityClass
@SuppressWarnings("unused")
public class FieldUtils {

    /**
     * This method is used to scan a class for all fields.
     *
     * @param typeClass The class, which should be scanned
     * @param <E>       The generic type of the class
     * @return The Set with all found fields of the given class.
     */
    public <E> @NotNull Set<Field> collectFields(@NotNull Class<E> typeClass) {
        Set<Field> fields = new HashSet<>();
        Class<?> clazz = typeClass;
        while (clazz != Object.class) {
            Field[] declaredFields = clazz.getDeclaredFields();
            clazz = clazz.getSuperclass();
            if (declaredFields.length == 0) {
                continue;
            }
            fields.addAll(Arrays.asList(declaredFields));
        }
        return fields;
    }

    /**
     * This method is used to iterate through a set of fields and search for a field by its name.
     *
     * @param fieldName The field name, which should be searched.
     * @param fieldSet  The Set, which should be iterated through
     * @return The field, if found. If not found, it returns "null"
     */
    public @Nullable Field findFieldByName(@NotNull String fieldName, @NotNull Set<Field> fieldSet) {
        for (Field field : fieldSet) {
            if (!field.getName().equalsIgnoreCase(fieldName)) {
                continue;
            }
            return field;
        }
        return null;
    }
}
