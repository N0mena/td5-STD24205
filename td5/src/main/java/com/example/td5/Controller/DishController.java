package com.example.td5.Controller;

import com.example.td5.Entity.Dish;
import com.example.td5.Entity.Ingredient;
import com.example.td5.Repository.DishRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/dishes")
public class DishController {

    private final DishRepository dishRepository;

    public DishController(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @GetMapping("/{id}/ingredients")
    public ResponseEntity<?> getIngredientsByDish(
            @PathVariable Integer id,
            @RequestParam(required = false) String ingredientName,
            @RequestParam(required = false) Double ingredientPriceAround
    ) throws SQLException {

        Dish dish = dishRepository.findById(id);

        if (dish == null) {
            return ResponseEntity
                    .status(404)
                    .body("Dish.id=" + id + " is not found");
        }

        List<Ingredient> ingredients =
                dishRepository.findIngredientsByDishId(id, ingredientName, ingredientPriceAround);

        return ResponseEntity.ok(ingredients);
    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) String ingredientName) throws SQLException {

        if (ingredientName != null) {
            List<Dish> dishes = dishRepository.findDishsByIngredientName(ingredientName);
            return ResponseEntity.ok(dishes);
        }

        List<Dish> dishes = dishRepository.findAll();
        return ResponseEntity.ok(dishes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) throws SQLException {
        try {
            Dish dish = dishRepository.findDishById(id);
            return ResponseEntity.ok(dish);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Dish dish) throws SQLException {
        if (dish == null) {
            return ResponseEntity.status(400).body("Request body is mandatory.");
        }
        Dish saved = dishRepository.saveDish(dish);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}/ingredients")
    public ResponseEntity<?> updateIngredients(
            @PathVariable Integer id,
            @RequestBody(required = false) List<Ingredient> ingredients) throws SQLException {

        if (ingredients == null) {
            return ResponseEntity
                    .status(400)
                    .body("Request body is mandatory and must contain a list of ingredients.");
        }

        Dish dish = dishRepository.findById(id);
        if (dish == null) {
            return ResponseEntity
                    .status(404)
                    .body("Dish.id=" + id + " is not found");
        }

        Dish updated = dishRepository.updateIngredients(id, ingredients);
        return ResponseEntity.ok(updated);
    }
}