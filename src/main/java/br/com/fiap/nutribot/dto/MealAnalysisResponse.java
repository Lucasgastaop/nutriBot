package br.com.fiap.nutribot.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Estimativa do modelo. Nada disso é gravado no diário.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MealAnalysisResponse(
        List<Item> items,
        double totalCalories,
        double protein,
        double carbs,
        double fat
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String food, double grams, double calories) {}
}
