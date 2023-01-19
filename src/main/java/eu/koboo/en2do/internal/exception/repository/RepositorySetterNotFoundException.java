package eu.koboo.en2do.internal.exception.repository;

public class RepositorySetterNotFoundException extends Exception {

    public RepositorySetterNotFoundException(Class<?> typeClass, Class<?> repoClass, String fieldName) {
        super("The class " + typeClass.getName() + " used in repository " + repoClass.getName() +
                " doesn't have a setter method for the field \"" + fieldName + "\"!");
    }
}
