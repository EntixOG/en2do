package eu.koboo.en2do;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import eu.koboo.en2do.internal.RepositoryInvocationHandler;
import eu.koboo.en2do.internal.RepositoryMeta;
import eu.koboo.en2do.internal.Validator;
import eu.koboo.en2do.internal.codec.InternalPropertyCodecProvider;
import eu.koboo.en2do.internal.convention.AnnotationConvention;
import eu.koboo.en2do.internal.exception.methods.*;
import eu.koboo.en2do.internal.exception.repository.*;
import eu.koboo.en2do.internal.methods.dynamic.DynamicMethod;
import eu.koboo.en2do.internal.methods.dynamic.FilterType;
import eu.koboo.en2do.internal.methods.dynamic.MethodFilterPart;
import eu.koboo.en2do.internal.methods.operators.FilterOperator;
import eu.koboo.en2do.internal.methods.operators.MethodOperator;
import eu.koboo.en2do.internal.methods.predefined.impl.*;
import eu.koboo.en2do.repository.Collection;
import eu.koboo.en2do.repository.DropEntitiesOnStart;
import eu.koboo.en2do.repository.DropIndexesOnStart;
import eu.koboo.en2do.repository.Repository;
import eu.koboo.en2do.repository.entity.Id;
import eu.koboo.en2do.repository.entity.NonIndex;
import eu.koboo.en2do.repository.entity.compound.CompoundIndex;
import eu.koboo.en2do.repository.entity.compound.Index;
import eu.koboo.en2do.repository.entity.ttl.TTLIndex;
import eu.koboo.en2do.repository.methods.async.Async;
import eu.koboo.en2do.repository.methods.fields.UpdateBatch;
import eu.koboo.en2do.repository.methods.pagination.Pagination;
import eu.koboo.en2do.repository.methods.sort.*;
import eu.koboo.en2do.repository.methods.transform.Transform;
import eu.koboo.en2do.utility.AnnotationUtils;
import eu.koboo.en2do.utility.FieldUtils;
import eu.koboo.en2do.utility.GenericUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * This object is the main entry point of en2do.
 * The connection will be opened on construction.
 * Keep in mind, that you should call "#MongoManger#close()" on application shutdown/termination.
 * See documentation: <a href="https://koboo.gitbook.io/en2do/get-started/create-the-mongomanager">...</a>
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@SuppressWarnings("unused")
public class MongoManager {

    // Predefined methods by Java objects
    // These methods are ignored by our method processing proxy / invocation handler.
    @NotNull
    private static final List<String> IGNORED_DEFAULT_METHODS = Arrays.asList(
            "notify", "notifyAll", "wait", "finalize", "clone"
    );

    @NotNull
    Map<Class<?>, Repository<?, ?>> repositoryRegistry;

    @NotNull
    Map<Class<?>, RepositoryMeta<?, ?, ?>> repositoryMetaRegistry;

    @Nullable
    ExecutorService executorService;

    @Getter
    @NotNull
    CodecRegistry codecRegistry;

    @NotNull
    MongoClient client;

    @NotNull
    MongoDatabase database;

