# camel-quarkus kafka with schema-registry example

This example shows how camel-quarkus-kafka can be used in combination with confluent schema-registry.

## Previously Undocumented Configuartion Settings
This is not something I could find and example of anywhere.  You need to include the schema registry url, and the apikey and secret of your confluent cloud environment.  But how?  What properties do you set?   
The answer is in the KafkaRoute.java file.  Details below:   

```java
        Map<String, Object> schemaRegistryProperties = new HashMap<>();
        schemaRegistryProperties.put("basic.auth.credentials.source", "USER_INFO");
        schemaRegistryProperties.put("basic.auth.user.info", apiKeySchemaRegistry + ":" + apiSecretSchemaRegistry);
```

The api keys and secrets are stored and read from the properties file.  You have to put these into the schemaRegistryProperties hashmap and pass this in as additional properties.

```java
       // First route: consume from normal topic, write to Avro topic
        from(kafkaConsumerUrl)
                .unmarshal().json(JsonLibrary.Jackson, AvroMessage.class)
                .to(kafka(destinationTopic)
                        .brokers(bootstrapServers)
                        .schemaRegistryURL(schemaRegistryUrl)
                        .additionalProperties(schemaRegistryProperties)
                .serializerClass(KAFKA_VALUE_SERIALIZER)
                        .securityProtocol(KAFKA_SECURITY_PROTOCOL)
                        .saslMechanism(KAFKA_SASL_MECHANISM)
                        .saslJaasConfig(jaasConfigBootstrap)
                        )
                .setBody(constant("done"));

        // Second route: consume from Avro topic and log
        from(kafka(destinationTopic)
                .brokers(bootstrapServers)
                .schemaRegistryURL(schemaRegistryUrl)
                        .additionalProperties(schemaRegistryProperties)
                        .specificAvroReader(true)
                .valueDeserializer(KAFKA_VALUE_DESERIALIZER)
                .securityProtocol(KAFKA_SECURITY_PROTOCOL)
                .saslMechanism(KAFKA_SASL_MECHANISM)
                .saslJaasConfig(jaasConfigBootstrap) // JAAS config for Kafka broker
                //.saslJaasConfig(jaasConfigSchemaRegistry)
        )
````

Where is this documented?  As far as I know its only documented here in this very github repo readme.

The full file is below   

```java
package org.acme;

        import org.acme.avro.AvroMessage;
        import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
        import org.apache.camel.model.dataformat.JsonLibrary;
        import org.eclipse.microprofile.config.inject.ConfigProperty;

        import javax.enterprise.context.ApplicationScoped;
        import java.util.HashMap;
        import java.util.Map;

@ApplicationScoped
public class KafkaRoute extends EndpointRouteBuilder {

