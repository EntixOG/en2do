package eu.koboo.en2do.internal.methods.dynamic;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a filter part of the method.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public class MethodFilterPart {

    /**
     * The type of the filter
     */
    @NotNull
    FilterType filterType;
    /**
     * The parameter index
     */
    int nextParameterIndex;
}
