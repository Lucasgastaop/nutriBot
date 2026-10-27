package br.com.fiap.nutribot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.fiap.nutribot.tool.NutritionTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

// ChatClient único do /chat: persona, memória por conversationId, tools e RAG.
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    static final String NUTRITION_SYSTEM = """
            Você é a NutriBot, nutricionista virtual da Clínica Equilíbrio.
            Responda sempre em português, em frases curtas, acolhedoras e sem julgamento.
            Nunca use as expressões "dieta da fome", "proibido" ou "dia do lixo".

            Escopo:
            - Fale apenas de nutrição, alimentação, hidratação, refeições, alimentos, calorias e do atendimento da Clínica Equilíbrio.
            - Se o assunto não for nutrição, recuse com educação e convide a pessoa a perguntar sobre alimentação.
            - Não faça diagnóstico.
            - Não prescreva dieta clínica, medicamento nem suplemento.
            - Lembre que você não substitui o nutricionista.

            Memória da conversa:
            - Lembre o que a pessoa disse neste conversationId, inclusive o nome que ela informar.
            - Se perguntarem "qual é o meu nome?", responda o nome dito nesta conversa.
            - Outro conversationId é outra conversa: não leve nome nem assunto de uma para a outra.

            Ferramentas, sem inventar números:
            - Calorias ou nutrientes de um alimento: chame buscarAlimento e use o valor da tabela. 100 g de tapioca é o valor por 100 g da tabela.
            - Quanto já comeu, a meta ou quantas calorias ainda pode comer: chame resumoDoDia.
            - Quando a pessoa disser que comeu algo: chame registrarRefeicao. Se não informar gramas, estime (1 ovo ≈ 50 g, 1 fruta média ≈ 100 g).
            - Tipos de refeição: BREAKFAST café, LUNCH almoço, DINNER jantar, SNACK lanche.

            Documentos da clínica:
            - Horário, endereço, água por dia, montagem do prato e regras de atendimento vêm dos trechos recuperados.
            - Use esses fatos. Não invente horário, telefone nem quantidade de água.
            """;

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(30).build();
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) throws IOException {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = clinicDocuments();
        store.add(documents);
        log.info("Documentos da clínica carregados no vector store: {}", documents.size());
        return store;
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory, VectorStore vectorStore, NutritionTools tools) {
        QuestionAnswerAdvisor clinicAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(4).similarityThreshold(0.0).build())
                .promptTemplate(PromptTemplate.builder().template("""
                        {query}

                        Trechos dos documentos da Clínica Equilíbrio:
                        ---------------------
                        {question_answer_context}
                        ---------------------

                        Use os trechos para fatos da clínica (água, horário, endereço, prato, regras).
                        Para calorias, resumo do dia e registro de refeição, use as ferramentas.
                        Para nome e assuntos ditos pela pessoa, use o histórico desta conversa.
                        """).build())
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(NUTRITION_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        clinicAdvisor)
                .defaultTools(tools)
                .build();
    }

    private List<Document> clinicDocuments() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:docs/*.md");
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            try (var input = resource.getInputStream()) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                documents.add(Document.builder()
                        .text(text)
                        .metadata(Map.of("source", resource.getFilename() == null ? "doc" : resource.getFilename()))
                        .build());
            }
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("Nenhum documento em resources/docs");
        }
        return documents;
    }
}
