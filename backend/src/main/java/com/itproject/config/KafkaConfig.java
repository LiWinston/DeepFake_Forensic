package com.itproject.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for async message processing
 */
@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id:forensic-group}")
    private String groupId;
    
    // Producer Configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
    
    // Topic names as beans
    @Bean("metadataAnalysisTopic")
    public String metadataAnalysisTopic() {
        return "metadata-analysis";
    }
    
    @Bean("fileProcessingTopic") 
    public String fileProcessingTopic() {
        return "file-processing";
    }
    
    @Bean("traditionalAnalysisTopic")
    public String traditionalAnalysisTopic() {
        return "traditional-analysis-tasks";
    }
    
    @Bean("imageAiAnalysisTopic")
    public String imageAiAnalysisTopic() { return "image-ai-analysis-tasks"; }
    
    @Bean("videoTraditionalAnalysisTopic")
    public String videoTraditionalAnalysisTopic() { return "video-traditional-analysis-tasks"; }
    
    @Bean("videoAiAnalysisTopic")
    public String videoAiAnalysisTopic() { return "video-ai-analysis-tasks"; }
    
    @Bean("analysisResultsTopic")
    public String analysisResultsTopic() { return "analysis-results"; }
    
    // Auto-create topics on application startup
    @Bean
    public NewTopic metadataAnalysisTopicCreate() {
        return TopicBuilder.name("metadata-analysis")
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
    
    @Bean 
    public NewTopic fileProcessingTopicCreate() {
        return TopicBuilder.name("file-processing")
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
    
    @Bean
    public NewTopic traditionalAnalysisTopicCreate() {
        return TopicBuilder.name("traditional-analysis-tasks")
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
    
    @Bean
    public NewTopic imageAiAnalysisTopicCreate() {
        return TopicBuilder.name("image-ai-analysis-tasks").partitions(3).replicas(1).compact().build();
    }
    
    @Bean
    public NewTopic videoTraditionalAnalysisTopicCreate() {
        return TopicBuilder.name("video-traditional-analysis-tasks").partitions(3).replicas(1).compact().build();
    }
    
    @Bean
    public NewTopic videoAiAnalysisTopicCreate() {
        return TopicBuilder.name("video-ai-analysis-tasks").partitions(3).replicas(1).compact().build();
    }
    
    @Bean
    public NewTopic analysisResultsTopicCreate() {
        return TopicBuilder.name("analysis-results").partitions(3).replicas(1).compact().build();
    }
}