    private static final String KAFKA_SECURITY_PROTOCOL = "SASL_SSL";
    private static final String KAFKA_SASL_MECHANISM = "PLAIN";
    private static final String KAFKA_VALUE_SERIALIZER = "io.confluent.kafka.serializers.KafkaAvroSerializer";
    private static final String KAFKA_VALUE_DESERIALIZER = "io.confluent.kafka.serializers.KafkaAvroDeserializer";

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "confluent.cloud.api.key")
    String apiKey;

    @ConfigProperty(name = "confluent.cloud.api.secret")
    String apiSecret;

    @ConfigProperty(name = "kafka.topic.string")
    String sourceTopic;

    @ConfigProperty(name = "kafka.topic.avro")
    String destinationTopic;

    @ConfigProperty(name = "kafka.topic.string.group-id")
    String sourceGroupId;

    @ConfigProperty(name = "schema.registry.url")
    String schemaRegistryUrl;
    @ConfigProperty(name = "confluent.cloud.schema.registry.api.key")
    String apiKeySchemaRegistry;

    @ConfigProperty(name = "confluent.cloud.schema.registry.api.secret")
    String apiSecretSchemaRegistry;
    @ConfigProperty(name = "confluent.cloud.schema.registry.basic.auth.user.info")
    String schemaRegistryAuthUserInfo;

    @Override
    public void configure() throws Exception {

        String jaasConfigBootstrap = setupJaasConfig(apiKey, apiSecret);
        String jaasConfigSchemaRegistry = setupJaasConfig(apiKeySchemaRegistry, apiSecretSchemaRegistry);
        Map<String, Object> schemaRegistryProperties = new HashMap<>();
        schemaRegistryProperties.put("basic.auth.credentials.source", "USER_INFO");
        schemaRegistryProperties.put("basic.auth.user.info", apiKeySchemaRegistry + ":" + apiSecretSchemaRegistry);

        String kafkaConsumerUrl = setupKafkaConsumerUrl(sourceTopic, bootstrapServers, sourceGroupId, jaasConfigBootstrap);

        // First route: consume from normal topic, write to Avro topic
        from(kafkaConsumerUrl)
                .unmarshal().json(JsonLibrary.Jackson, AvroMessage.class)
                .to(kafka(destinationTopic)
                        .brokers(bootstrapServers)
                        .schemaRegistryURL(schemaRegistryUrl)
                        .additionalProperties(schemaRegistryProperties)
                .serializerClass(KAFKA_VALUE_SERIALIZER)
                        .securityProtocol(KAFKA_SECURITY_PROTOCOL)
                        .saslMechanism(KAFKA_SASL_MECHANISM)
                        .saslJaasConfig(jaasConfigBootstrap)
                        )
                .setBody(constant("done"));

        // Second route: consume from Avro topic and log
        from(kafka(destinationTopic)
                .brokers(bootstrapServers)
                .schemaRegistryURL(schemaRegistryUrl)
                        .additionalProperties(schemaRegistryProperties)
                        .specificAvroReader(true)
                .valueDeserializer(KAFKA_VALUE_DESERIALIZER)
                .securityProtocol(KAFKA_SECURITY_PROTOCOL)
                .saslMechanism(KAFKA_SASL_MECHANISM)
                .saslJaasConfig(jaasConfigBootstrap) // JAAS config for Kafka broker
                //.saslJaasConfig(jaasConfigSchemaRegistry)
        )

                .log("Avro message received: ${body}");
    }

    private String setupJaasConfig(String apiKey, String apiSecret) {
        return String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                apiKey, apiSecret);
    }

    private String setupKafkaConsumerUrl(String topic, String brokers, String groupId, String jaasConfig) {
        return String.format(
                "kafka:%s?brokers=%s&groupId=%s&securityProtocol=SASL_SSL&autoOffsetReset=earliest&saslMechanism=PLAIN&saslJaasConfig=%s",
                topic, brokers, groupId, jaasConfig);
    }
}

```

I hope this is helpful!  If you do not set this then the schema resgitry will not use the correct schema in the first 5 bytes. First byte is the magic number. Second 4 bytes is the SchemaID.   

If you are seeing magic byte errors when consumers are using the schema, this is why.  If you do not connect to the schema registry the schema id is not set when writing data to the Kafka topic.   

All the neccessary parameters are set in the src/main/resources/application.properties file.  I changed the keys and secrets, and other values by shifting and changing numbers in the sequence but left it filled out so that everyone can see clearly what goes where. Do not worry none of the keys and secrets actually work.  Using it directly with out changes should give you a 404 error followed by a 401 unathorized if you update the kafka.bootstrap.servers properly with out setting the keys and secrets.


## Preconditions
If you do not have a local cluster then you need a confluent cloud cluster running.  If using rest you will need to read the information on the Rest produce V3 documentation.
Helpful links to getstarted with rest produce v3   
[https://docs.confluent.io/cloud/current/kafka-rest/krest-qs.html](https://docs.confluent.io/cloud/current/kafka-rest/krest-qs.html)   
[https://docs.confluent.io/cloud/current/api.htm](https://docs.confluent.io/cloud/current/api.htm)

The rest of this readme and original source code came frrom the following github   
[https://github.com/tstuber/camel-quarkus-kafka-schema-registry](https://github.com/tstuber/camel-quarkus-kafka-schema-registry)   
Many thanks for his example!   

* You need to have a local kafka cluster running. You can use the provided `docker-compose.yml`.
* You need access to confluent.io maven repo: https://packages.confluent.io/maven/. You might need to add it to your `settings.xml`

## Starting the sample

The example covers twice the same use case:
* Invoke a local http endpoint with a json message. 
* The message will be written by a camel-route to kafka. 
* Another route will receive the message and print the body

The example is twice implemented. Once with a simple string serialization and once using the avro format and the schema-registry from confluent. 

```
# Invoking endoind for string message
curl -H "Content-Type: application/json" -X POST -d '{"name":"stef","message":"my string"}' http://localhost:8080/string

# Invoking endpoint for avro message
curl -H "Content-Type: application/json" -X POST -d '{"name":"andy","message":"my avro"}' http://localhost:8080/avro
```

Note that the producer registers the avro schema automatically at the schema-registry. Check the availables schemas with the schema-registry API (after avro endpoint has been invoked)

```
curl localhost:8081/subjects
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `code-with-quarkus-1.0.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application is now runnable using `java -jar target/code-with-quarkus-1.0.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.html.

# RESTEasy JAX-RS

<p>A Hello World RESTEasy resource</p>

Guide: https://quarkus.io/guides/rest-json
