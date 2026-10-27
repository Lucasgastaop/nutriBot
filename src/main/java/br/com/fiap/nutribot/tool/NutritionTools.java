package br.com.fiap.nutribot.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import br.com.fiap.nutribot.dto.MealEntryResponse;
import br.com.fiap.nutribot.dto.SummaryResponse;
import br.com.fiap.nutribot.model.Food;
import br.com.fiap.nutribot.model.MealEntry;
import br.com.fiap.nutribot.model.MealType;
import br.com.fiap.nutribot.service.FoodService;
import br.com.fiap.nutribot.service.MealService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// As três ferramentas do chat. Os números vêm dos services, não do modelo.
@Component
public class NutritionTools {

    private final FoodService foodService;
    private final MealService mealService;

    public NutritionTools(FoodService foodService, MealService mealService) {
        this.foodService = foodService;
        this.mealService = mealService;
    }

    @Tool(description = """
            Busca alimentos na tabela da clínica pelo nome.
            Devolve calorias, proteína, carboidrato e gordura por 100 g.
            Use sempre que perguntarem calorias ou nutrientes de um alimento, como tapioca ou ovo.
            Não invente valores: responda só com o que esta ferramenta devolver.
            """)
    public String buscarAlimento(
            @ToolParam(description = "Nome ou parte do nome do alimento. Ex.: tapioca, ovo, banana") String nome) {
        List<Food> foods = lookup(nome);
        if (foods.isEmpty()) {
            return "Nenhum alimento encontrado na tabela para: " + nome;
        }
        StringBuilder answer = new StringBuilder("Valores da tabela por 100 g:\n");
        foods.stream().limit(5).forEach(food -> answer.append("- %s (%s): %.0f kcal, proteína %.1f g, carboidrato %.1f g, gordura %.1f g%n"
                .formatted(food.getName(), food.getCategory(), food.getCalories(), food.getProtein(),
                        food.getCarbs(), food.getFat())));
        return answer.toString();
    }

    @Tool(description = """
            Resumo alimentar de um dia: calorias já consumidas, meta, calorias que ainda pode comer,
            proteína, carboidrato, gordura e refeições registradas.
            Use para perguntas como "quantas calorias ainda posso comer hoje?".
            Não invente o saldo: use o campo de calorias restantes desta ferramenta.
            """)
    public String resumoDoDia(
            @ToolParam(description = "Data yyyy-MM-dd. Use hoje para o dia atual.") String data) {
        try {
            SummaryResponse summary = mealService.summary(parseDate(data));
            String meals = summary.meals().isEmpty()
                    ? "(nenhuma refeição registrada)"
                    : summary.meals().stream().map(this::formatMeal).reduce((a, b) -> a + "\n" + b).orElse("");
            return """
                    Data: %s
                    Calorias consumidas: %.1f kcal
                    Meta diária: %d kcal
                    Calorias que ainda pode comer: %.1f kcal
                    Proteína: %.1f g
                    Carboidrato: %.1f g
                    Gordura: %.1f g
                    Refeições:
                    %s
                    """.formatted(summary.date(), summary.consumedCalories(), summary.dailyCalories(),
                    summary.remainingCalories(), summary.protein(), summary.carbs(), summary.fat(), meals);
        }
        catch (DateTimeParseException ex) {
            return "Data inválida. Use yyyy-MM-dd ou hoje.";
        }
    }

    @Tool(description = """
            Registra uma refeição no diário do paciente.
            Use quando a pessoa disser que comeu algo e quiser anotar, por exemplo "comi 2 ovos no café".
            A gravação só acontece quando esta ferramenta é chamada.
            """)
    public String registrarRefeicao(
            @ToolParam(description = "Nome do alimento, de preferência como na tabela. Ex.: Ovo cozido, Banana prata") String alimento,
            @ToolParam(description = "Quantidade em gramas da porção. 1 ovo ≈ 50 g, 1 fruta média ≈ 100 g.") double gramas,
            @ToolParam(description = "BREAKFAST para café, LUNCH para almoço, DINNER para jantar, SNACK para lanche") String tipoRefeicao) {
        try {
            List<Food> foods = lookup(alimento);
            if (foods.isEmpty()) {
                return "Não registrei: alimento não encontrado na tabela para \"" + alimento + "\".";
            }
            Food food = choose(foods, alimento);
            MealEntry entry = mealService.log(food.getName(), gramas, mealType(tipoRefeicao));
            return "Refeição registrada no diário. id=%d, data=%s, refeição=%s, alimento=%s, gramas=%.0f, calorias=%.1f"
                    .formatted(entry.getId(), entry.getDate(), entry.getMealType(), entry.getFood().getName(),
                            entry.getGrams(), entry.calories());
        }
        catch (ResponseStatusException ex) {
            return "Não registrei a refeição: " + ex.getReason();
        }
        catch (IllegalArgumentException ex) {
            return "Não registrei a refeição: " + ex.getMessage();
        }
    }

    private String formatMeal(MealEntryResponse meal) {
        return "- %s: %s, %.0f g, %.1f kcal".formatted(meal.mealType(), meal.foodName(), meal.grams(), meal.calories());
    }

    private List<Food> lookup(String nome) {
        if (nome == null || nome.isBlank()) {
            return List.of();
        }
        String simplified = fold(nome).replaceAll("^\\d+([.,]\\d+)?\\s*(g|gramas)?\\s*(de\\s+)?", "").trim();
        List<Food> found = searchLoose(simplified.isBlank() ? fold(nome) : simplified);
        if (found.isEmpty() && simplified.contains(" ")) {
            found = searchLoose(simplified.substring(simplified.lastIndexOf(' ') + 1));
        }
        return found;
    }

    private List<Food> searchLoose(String query) {
        if (query.isBlank()) {
            return List.of();
        }
        List<Food> found = foodService.search(query);
        if (!found.isEmpty()) {
            return found;
        }
        if (query.endsWith("s") && query.length() > 3) {
            return foodService.search(query.substring(0, query.length() - 1));
        }
        return List.of();
    }

    private Food choose(List<Food> foods, String query) {
        String folded = fold(query);
        return foods.stream()
                .min(Comparator.comparingInt((Food food) -> rank(food.getName(), folded)).thenComparing(Food::getName))
                .orElseThrow();
    }

    private int rank(String name, String query) {
        String folded = fold(name);
        if (folded.equals(query)) {
            return 0;
        }
        if (folded.startsWith(query)) {
            return 1;
        }
        if (folded.contains(query)) {
            return 2;
        }
        return 3;
    }

    private LocalDate parseDate(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String value = fold(data);
        if (value.equals("hoje") || value.equals("today")) {
            return null;
        }
        return LocalDate.parse(data.trim());
    }

    private MealType mealType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("informe o tipo da refeição");
        }
        String folded = fold(value);
        if (folded.contains("breakfast") || folded.contains("cafe")) {
            return MealType.BREAKFAST;
        }
        if (folded.contains("lunch") || folded.contains("almoco")) {
            return MealType.LUNCH;
        }
        if (folded.contains("dinner") || folded.contains("jantar")) {
            return MealType.DINNER;
        }
        if (folded.contains("snack") || folded.contains("lanche")) {
            return MealType.SNACK;
        }
        return MealType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private String fold(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }
}
