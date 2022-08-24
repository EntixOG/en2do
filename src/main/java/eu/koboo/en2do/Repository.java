package eu.koboo.en2do;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import eu.koboo.en2do.annotation.Entity;
import eu.koboo.en2do.annotation.Id;
import eu.koboo.en2do.exception.DuplicateFieldException;
import eu.koboo.en2do.exception.FinalFieldException;
import eu.koboo.en2do.exception.NoFieldsException;
import eu.koboo.en2do.exception.NoUniqueIdException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ExecutorService;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Repository<T, ID> {

    @Getter
    final MongoManager mongoManager;
    final ExecutorService executorService;
    final Class<T> entityClass;
    @Getter
    final String entityCollectionName;
    final Set<Field> fieldRegistry;
    Field entityIdField;

    @SuppressWarnings("unchecked")
    public Repository(MongoManager mongoManager, ExecutorService executorService) {
        this.mongoManager = mongoManager;
        this.executorService = executorService;
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            throw new RuntimeException("No @Entity annotation present at entity " + entityClass.getName() + ". That's important, to create and get collections.");
        }
        this.entityCollectionName = entityClass.getAnnotation(Entity.class).value();
        this.fieldRegistry = new HashSet<>();
        Set<String> fieldNames = new HashSet<>();
        Field[] declaredFields = entityClass.getDeclaredFields();
        try {
            if (declaredFields.length == 0) {
                throw new NoFieldsException("No fields found in entity " + entityClass.getName());
            }
            for (Field field : declaredFields) {
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new FinalFieldException("Field \"" + field.getName() + "\" is final. That's forbidden, due to java module systems.");
                }
                String fieldName = field.getName().toLowerCase(Locale.ROOT);
                if (fieldNames.contains(fieldName)) {
                    throw new DuplicateFieldException("Duplicated field name \"" + fieldName + "\". That's forbidden, due to collisions in Documents.");
                }
                fieldNames.add(fieldName);
                fieldRegistry.add(field);
                field.setAccessible(true);
                if (!field.isAnnotationPresent(Id.class)) {
                    continue;
                }
                entityIdField = field;
            }
            if (entityIdField == null) {
                throw new NoUniqueIdException("No @Id annotation in entity " + entityClass.getName() + ". That's important, to reference entities.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        fieldNames.clear();
    }

    public MongoCollection<Document> getCollection() {
        return mongoManager.getDatabase().getCollection(entityCollectionName);
    }

    protected Class<T> getEntityClass() {
        return entityClass;
    }

    public Document toDocument(T entity) {
        Document document = new Document();
        ID uniqueId = getIdFromEntity(entity);
        if (uniqueId == null) {
            throw new NullPointerException("No uniqueId found in entity of " + entityClass.getSimpleName());
        }
        for (Field field : fieldRegistry) {
            try {
                document.put(field.getName(), field.get(entity));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return null;
            }
        }
        return document;
    }

    @SuppressWarnings("unchecked")
    public T toEntity(Document document) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        T entity = (T) entityClass.getDeclaredConstructors()[0].newInstance();
        for (Field field : fieldRegistry) {
            Object value = document.get(field.getName());
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return null;
            }
        }
        return entity;
    }

    @SuppressWarnings("unchecked")
    public ID getIdFromEntity(T entity) {
        try {
            return (ID) entityIdField.get(entity);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteAll() {
        try {
            getCollection().drop();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Result<Boolean> asyncDeleteAll() {
        return new Result<>(executorService, this::deleteAll);
    }

    public boolean save(T entity) {
        try {
            Document document = toDocument(entity);
            ID uniqueId = getIdFromEntity(entity);
            return mongoManager.update(entityCollectionName, Filters.eq(entityIdField.getName(), uniqueId), document);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Result<Boolean> asyncSave(T entity) {
        return new Result<>(executorService, () -> save(entity));
    }

    public boolean exists(Bson filters) {
        try {
            return mongoManager.exists(entityCollectionName, filters);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Result<Boolean> asyncExists(Bson filters) {
        return new Result<>(executorService, () -> exists(filters));
    }

    public T find(Bson filters) {
        try {
            Document document = mongoManager.find(entityCollectionName, filters);
            if (document == null) {
                return null;
            }
            return toEntity(document);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Result<T> asyncFind(Bson filters) {
        return new Result<>(executorService, () -> find(filters));
    }

    public List<T> findAll(Bson filters) {
        try {
            List<Document> documentList = mongoManager.into(entityCollectionName, filters);
            if (documentList == null || documentList.isEmpty()) {
                return new ArrayList<>();
            }
            List<T> entityList = new ArrayList<>();
            for (Document document : documentList) {
                T entity = toEntity(document);
                if (entity == null) {
                    continue;
                }
                entityList.add(entity);
            }
            return entityList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Result<List<T>> asyncFindAll(Bson filters) {
        return new Result<>(executorService, () -> findAll(filters));
    }

    public boolean delete(Bson filters) {
        try {
            return mongoManager.delete(entityCollectionName, filters);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Result<Boolean> asyncDelete(Bson filters) {
        return new Result<>(executorService, () -> delete(filters));
    }
}