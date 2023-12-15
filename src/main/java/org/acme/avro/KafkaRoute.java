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
