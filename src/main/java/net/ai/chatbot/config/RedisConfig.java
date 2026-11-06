package net.ai.chatbot.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import net.ai.chatbot.service.aiprocess.KnowledgebaseIngestionService;
import net.ai.chatbot.service.mongodb.MongodbVectorService;
import net.ai.chatbot.service.redis.KnowledgebaseProcessor;
import net.ai.chatbot.service.redis.RedisConsumerGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static net.ai.chatbot.constants.Constants.CHAT_BOT_CREATE_EVENT_STREAM;
import static net.ai.chatbot.constants.Constants.REDIS_STREAM_SERVER_GROUP;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    private final RedisConsumerGroupService redisConsumerGroupService;

    public RedisConfig(RedisConsumerGroupService redisConsumerGroupService) {
        this.redisConsumerGroupService = redisConsumerGroupService;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {

        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(redisHost, redisPort);
        config.setUsername(redisUsername);
        config.setPassword(redisPassword);

        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(true)                 // VERY IMPORTANT
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)            // VERY IMPORTANT
                .socketOptions(socketOptions)
                .pingBeforeActivateConnection(true) // VERY IMPORTANT
                .build();

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(30))
                        .clientOptions(clientOptions)
                        .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisObjectTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // Configure serializers (e.g., JSON serializer)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(MapRecord.class));
        return template;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamMessageListenerContainer(RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(String.class)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public Subscription websiteTrainStreamSubscription(
            StreamMessageListenerContainer<String, ObjectRecord<String, String>> container,
            MongoTemplate mongoTemplate,
            MongodbVectorService mongodbVectorService,
            KnowledgebaseIngestionService knowledgebaseIngestionService
    ) throws UnknownHostException {

        redisConsumerGroupService
                .createConsumerGroupIfNotExists(redisConnectionFactory(), CHAT_BOT_CREATE_EVENT_STREAM, REDIS_STREAM_SERVER_GROUP);

        StreamOffset<String> streamOffset = StreamOffset.create(CHAT_BOT_CREATE_EVENT_STREAM, ReadOffset.lastConsumed());

        container.start();

        return container.receive(
                Consumer.from(REDIS_STREAM_SERVER_GROUP, InetAddress.getLocalHost().getHostName()),
                streamOffset,
                purchaseStreamListener(mongoTemplate, mongodbVectorService, knowledgebaseIngestionService)
        );
    }

    @Bean
    public StreamListener<String, ObjectRecord<String, String>> purchaseStreamListener(MongoTemplate mongoTemplate,
                                                                                       MongodbVectorService mongodbVectorService,
                                                                                       KnowledgebaseIngestionService knowledgebaseIngestionService) {
        return new KnowledgebaseProcessor(mongoTemplate, mongodbVectorService, knowledgebaseIngestionService);
    }
}