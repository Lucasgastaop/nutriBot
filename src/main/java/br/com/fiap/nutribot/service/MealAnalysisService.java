package br.com.fiap.nutribot.service;

import br.com.fiap.nutribot.dto.MealAnalysisResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

// Estimativa estruturada. Este ChatClient não tem tools: nada é gravado no diário.
@Service
public class MealAnalysisService {

    private static final String SYSTEM = """
            Você estima a composição nutricional de uma refeição descrita em português.
            Não registre a refeição e não prescreva dieta.
            Para cada alimento, informe o nome em português, as gramas da porção e as calorias dessa porção.
            totalCalories é a soma das calorias dos itens.
            protein, carbs e fat são os totais em gramas.
            Quando a frase não trouxer quantidade, use porções brasileiras usuais:
            arroz 150 g, feijão 80 g, bife 120 g, salada 40 g, fruta média 100 g.
            Todos os campos numéricos devem ser números.
            """;

    private final ChatClient chatClient;

    public MealAnalysisService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM)
                .build();
    }

    public MealAnalysisResponse analyze(String description) {
        return chatClient.prompt()
                .user(description)
                .call()
                .entity(MealAnalysisResponse.class);
    }
}
