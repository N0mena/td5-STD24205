package com.example.td5.Entity;

import com.example.td5.Enum.Unit;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class DishIngredient {
    @JsonIgnore
    private Dish dish;
    private Ingredient ingredient;
    private Double quantity;
    private Unit unit;

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Dish getDish() {
        return dish;
    }

    public void setDish(Dish dish) {
        this.dish = dish;
    }

    @Override
    public String toString() {
        return "DishIngredient{" +
                "ingredient=" + ingredient +
                ", quantity=" + quantity +
                ", unit=" + unit +
                '}';
    }
}