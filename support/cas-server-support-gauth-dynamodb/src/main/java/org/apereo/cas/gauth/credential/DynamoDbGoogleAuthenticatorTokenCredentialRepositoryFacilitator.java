package org.apereo.cas.gauth.credential;

import org.apereo.cas.authentication.OneTimeTokenAccount;
import org.apereo.cas.configuration.model.support.mfa.gauth.DynamoDbGoogleAuthenticatorMultifactorProperties;
import org.apereo.cas.dynamodb.DynamoDbQueryBuilder;
import org.apereo.cas.dynamodb.DynamoDbTableUtils;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.DateTimeUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is {@link DynamoDbGoogleAuthenticatorTokenCredentialRepositoryFacilitator}.
 *
 * @author Misagh Moayyed
 * @since 6.5.0
 */
@RequiredArgsConstructor
@Slf4j
public class DynamoDbGoogleAuthenticatorTokenCredentialRepositoryFacilitator {
    private final DynamoDbGoogleAuthenticatorMultifactorProperties dynamoDbProperties;

    private final DynamoDbClient amazonDynamoDBClient;

    private static GoogleAuthenticatorAccount extractAttributeValuesFrom(final Map<String, AttributeValue> item) {
        val userId = item.get(ColumnNames.USERID.getColumnName()).s();
        val id = Long.parseLong(item.get(ColumnNames.ID.getColumnName()).n());
        val validationCode = Integer.parseInt(item.get(ColumnNames.VALIDATION_CODE.getColumnName()).n());
        val name = item.get(ColumnNames.NAME.getColumnName()).s();
        val secret = item.get(ColumnNames.SECRET.getColumnName()).s();
        val scratchCodes = item.get(ColumnNames.SCRATCH_CODES.getColumnName()).ss();
        val registrationTime = DateTimeUtils.zonedDateTimeOf(Long.parseLong(item.get(ColumnNames.REGISTRATION_DATE.getColumnName()).n()));
        return GoogleAuthenticatorAccount.builder()
            .id(id)
            .name(name)
            .registrationDate(registrationTime)
            .scratchCodes(scratchCodes.stream().map(Integer::valueOf).collect(Collectors.toList()))
            .secretKey(secret)
            .username(userId)
            .validationCode(validationCode)
            .build();
    }

    @SneakyThrows
    private static Map<String, AttributeValue> buildTableAttributeValuesMap(final OneTimeTokenAccount record) {
        val values = new HashMap<String, AttributeValue>();
        values.put(ColumnNames.NAME.getColumnName(), AttributeValue.builder().s(String.valueOf(record.getName())).build());
        values.put(ColumnNames.USERID.getColumnName(), AttributeValue.builder().s(record.getUsername().toLowerCase()).build());
        values.put(ColumnNames.SECRET.getColumnName(), AttributeValue.builder().s(String.valueOf(record.getSecretKey())).build());
        values.put(ColumnNames.SCRATCH_CODES.getColumnName(), AttributeValue.builder()
            .ss(record.getScratchCodes().stream().map(String::valueOf).collect(Collectors.toList())).build());
        val time = record.getRegistrationDate().toInstant().toEpochMilli();
        values.put(ColumnNames.ID.getColumnName(), AttributeValue.builder().n(String.valueOf(record.getId())).build());
        values.put(ColumnNames.REGISTRATION_DATE.getColumnName(),
            AttributeValue.builder().n(String.valueOf(time)).build());
        values.put(ColumnNames.VALIDATION_CODE.getColumnName(),
            AttributeValue.builder().n(String.valueOf(record.getValidationCode())).build());
        LOGGER.debug("Created attribute values [{}] based on [{}]", values, record);
        return values;
    }

    /**
     * Find.
     *
     * @param id the id
     * @return the one time token account
     */
    public OneTimeTokenAccount find(final long id) {
        val query =
            List.of(
                DynamoDbQueryBuilder.builder()
                    .key(ColumnNames.ID.getColumnName())
                    .attributeValue(List.of(AttributeValue.builder().n(String.valueOf(id)).build()))
                    .operator(ComparisonOperator.EQ)
                    .build());
        val results = getRecordsByKeys(query);
        return results.isEmpty() ? null : results.iterator().next();
    }

