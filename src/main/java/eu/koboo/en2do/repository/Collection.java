package eu.koboo.en2do.repository;

import java.lang.annotation.*;

/**
 * This annotation defines the collection name of the repository in the mongodb.
 * See documentation: <a href="https://koboo.gitbook.io/en2do/get-started/create-the-repository">...</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Collection {

    /**
     * @return The collection name in the mongodb.
     */
    String value();
}
