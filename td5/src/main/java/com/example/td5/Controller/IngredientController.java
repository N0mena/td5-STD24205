package com.example.td5.Controller;

import com.example.td5.Entity.Ingredient;
import com.example.td5.Entity.StockValue;
import com.example.td5.Enum.CategoryEnum;
import com.example.td5.Enum.Unit;
import com.example.td5.Repository.IngredientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientRepository ingredientRepository;

    public IngredientController(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dishName) throws SQLException {

        boolean hasPagination = page != null && size != null;
        boolean hasCriteria   = name != null || category != null || dishName != null;

        if (hasPagination && hasCriteria) {
            CategoryEnum categoryEnum = null;
            if (category != null) {
                try {
                    categoryEnum = CategoryEnum.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(400)
                            .body("Invalid category value: " + category);
                }
            }
            List<Ingredient> ingredients = ingredientRepository.findIngredientsByCriteria(
                    name, categoryEnum, dishName, page, size);
            return ResponseEntity.ok(ingredients);
        }

        if (hasPagination) {
            List<Ingredient> ingredients = ingredientRepository.findIngredients(page, size);
            return ResponseEntity.ok(ingredients);
        }

        List<Ingredient> ingredients = ingredientRepository.findAll();
        return ResponseEntity.ok(ingredients);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) throws SQLException {
        Ingredient ingredient = ingredientRepository.findById(id);
        if (ingredient == null) {
            return ResponseEntity
                    .status(404)
                    .body("Ingredient.id=" + id + " is not found");
        }
        return ResponseEntity.ok(ingredient);
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<?> getStock(
            @PathVariable Integer id,
            @RequestParam(required = false) String at,
            @RequestParam(required = false) String unit) throws SQLException {

        if (at == null || unit == null) {
            return ResponseEntity
                    .status(400)
                    .body("Either mandatory query parameter `at` or `unit` is not provided.");
        }

        Ingredient ingredient = ingredientRepository.findById(id);
        if (ingredient == null) {
            return ResponseEntity
                    .status(404)
                    .body("Ingredient.id=" + id + " is not found");
        }

        Instant instant  = Instant.parse(at);
        Unit    unitEnum = Unit.valueOf(unit.toUpperCase());

        StockValue stockValue = ingredientRepository.getStockValueAt(id, instant, unitEnum);
        if (stockValue == null) {
            return ResponseEntity
                    .status(404)
                    .body("No stock data found for Ingredient.id=" + id);
        }

        return ResponseEntity.ok(stockValue);
    }

    @PostMapping
    public ResponseEntity<?> createIngredients(
            @RequestBody(required = false) List<Ingredient> ingredients) throws SQLException {

        if (ingredients == null || ingredients.isEmpty()) {
            return ResponseEntity
                    .status(400)
                    .body("Request body is mandatory and must contain a list of ingredients.");
        }

        try {
            List<Ingredient> created = ingredientRepository.createIngredients(ingredients);
            return ResponseEntity.status(201).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}