    /**
     * Find.
     *
     * @param uid the username
     * @param id  the id
     * @return the one time token account
     */
    public OneTimeTokenAccount find(final String uid, final long id) {
        val query =
            List.of(
                DynamoDbQueryBuilder.builder()
                    .key(ColumnNames.USERID.getColumnName())
                    .attributeValue(List.of(AttributeValue.builder().s(uid.toLowerCase()).build()))
                    .operator(ComparisonOperator.EQ)
                    .build(),
                DynamoDbQueryBuilder.builder()
                    .key(ColumnNames.ID.getColumnName())
                    .attributeValue(List.of(AttributeValue.builder().n(String.valueOf(id)).build()))
                    .operator(ComparisonOperator.EQ)
                    .build());
        val results = getRecordsByKeys(query);
        return results.isEmpty() ? null : results.iterator().next();
    }

    /**
     * Find.
     *
     * @param username the username
     * @return the list
     */
    public Collection<? extends OneTimeTokenAccount> find(final String username) {
        val query =
            List.of(
                DynamoDbQueryBuilder.builder()
                    .key(ColumnNames.USERID.getColumnName())
                    .attributeValue(List.of(AttributeValue.builder().s(username.toLowerCase()).build()))
                    .operator(ComparisonOperator.EQ)
                    .build());
        return getRecordsByKeys(query);
    }

    /**
     * Find all.
     *
     * @return the list
     */
    public Collection<? extends OneTimeTokenAccount> findAll() {
        return getRecordsByKeys(List.of());
    }

    /**
     * Store.
     *
     * @param account the encoded account
     */
    public void store(final OneTimeTokenAccount account) {
        val values = buildTableAttributeValuesMap(account);
        val putItemRequest = PutItemRequest.builder().tableName(dynamoDbProperties.getTableName()).item(values).build();
        LOGGER.debug("Submitting put request [{}] for record [{}]", putItemRequest, account);
        val putItemResult = amazonDynamoDBClient.putItem(putItemRequest);
        LOGGER.debug("Record added with result [{}]", putItemResult);
    }

    /**
     * Remove all.
     */
    public void removeAll() {
        createTable(true);
    }

    /**
     * Remove.
     *
     * @param username the username
     */
    public void remove(final String username) {
        val query = List.of(
            DynamoDbQueryBuilder.builder()
                .key(ColumnNames.USERID.getColumnName())
                .attributeValue(List.of(AttributeValue.builder().s(username.toLowerCase()).build()))
                .operator(ComparisonOperator.GE)
                .build());
        val records = getRecordsByKeys(query);

        records.forEach(record -> {
            val del = DeleteItemRequest.builder()
                .tableName(dynamoDbProperties.getTableName())
                .key(CollectionUtils.wrap(
                    ColumnNames.ID.getColumnName(), AttributeValue.builder().n(String.valueOf(record.getId())).build()))
                .build();
            LOGGER.debug("Submitting delete request [{}] for [{}]", del, record.getId());
            val res = amazonDynamoDBClient.deleteItem(del);
            LOGGER.debug("Delete request came back with result [{}]", res);
        });
    }

    /**
     * Remove.
     *
     * @param id the id
     */
    public void remove(final long id) {
        val query = List.of(
            DynamoDbQueryBuilder.builder()
                .key(ColumnNames.ID.getColumnName())
                .attributeValue(List.of(AttributeValue.builder().n(String.valueOf(id)).build()))
                .operator(ComparisonOperator.GE)
                .build());
        val records = getRecordsByKeys(query);

        records.forEach(record -> {
            val del = DeleteItemRequest.builder()
                .tableName(dynamoDbProperties.getTableName())
                .key(CollectionUtils.wrap(
                    ColumnNames.ID.getColumnName(), AttributeValue.builder().n(String.valueOf(record.getId())).build()))
                .build();
            LOGGER.debug("Submitting delete request [{}] for [{}]", del, record.getId());
            val res = amazonDynamoDBClient.deleteItem(del);
            LOGGER.debug("Delete request came back with result [{}]", res);
        });
    }

