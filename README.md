# NutriBot API

Projeto base do **Diamante — Java Advanced (segundo semestre)**.

A NutriBot é a nutricionista virtual da Clínica Equilíbrio. O projeto base já tem o diário alimentar, a tabela de alimentos e o perfil do paciente. A parte de IA com **Spring AI** está implementada: chat com system prompt, memória de conversa, tool calling, RAG e structured output.

O enunciado completo e os critérios de avaliação estão no deck da atividade.

## Equipe

| Nome | RM |
|---|---|
| PREENCHER | PREENCHER |

## Modelo

O padrão é o Ollama local: chat com `llama3.2` e embeddings com `nomic-embed-text`, em `http://localhost:11434/v1`. Nenhuma chave fica no repositório. Dá para trocar o modelo pelas variáveis de ambiente abaixo.

O que a equipe implementou, em cima do projeto base:

- `POST /chat` com `ChatClient`, persona da NutriBot em português
- memória `MessageWindowChatMemory` separada por `conversationId`
- tools `buscarAlimento`, `resumoDoDia` e `registrarRefeicao` com `@Tool`
- RAG no `SimpleVectorStore`, com os markdowns de `resources/docs` carregados na subida
- `POST /meals/analyze` com structured output (`.entity()`), sem gravar no diário

## Como rodar

Requisitos: Java 25 e Maven (ou o `mvnw` da sua IDE).

```bash
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. O console do H2 não está habilitado; os dados iniciais são carregados pelo `DataLoader` a cada inicialização.

O arquivo `test.http` tem exemplos de todas as rotas.

## Configurando o modelo

O projeto usa o starter da OpenAI do Spring AI, que funciona com **qualquer API compatível com a da OpenAI**. A configuração é feita por variáveis de ambiente — **nunca coloque a chave no código ou no `application.yml`**, o repositório é público.

| Variável | Padrão | Para que serve |
|---|---|---|
| `OPENAI_API_KEY` | `ollama` | Chave da API. No Ollama qualquer valor serve |
| `OPENAI_BASE_URL` | `http://localhost:11434/v1` | Endereço da API, já com `/v1`. O Spring AI 2 acrescenta `/chat/completions` e `/embeddings` |
| `OPENAI_MODEL` | `llama3.2` | Modelo de chat |
| `OPENAI_EMBEDDING_MODEL` | `nomic-embed-text` | Modelo de embeddings (RAG) |

Exemplos:

```bash
# OpenAI
export OPENAI_API_KEY=sk-...

# Ollama local (ollama pull llama3.2 && ollama pull nomic-embed-text)
export OPENAI_API_KEY=ollama
export OPENAI_BASE_URL=http://localhost:11434/v1
export OPENAI_MODEL=llama3.2
export OPENAI_EMBEDDING_MODEL=nomic-embed-text

# Google Gemini (endpoint compatível com OpenAI)
export OPENAI_API_KEY=sua-chave-do-ai-studio
export OPENAI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
export OPENAI_MODEL=gemini-2.5-flash
export OPENAI_EMBEDDING_MODEL=gemini-embedding-001
```

No IntelliJ, as variáveis podem ser definidas em *Run → Edit Configurations → Environment variables*.

## O que já está pronto

| Método | Rota | Descrição |
|---|---|---|
| GET | `/foods?name=` | Busca na tabela de alimentos (valores por 100 g) |
| GET | `/foods/{id}` | Um alimento |
| GET | `/meals?date=` | Refeições do dia (sem `date`, hoje) |
| POST | `/meals` | Registra uma refeição `{date?, mealType, foodId, grams}` → 201 |
| DELETE | `/meals/{id}` | Remove uma refeição → 204 |
| GET | `/summary?date=` | Resumo do dia: calorias, macros, meta e o que falta |
| GET | `/profile` | Perfil do paciente |
| PUT | `/profile` | Atualiza `{name, goal, dailyCalories}` |

- `mealType`: `BREAKFAST`, `LUNCH`, `DINNER` ou `SNACK`.
- `goal`: `LOSE_WEIGHT`, `MAINTAIN` ou `GAIN_MUSCLE`.
- Erros sempre no formato `{"error": "..."}`.

Os serviços (`FoodService`, `MealService`, `ProfileService`) já têm os métodos que as tools vão precisar, como `FoodService.findByName`, `MealService.log` e `MealService.summary`.

## Base de conhecimento (RAG)

Os documentos da clínica estão em `src/main/resources/docs/`:

- `guia-alimentar.md` — orientações nutricionais da Clínica Equilíbrio
- `regras-atendimento.md` — o que o NutriBot pode e não pode fazer, horários e contato

Eles são carregados no `SimpleVectorStore` na inicialização da aplicação.

## Rotas da equipe

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| POST | `/chat` | `{conversationId, message}` | `{answer}` |
| POST | `/meals/analyze` | `{description}` | `{items: [{food, grams, calories}], totalCalories, protein, carbs, fat}` |

O app de testes está em https://github.com/joaocarloslima/nutribot-app.
