package cn.edu.gdou.jingbanyou.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.store.ChromaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库配置类 - ChromaDB
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.chroma.client.host:localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    @Value("${spring.ai.vectorstore.chroma.store.collection-name:jingbanyou_knowledge}")
    private String collectionName;

    /**
     * ChromaDB API 客户端
     */
    @Bean
    public ChromaApi chromaApi() {
        log.info("初始化 ChromaDB API 客户端 - Host: {}:{}, Collection: {}", chromaHost, chromaPort, collectionName);
        return new ChromaApi(chromaHost, chromaPort);
    }

    /**
     * 向量存储 Bean - 用于 RAG 知识库
     */
    @Bean
    public VectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        log.info("初始化 ChromaDB 向量存储...");
        return ChromaVectorStore.builder()
                .chromaApi(chromaApi)
                .embeddingModel(embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }

}