    /**
     * Count.
     *
     * @return the long
     */
    public long count() {
        return findAll().size();
    }

    /**
     * Count.
     *
     * @param username the username
     * @return the long
     */
    public long count(final String username) {
        return find(username).size();
    }

    /**
     * Create table.
     *
     * @param deleteTables delete existing tables
     */
    @SneakyThrows
    public void createTable(final boolean deleteTables) {
        val throughput = ProvisionedThroughput.builder()
            .readCapacityUnits(dynamoDbProperties.getReadCapacity())
            .writeCapacityUnits(dynamoDbProperties.getWriteCapacity())
            .build();
        val request = CreateTableRequest.builder()
            .attributeDefinitions(AttributeDefinition.builder()
                .attributeName(ColumnNames.ID.getColumnName())
                .attributeType(ScalarAttributeType.N).build())
            .keySchema(KeySchemaElement.builder()
                .attributeName(ColumnNames.ID.getColumnName())
                .keyType(KeyType.HASH).build())
            .provisionedThroughput(throughput)
            .billingMode(BillingMode.fromValue(dynamoDbProperties.getBillingMode().name()))
            .tableName(dynamoDbProperties.getTableName())
            .build();
        if (deleteTables) {
            val delete = DeleteTableRequest.builder().tableName(dynamoDbProperties.getTableName()).build();
            LOGGER.debug("Sending delete request [{}] to remove table if necessary", delete);
            DynamoDbTableUtils.deleteTableIfExists(amazonDynamoDBClient, delete);
        }
        LOGGER.debug("Sending create request [{}] to create table", request);
        DynamoDbTableUtils.createTableIfNotExists(amazonDynamoDBClient, request);
        LOGGER.debug("Waiting until table [{}] becomes active...", request.tableName());
        DynamoDbTableUtils.waitUntilActive(amazonDynamoDBClient, request.tableName());
        val describeTableRequest = DescribeTableRequest.builder().tableName(request.tableName()).build();
        LOGGER.debug("Sending request [{}] to obtain table description...", describeTableRequest);
        val tableDescription = amazonDynamoDBClient.describeTable(describeTableRequest).table();
        LOGGER.debug("Located newly created table with description: [{}]", tableDescription);
    }

    /**
     * The column names.
     */
    @Getter
    @RequiredArgsConstructor
    public enum ColumnNames {
        /**
         * User id column.
         */
        ID("id"),
        /**
         * User id column.
         */
        USERID("userid"),
        /**
         * secret column.
         */
        SECRET("secret"),
        /**
         * validation code column.
         */
        VALIDATION_CODE("validationCode"),
        /**
         * scratch code column.
         */
        SCRATCH_CODES("scratchCodes"),
        /**
         * registration time column.
         */
        REGISTRATION_DATE("registrationDate"),
        /**
         * name column.
         */
        NAME("name");

        private final String columnName;
    }

    private Collection<? extends OneTimeTokenAccount> getRecordsByKeys(final List<DynamoDbQueryBuilder> queries) {
        val scanRequest = ScanRequest.builder()
            .tableName(dynamoDbProperties.getTableName())
            .scanFilter(queries.stream()
                .map(query -> {
                    val cond = Condition.builder().comparisonOperator(query.getOperator())
                        .attributeValueList(query.getAttributeValue()).build();
                    return Pair.of(query.getKey(), cond);
                })
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue)))
            .build();
        LOGGER.debug("Submitting request [{}] to get record with keys [{}]", scanRequest, queries);
        val items = amazonDynamoDBClient.scan(scanRequest).items();
        return items
            .stream()
            .map(DynamoDbGoogleAuthenticatorTokenCredentialRepositoryFacilitator::extractAttributeValuesFrom)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
