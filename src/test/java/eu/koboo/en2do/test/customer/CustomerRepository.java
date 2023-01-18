package eu.koboo.en2do.test.customer;

import eu.koboo.en2do.repository.options.Collection;
import eu.koboo.en2do.repository.Repository;
import eu.koboo.en2do.repository.options.DropEntitiesOnStart;
import eu.koboo.en2do.repository.options.DropIndexesOnStart;
import eu.koboo.en2do.repository.methods.options.transform.Transform;
import eu.koboo.en2do.repository.methods.options.sort.annotation.Limit;
import eu.koboo.en2do.repository.methods.options.sort.annotation.Skip;
import eu.koboo.en2do.repository.methods.options.sort.annotation.SortBy;
import eu.koboo.en2do.repository.methods.options.sort.parameter.Sort;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Collection("customer_repository")
@DropIndexesOnStart
@DropEntitiesOnStart
public interface CustomerRepository extends Repository<Customer, UUID> {

    Customer findFirstByFirstName(String firstName);

    long countByFirstName(String firstName);

    long countByCustomerId(int customerId);

    boolean deleteByFirstName(String firstName);

    boolean existsByLastName(String lastName);

    boolean existsByLastNameContains(String lastNamePart);

    Customer findFirstByFirstNameIgn(String firstname);

    Customer findFirstByBalanceGreaterThan(double balance);

    Customer findFirstByBalanceLessThan(double balance);

    Customer findFirstByBalanceGreaterEq(double balance);

    Customer findFirstByBalanceLessEq(double balance);

    Customer findFirstByFirstNameRegex(String namePart);

    Customer findFirstByFirstNameRegex(Pattern pattern);

    Customer findFirstByFirstNameExists();

    Customer findFirstByFirstNameContains(String partOfFirstName);

    List<Customer> findManyByBalanceBetweenAndCustomerId(double from, double to, int customerId);

    Customer findFirstByFirstNameAndBalanceNotBetweenAndCustomerId(String firstName, double from, double to, int customerId);

    List<Customer> findManyByCustomerIdOrCustomerId(int customerId1, int customerId2);

    List<Customer> findManyByCustomerIdIn(List<Integer> customerIdList);

    List<Customer> findManyByCustomerIdNotIn(List<Integer> customerIdList);

    @SortBy(field = "customerId")
    @SortBy(field = "balance", ascending = true)
    @Limit(10)
    @Skip(5)
    List<Customer> findManyByCustomerIdExists();

    List<Customer> findManyByCustomerIdNot(int customerId, Sort sort);

    @Transform("existsByStreet")
    boolean myTransformedMethod(String street);

    @Transform("findManyByStreet")
    List<Customer> myTransformedMethod2(String street);
}
