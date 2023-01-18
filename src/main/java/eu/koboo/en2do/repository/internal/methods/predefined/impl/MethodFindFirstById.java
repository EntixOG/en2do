package eu.koboo.en2do.repository.internal.methods.predefined.impl;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import eu.koboo.en2do.repository.Repository;
import eu.koboo.en2do.repository.internal.RepositoryMeta;
import eu.koboo.en2do.repository.internal.methods.predefined.PredefinedMethod;
import org.bson.conversions.Bson;

import java.lang.reflect.Method;

public class MethodFindFirstById<E, ID, R extends Repository<E, ID>> extends PredefinedMethod<E, ID, R> {

    public MethodFindFirstById(RepositoryMeta<E, ID, R> meta, MongoCollection<E> entityCollection) {
        super("findFirstById", meta, entityCollection);
    }

    @Override
    public Object handle(Method method, Object[] arguments) throws Exception {
        ID uniqueId = repositoryMeta.checkUniqueId(method, arguments[0]);
        Bson idFilter = repositoryMeta.createIdFilter(uniqueId);
        FindIterable<E> findIterable = repositoryMeta.createIterable(idFilter, methodName);
        return findIterable.limit(1).first();
    }
}
