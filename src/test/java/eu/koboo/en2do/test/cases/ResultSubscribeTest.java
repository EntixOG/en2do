package eu.koboo.en2do.test.cases;

import eu.koboo.en2do.MongoManager;
import eu.koboo.en2do.Result;
import eu.koboo.en2do.test.Const;
import eu.koboo.en2do.test.customer.Customer;
import eu.koboo.en2do.test.customer.CustomerRepository;
import eu.koboo.en2do.test.customer.CustomerScope;
import org.bson.conversions.Bson;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class ResultSubscribeTest {

    static MongoManager manager;
    static CustomerRepository repository;
    static CustomerScope scope;
    static ExecutorService executorService;

    @BeforeClass
    public static void before() {
        System.out.println(ResultSubscribeTest.class.getName() + " starting.");
        manager = new MongoManager();
        assertNotNull(manager);
        repository = new CustomerRepository(manager);
        assertNotNull(repository);
        scope = new CustomerScope(repository);
        assertNotNull(scope);
        executorService = Executors.newSingleThreadExecutor();
        assertNotNull(executorService);
        assertNotNull(Const.CUSTOMER);
    }

    @Test
    public void operationTest() throws Exception {
        Bson idFilter = scope.eq(Customer::getUniqueId, Const.UNIQUE_ID);
        assertNotNull(idFilter);

        Result<Boolean> deleteResult = repository.asyncDeleteAll();
        assertNotNull(deleteResult);
        deleteResult.subscribe(deleted -> {
            assertTrue(deleted);

            Result<Boolean> saveResult = repository.asyncSave(Const.CUSTOMER);
            assertNotNull(saveResult);
            saveResult.subscribe(saved -> {
                assertTrue(saved);

                Result<Boolean> existsResult = repository.asyncExists(idFilter);
                assertNotNull(existsResult);
                existsResult.subscribe(exists -> {
                    assertTrue(exists);

                    Result<Customer> result = repository.asyncFind(idFilter);
                    assertNotNull(result);

                    result.subscribe(customer -> {
                        assertNotNull(customer);
                        assertEquals(Const.UNIQUE_ID, customer.getUniqueId());
                        assertEquals(Const.FIRST_NAME, customer.getFirstName());
                        assertEquals(Const.LAST_NAME, customer.getLastName());
                        assertEquals(Const.BIRTHDAY, customer.getBirthday());
                    });
                });
            });
        });

        Thread.sleep(1000L);
    }

    @AfterClass
    public static void after() {
        System.out.println(ResultSubscribeTest.class.getName() + " ending.");
        executorService.shutdown();
        assertTrue(manager.close());
        assertTrue(repository.asyncDeleteAll().await());
    }
}