    public MongoManager(@Nullable Credentials credentials, @Nullable ExecutorService executorService) {
        repositoryRegistry = new ConcurrentHashMap<>();
        repositoryMetaRegistry = new ConcurrentHashMap<>();

        this.executorService = executorService;

        // If no credentials given, try loading them from default file.
        if (credentials == null) {
            credentials = Credentials.fromFile();
        }
        // If no credentials given, try loading them from default resource.
        if (credentials == null) {
            credentials = Credentials.fromResource();
        }
        // If no credentials given, throw exception.
        if (credentials == null) {
            throw new NullPointerException("No credentials given! Please make sure to provide " +
                    "accessible credentials.");
        }

        String connectString = credentials.getConnectString();
        // If credentials connectString is null, throw exception
        if (connectString == null) {
            throw new NullPointerException("No connectString given! Please make sure to provide a " +
                    "accessible connectString.");
        }
        // If credentials databaseString is null, throw exception
        String databaseString = credentials.getDatabase();
        if (databaseString == null) {
            throw new NullPointerException("No databaseString given! Please make sure to provide a " +
                    "accessible databaseString.");
        }

        ConnectionString connection = new ConnectionString(connectString);

        codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder()
                        .register(new InternalPropertyCodecProvider())
                        .automatic(true)
                        .conventions(List.of(
                                new AnnotationConvention(repositoryMetaRegistry),
                                Conventions.ANNOTATION_CONVENTION,
                                Conventions.SET_PRIVATE_FIELDS_CONVENTION,
                                Conventions.USE_GETTERS_FOR_SETTERS
                        ))
                        .build())
        );

        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applicationName("en2do-client")
                .applyConnectionString(connection)
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(codecRegistry)
                .build();

        client = MongoClients.create(clientSettings);
        database = client.getDatabase(databaseString);
    }

    public MongoManager(@Nullable Credentials credentials) {
        this(credentials, null);
    }

    public MongoManager() {
        this(null, null);
    }


    public boolean close() {
        return close(true);
    }

    public boolean close(boolean shutdownExecutor) {
        try {
            if (executorService != null && shutdownExecutor) {
                executorService.shutdown();
            }
            repositoryRegistry.clear();
            for (RepositoryMeta<?, ?, ?> meta : repositoryMetaRegistry.values()) {
                meta.destroy();
            }
            repositoryMetaRegistry.clear();
            client.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <E, ID, R extends Repository<E, ID>> @NotNull R create(@NotNull Class<R> repositoryClass) {
        try {

            // Check for already created repository to avoid multiply instances of the same repository
            if (repositoryRegistry.containsKey(repositoryClass)) {
                return (R) repositoryRegistry.get(repositoryClass);
            }

            // Parse annotated collection name and create pojo-related mongo collection
            Collection collectionAnnotation = repositoryClass.getAnnotation(Collection.class);
            if (collectionAnnotation == null) {
                throw new RepositoryNameNotFoundException(repositoryClass, Collection.class);
            }

            // Check if the collection name is valid and for duplication issues
            String entityCollectionName = collectionAnnotation.value();
            if (entityCollectionName.trim().equalsIgnoreCase("")) {
                throw new RepositoryNameNotFoundException(repositoryClass, Collection.class);
            }
            for (RepositoryMeta<?, ?, ?> meta : repositoryMetaRegistry.values()) {
                if (meta.getCollectionName().equalsIgnoreCase(entityCollectionName)) {
                    throw new RepositoryNameDuplicateException(repositoryClass, Collection.class);
                }
            }

            // Parse Entity and UniqueId type classes by generic repository arguments
            // (Yea, it's very hacky/unclean, but it works)
            Type[] repoGenericTypeArray = repositoryClass.getGenericInterfaces();
            Type repoGenericTypeParams = null;
            for (Type type : repoGenericTypeArray) {
                if (type.getTypeName().split("<")[0].equalsIgnoreCase(Repository.class.getName())) {
                    repoGenericTypeParams = type;
                    break;
                }
            }
            if (repoGenericTypeParams == null) {
                throw new RepositoryNoTypeException(repositoryClass);
            }

            // Searching for entity class
            Class<E> entityClass;
            try {
                // get class name of generic type arguments
                String entityClassName = repoGenericTypeParams.getTypeName().split("<")[1].split(",")[0];
                entityClass = (Class<E>) Class.forName(entityClassName);
            } catch (ClassNotFoundException e) {
                throw new RepositoryEntityNotFoundException(repositoryClass, e);
            }

            Validator.validateCompatibility(repositoryClass, entityClass);

            // Collect all fields recursively to ensure, we'll get the inheritance fields
            Set<Field> entityFieldSet = FieldUtils.collectFields(entityClass);

            // Class type of the uniqueId of the entity.
            Class<ID> tempEntityUniqueIdClass = null;
            Field tempEntityUniqueIdField = null;
            for (Field field : entityFieldSet) {
                // Check for @Id annotation to find unique identifier of entity
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                tempEntityUniqueIdClass = (Class<ID>) field.getType();
                tempEntityUniqueIdField = field;
                tempEntityUniqueIdField.setAccessible(true);
            }
            // Check if we found any unique identifier.
            if (tempEntityUniqueIdClass == null) {
                throw new RepositoryIdNotFoundException(entityClass, Id.class);
            }
            Class<ID> entityUniqueIdClass = tempEntityUniqueIdClass;
            Field entityUniqueIdField = tempEntityUniqueIdField;

            MongoCollection<E> entityCollection = database.getCollection(entityCollectionName, entityClass);

            RepositoryMeta<E, ID, R> repositoryMeta = new RepositoryMeta<>(
                    repositoryClass, entityClass,
                    entityFieldSet,
                    entityUniqueIdClass, entityUniqueIdField,
                    entityCollection, entityCollectionName
            );


            // Define default methods with handler into the meta registry
            repositoryMeta.registerPredefinedMethod(new MethodCountAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodDelete<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodDeleteAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodDeleteById<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodDrop<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodEquals<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodExists<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodExistsById<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodFindAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodFindFirstById<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodGetClass<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodGetCollectionName<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodGetEntityClass<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodGetEntityUniqueIdClass<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodGetUniqueId<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodHashCode<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodPageAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodSave<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodSaveAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodSortAll<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodToString<>(repositoryMeta, entityCollection));
            repositoryMeta.registerPredefinedMethod(new MethodUpdateAllFields<>(repositoryMeta, entityCollection));

            // Iterate through the repository methods
            for (Method method : repositoryClass.getMethods()) {
                String methodName = method.getName();

                // Apply transform annotation
                Transform transform = method.getAnnotation(Transform.class);
                if (transform != null) {
                    methodName = transform.value();
                }

                // Check if we catch a predefined method
                if (repositoryMeta.isRepositoryMethod(methodName)) {
                    continue;
                }

                // Skip if the method should be ignored
                if (IGNORED_DEFAULT_METHODS.contains(methodName)) {
                    continue;
                }

                // Get the default return type of the method
                Class<?> returnType = method.getReturnType();

                // Check if the method is async and if so, check for completable future return type.
                boolean isAsyncMethod = method.isAnnotationPresent(Async.class);
                if (isAsyncMethod) {
                    // Check async method name
                    if (methodName.startsWith("async")) {
                        String predefinedName = repositoryMeta.getPredefinedNameByAsyncName(methodName);
                        if (repositoryMeta.isRepositoryMethod(predefinedName)) {
                            continue;
                        }
                        throw new MethodInvalidAsyncNameException(method, repositoryClass);
                    }
                    // Check CompletableFuture return type
                    if (GenericUtils.isNotTypeOf(returnType, CompletableFuture.class)) {
                        throw new MethodInvalidAsyncReturnException(method, repositoryClass);
                    }
                    returnType = GenericUtils.getGenericTypeOfReturnType(method);
                }


                // Parse the MethodOperator by the methodName
                MethodOperator methodOperator = MethodOperator.parseMethodStartsWith(methodName);
                if (methodOperator == null) {
                    throw new MethodNoMethodOperatorException(method, repositoryClass);
                }

                // Check the returnTypes by using the predefined validator.
                methodOperator.validate(method, returnType, entityClass, repositoryClass);

                String methodNameWithoutOperator = methodOperator.removeOperatorFrom(methodName);
                if (methodName.contains("And") && methodName.contains("Or")) {
                    throw new MethodDuplicatedChainException(method, entityClass);
                }

                boolean multipleFilter = methodNameWithoutOperator.contains("And") || methodNameWithoutOperator.contains("Or");
                boolean andFilter = methodNameWithoutOperator.contains("And");
                String[] methodFilterPartArray;
                if (andFilter) {
                    methodFilterPartArray = methodNameWithoutOperator.split("And");
                } else {
                    methodFilterPartArray = methodNameWithoutOperator.split("Or");
                }

                // Count for further validation
                int expectedParameterCount = 0;

                int nextParameterIndex = 0;
                int itemCount = 0;
                List<MethodFilterPart> filterPartList = new LinkedList<>();
                for (String filterOperatorString : methodFilterPartArray) {
                    FilterType filterType = createFilterType(entityClass, repositoryClass, method, filterOperatorString,
                            entityFieldSet);
                    int filterTypeParameterCount = filterType.getOperator().getExpectedParameterCount();
                    for (int i = 0; i < filterTypeParameterCount; i++) {
                        int paramIndex = nextParameterIndex + i;
                        Class<?> paramClass = method.getParameters()[paramIndex].getType();
                        if (paramClass == null) {
                            throw new MethodParameterNotFoundException(method, repositoryClass, (paramIndex + filterTypeParameterCount),
                                    method.getParameterCount());
                        }
                        // Special checks for some operators
                        Class<?> fieldClass = filterType.getField().getType();
                        switch (filterType.getOperator()) {
                            case REGEX:
                                if (GenericUtils.isNotTypeOf(String.class, paramClass) && GenericUtils.isNotTypeOf(Pattern.class, paramClass)) {
                                    throw new MethodInvalidRegexParameterException(method, repositoryClass, paramClass);
                                }
                                break;
                            case IN:
                                if (GenericUtils.isNotTypeOf(List.class, paramClass)) {
                                    throw new MethodMismatchingTypeException(method, repositoryClass, List.class, paramClass);
                                }
                                Class<?> listType = GenericUtils.getGenericTypeOfParameter(method, paramIndex);
                                if (GenericUtils.isNotTypeOf(fieldClass, listType)) {
                                    throw new MethodInvalidListParameterException(method, repositoryClass, fieldClass, listType);
                                }
                                break;
                            default:
                                if (GenericUtils.isNotTypeOf(fieldClass, paramClass)) {
                                    throw new MethodMismatchingTypeException(method, repositoryClass, fieldClass, paramClass);
                                }
                                break;
                        }
                    }
                    MethodFilterPart filterPart = new MethodFilterPart(filterType, nextParameterIndex);
                    filterPartList.add(filterPart);
                    // Further validation
                    expectedParameterCount += filterTypeParameterCount;
                    nextParameterIndex = itemCount + filterTypeParameterCount;
                    itemCount += 1;
                }

                int methodParameterCount = method.getParameterCount();
                // If the method is a pageBy, it needs at least one parameter of type Pager
                if (methodOperator == MethodOperator.PAGE && methodParameterCount == 0) {
                    throw new MethodPageRequiredException(method, repositoryClass, Pagination.class);
                }
                // Validate the parameterCount of the filters and the method parameters itself.
                if (expectedParameterCount != methodParameterCount) {
                    if (methodParameterCount > 0) {
                        // Subtract 1 from parameterCount. This object could be a Sort or Pager object.
                        // That means, the expectedParameterCount is less than the actualParameterCount.
                        Class<?> lastMethodParameter = method.getParameterTypes()[methodParameterCount - 1];
                        if (lastMethodParameter.isAssignableFrom(Sort.class)) {
                            if (methodOperator == MethodOperator.PAGE) {
                                throw new MethodSortNotAllowedException(method, repositoryClass);
                            }
                            if ((expectedParameterCount + 1) != methodParameterCount) {
                                throw new MethodParameterCountException(method, repositoryClass, (expectedParameterCount + 1), methodParameterCount);
                            }
                        }
                        if (lastMethodParameter.isAssignableFrom(Pagination.class)) {
                            if (methodOperator != MethodOperator.PAGE) {
                                throw new MethodPageNotAllowedException(method, repositoryClass);
                            }
                            if ((expectedParameterCount + 1) != methodParameterCount) {
                                throw new MethodParameterCountException(method, repositoryClass, (expectedParameterCount + 1), methodParameterCount);
                            }
                        }
                        if(lastMethodParameter.isAssignableFrom(UpdateBatch.class)) {
                            if(methodOperator != MethodOperator.UPDATE_FIELD) {
                                throw new MethodBatchNotAllowedException(method, repositoryClass);
                            }
                            if ((expectedParameterCount + 1) != methodParameterCount) {
                                throw new MethodParameterCountException(method, repositoryClass, (expectedParameterCount + 1), methodParameterCount);
                            }
                        }
                    } else {
                        throw new MethodParameterCountException(method, repositoryClass, expectedParameterCount, methodParameterCount);
                    }
                }

                // Check if the field from sort annotation exists.
                SortBy sortAnnotation = method.getAnnotation(SortBy.class);
                if (sortAnnotation != null) {
                    if (methodOperator == MethodOperator.PAGE) {
                        throw new MethodSortNotAllowedException(method, repositoryClass);
                    }
                    String sortFieldName = sortAnnotation.field();
                    Field field = FieldUtils.findFieldByName(sortFieldName, entityFieldSet);
                    if (field == null) {
                        throw new MethodSortFieldNotFoundException(sortFieldName, method, entityClass, repositoryClass);
                    }
                }
                if (methodParameterCount > 0) {
                    Class<?> lastMethodParameter = method.getParameterTypes()[methodParameterCount - 1];
                    // Check if both Sort types are used.
                    // This is not allowed, even if it is possible internally.
                    boolean hasAnySortAnnotation = method.isAnnotationPresent(Limit.class)
                            || method.isAnnotationPresent(Skip.class)
                            || method.isAnnotationPresent(SortBy.class)
                            || method.isAnnotationPresent(SortByArray.class);
                    if (hasAnySortAnnotation && lastMethodParameter.isAssignableFrom(Sort.class)) {
                        throw new MethodMixedSortException(method, repositoryClass, Sort.class, SortBy.class);
                    }
                    // We can't check the field, because it's a parameter, we can only check it, on executing
                    // while runtime
                }

                DynamicMethod<E, ID, R> dynamicMethod = new DynamicMethod<>(method, repositoryMeta, methodOperator,
                        multipleFilter, andFilter, filterPartList);
                repositoryMeta.registerDynamicMethod(methodName, dynamicMethod);
            }

            // Drop all entities on start if annotation is present.
            if (repositoryClass.isAnnotationPresent(DropEntitiesOnStart.class)) {
                entityCollection.drop();
            }

            // Drop all indexes on start if annotation is present.
            if (repositoryClass.isAnnotationPresent(DropIndexesOnStart.class)) {
                entityCollection.dropIndexes();
            }

            // Check for invalid index configuration
            if (!repositoryMeta.isSeparateEntityId() && entityUniqueIdField.isAnnotationPresent(NonIndex.class)) {
                throw new RepositoryNonIndexIdException(repositoryClass);
            }

            // Creating an index on the uniqueIdentifier field of the entity to speed up queries,
            // but only if wanted. Users can disable that with the annotation.
            if (repositoryMeta.isSeparateEntityId()
                    && !entityUniqueIdField.isAnnotationPresent(NonIndex.class)) {
                entityCollection.createIndex(Indexes.ascending(entityUniqueIdField.getName()), new IndexOptions().unique(true));
            }
            Set<CompoundIndex> compoundIndexSet = AnnotationUtils.collectAnnotations(entityClass, CompoundIndex.class);
            for (CompoundIndex compoundIndex : compoundIndexSet) {
                // Checking if the field in the annotation exists in the entity class.
                Index[] fieldIndexes = compoundIndex.value();
                for (Index fieldIndex : fieldIndexes) {
                    if (entityFieldSet.stream().map(Field::getName).noneMatch(fieldName -> fieldIndex.value().equalsIgnoreCase(fieldName))) {
                        throw new RepositoryIndexFieldNotFoundException(repositoryClass, fieldIndex.value());
                    }
                }
                // Validated all fields and creating the indexes on the collection.
                List<Bson> indexBsonList = new ArrayList<>();
                for (Index fieldIndex : fieldIndexes) {
                    String fieldName = fieldIndex.value();
                    Bson bsonIndex;
                    if (fieldIndex.ascending()) {
                        bsonIndex = Indexes.ascending(fieldName);
                    } else {
                        bsonIndex = Indexes.descending(fieldName);
                    }
                    indexBsonList.add(bsonIndex);
                }
                IndexOptions indexOptions = new IndexOptions()
                        .unique(compoundIndex.uniqueIndex());
                entityCollection.createIndex(Indexes.compoundIndex(indexBsonList), indexOptions);
            }

            Set<TTLIndex> ttlIndexSet = AnnotationUtils.collectAnnotations(entityClass, TTLIndex.class);
            for (TTLIndex ttlIndex : ttlIndexSet) {
                // Checking if the field in the annotation exists in the entity class.
                String ttlField = ttlIndex.value();
                boolean foundTTLField = false;
                for (Field entityField : entityFieldSet) {
                    if (!entityField.getName().equalsIgnoreCase(ttlField)) {
                        continue;
                    }
                    if (GenericUtils.isNotTypeOf(entityField.getType(), Date.class)) {
                        continue;
                    }
                    foundTTLField = true;
                    break;
                }
                if (!foundTTLField) {
                    throw new RepositoryTTLFieldNotFoundException(repositoryClass, ttlField);
                }
                IndexOptions indexOptions = new IndexOptions()
                        .expireAfter(ttlIndex.ttl(), ttlIndex.time());
                entityCollection.createIndex(Indexes.ascending(ttlField), indexOptions);
            }

            ///////////////////////////
            //                       //
            // Validation successful //
            //                       //
            ///////////////////////////

            // Create dynamic repository proxy object
            ClassLoader repoClassLoader = repositoryClass.getClassLoader();
            Class<?>[] interfaces = new Class[]{repositoryClass};
            Repository<E, ID> repository = (Repository<E, ID>) Proxy.newProxyInstance(repoClassLoader, interfaces,
                    new RepositoryInvocationHandler<>(repositoryMeta, executorService));
            repositoryRegistry.put(repositoryClass, repository);
            repositoryMetaRegistry.put(repositoryClass, repositoryMeta);
            return (R) repository;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <E> @NotNull FilterType createFilterType(@NotNull Class<E> entityClass, @NotNull Class<?> repoClass,
                                                     @NotNull Method method, @NotNull String filterOperatorString,
                                                     @NotNull Set<Field> fieldSet) throws Exception {
        FilterOperator filterOperator = FilterOperator.parseFilterEndsWith(filterOperatorString);
        String expectedFieldName = filterOperator.removeOperatorFrom(filterOperatorString);
        boolean notFilter = false;
        if (expectedFieldName.endsWith("Not")) {
            expectedFieldName = expectedFieldName.replaceFirst("Not", "");
            notFilter = true;
        }
        Field field = FieldUtils.findFieldByName(expectedFieldName, fieldSet);
        if (field == null) {
            throw new MethodFieldNotFoundException(expectedFieldName, method, entityClass, repoClass);
        }
        return new FilterType(field, notFilter, filterOperator);
    }
}
