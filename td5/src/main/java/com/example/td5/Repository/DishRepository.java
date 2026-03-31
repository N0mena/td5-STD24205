package com.example.td5.Repository;

import com.example.td5.Entity.Dish;
import com.example.td5.Entity.DishIngredient;
import com.example.td5.Entity.Ingredient;
import com.example.td5.Enum.CategoryEnum;
import com.example.td5.Enum.DishTypeEnum;
import com.example.td5.Enum.Unit;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DishRepository {

    private final DataSource dataSource;

    public DishRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Ingredient> findIngredientsByDishId(
            Integer dishId,
            String ingredientName,
            Double ingredientPriceAround
    ) throws SQLException {

        String sql = """
            SELECT i.id, i.name, i.category, i.price
            FROM ingredient i
            JOIN dish_ingredient di ON di.id_ingredient = i.id
            WHERE di.id_dish = ?
            """;

        List<Ingredient> list = new ArrayList<>();

        // construction dynamique comme ton style
        if (ingredientName != null) {
            sql += " AND i.name ILIKE ? ";
        }

        if (ingredientPriceAround != null) {
            sql += " AND i.price BETWEEN ? AND ? ";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int index = 1;

            ps.setInt(index++, dishId);

            if (ingredientName != null) {
                ps.setString(index++, "%" + ingredientName + "%");
            }

            if (ingredientPriceAround != null) {
                ps.setDouble(index++, ingredientPriceAround - 50);
                ps.setDouble(index++, ingredientPriceAround + 50);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    Ingredient ingredient = new Ingredient();
                    ingredient.setId(rs.getInt("id"));
                    ingredient.setName(rs.getString("name"));
                    ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));
                    ingredient.setPrice(rs.getDouble("price"));

                    list.add(ingredient);
                }
            }
        }

        return list;
    }

    public List<Dish> findAll() throws SQLException {
        String sql = "SELECT id, name, dish_type, selling_price FROM dish";
        List<Dish> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Dish dish = mapDish(rs);
                dish.setDishIngredients(findIngredientsByDishId(dish.getId()));
                list.add(dish);
            }
        }
        return list;
    }

    public Dish findById(Integer id) throws SQLException {
        String sql = "SELECT id, name, dish_type, selling_price FROM dish WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Dish dish = mapDish(rs);
                    dish.setDishIngredients(findIngredientsByDishId(id));
                    return dish;
                }
            }
        }
        return null;
    }


    public Dish findDishById(Integer id) throws SQLException {
        Dish dish = findById(id);
        if (dish == null) {
            throw new RuntimeException("Dish.id=" + id + " is not found");
        }
        return dish;
    }

    public List<Dish> findDishsByIngredientName(String ingredientName) throws SQLException {
        String sql = """
                SELECT DISTINCT d.id, d.name, d.dish_type, d.selling_price
                FROM dish d
                JOIN dish_ingredient di ON di.id_dish = d.id
                JOIN ingredient i ON i.id = di.id_ingredient
                WHERE LOWER(i.name) LIKE LOWER(?)
                """;
        List<Dish> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + ingredientName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Dish dish = mapDish(rs);
                    dish.setDishIngredients(findIngredientsByDishId(dish.getId()));
                    list.add(dish);
                }
            }
        }
        return list;
    }

    public Dish saveDish(Dish dish) throws SQLException {
        if (dish.getId() == null) {
            String sql = """
                    INSERT INTO dish (id, name, dish_type, selling_price)
                    VALUES (?, ?, ?::dish_type, ?)
                    """;
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                int newId;
                try {
                    newId = getNextSerialValue(conn, "dish", "id");
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, newId);
                        ps.setString(2, dish.getName());
                        ps.setString(3, dish.getDishType().name());
                        if (dish.getPrice() != null) {
                            ps.setDouble(4, dish.getPrice());
                        } else {
                            ps.setNull(4, Types.DOUBLE);
                        }
                        ps.executeUpdate();
                    }
                    insertDishIngredients(conn, newId, dish.getDishIngredients());
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
                return findById(newId);
            }
        } else {
            return updateIngredients(dish.getId(), extractIngredients(dish.getDishIngredients()));
        }
    }

    public Dish updateIngredients(Integer dishId, List<Ingredient> ingredients) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM dish_ingredient WHERE id_dish = ?")) {
                    ps.setInt(1, dishId);
                    ps.executeUpdate();
                }
                List<Ingredient> validIngredients = filterExistingIngredients(conn, ingredients);

                if (!validIngredients.isEmpty()) {
                    String sql = """
                            INSERT INTO dish_ingredient (id, id_ingredient, id_dish, required_quantity, unit)
                            VALUES (?, ?, ?, ?, ?::unit)
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        int nextId = getNextSerialValue(conn, "dish_ingredient", "id");
                        for (Ingredient ingredient : validIngredients) {
                            ps.setInt(1, nextId++);
                            ps.setInt(2, ingredient.getId());
                            ps.setInt(3, dishId);
                            ps.setNull(4, Types.DOUBLE);
                            ps.setString(5, Unit.PCS.name());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
            return findById(dishId);
        }
    }

    private void insertDishIngredients(Connection conn, int dishId, List<DishIngredient> dishIngredients) throws SQLException {
        if (dishIngredients == null || dishIngredients.isEmpty()) return;
        String sql = """
                INSERT INTO dish_ingredient (id, id_ingredient, id_dish, required_quantity, unit)
                VALUES (?, ?, ?, ?, ?::unit)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int nextId = getNextSerialValue(conn, "dish_ingredient", "id");
            for (DishIngredient di : dishIngredients) {
                ps.setInt(1, nextId++);
                ps.setInt(2, di.getIngredient().getId());
                ps.setInt(3, dishId);
                if (di.getQuantity() != null) {
                    ps.setDouble(4, di.getQuantity());
                } else {
                    ps.setNull(4, Types.DOUBLE);
                }
                ps.setString(5, di.getUnit() != null ? di.getUnit().name() : Unit.PCS.name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<Ingredient> extractIngredients(List<DishIngredient> dishIngredients) {
        if (dishIngredients == null) return new ArrayList<>();
        List<Ingredient> ingredients = new ArrayList<>();
        for (DishIngredient di : dishIngredients) {
            ingredients.add(di.getIngredient());
        }
        return ingredients;
    }

    private List<Ingredient> filterExistingIngredients(Connection conn,
                                                       List<Ingredient> ingredients) throws SQLException {
        List<Ingredient> valid = new ArrayList<>();
        String sql = "SELECT id FROM ingredient WHERE id = ?";
        for (Ingredient ingredient : ingredients) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, ingredient.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        valid.add(ingredient);
                    }
                }
            }
        }
        return valid;
    }

    private List<DishIngredient> findIngredientsByDishId(Integer dishId) throws SQLException {
        String sql = """
                SELECT ingredient.id, ingredient.name, ingredient.price, ingredient.category,
                       di.required_quantity, di.unit
                FROM ingredient
                JOIN dish_ingredient di ON di.id_ingredient = ingredient.id
                WHERE di.id_dish = ?
                """;
        List<DishIngredient> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dishId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ingredient ingredient = new Ingredient();
                    ingredient.setId(rs.getInt("id"));
                    ingredient.setName(rs.getString("name"));
                    ingredient.setPrice(rs.getDouble("price"));
                    ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));

                    DishIngredient di = new DishIngredient();
                    di.setIngredient(ingredient);
                    di.setQuantity(rs.getObject("required_quantity") == null
                            ? null : rs.getDouble("required_quantity"));
                    di.setUnit(Unit.valueOf(rs.getString("unit")));
                    list.add(di);
                }
            }
        }
        return list;
    }

    private Dish mapDish(ResultSet rs) throws SQLException {
        Dish dish = new Dish();
        dish.setId(rs.getInt("id"));
        dish.setName(rs.getString("name"));
        dish.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type")));
        dish.setPrice(rs.getObject("selling_price") == null ? null : rs.getDouble("selling_price"));
        return dish;
    }

    private int getNextSerialValue(Connection conn, String tableName,
                                   String columnName) throws SQLException {
        String sequenceName;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_get_serial_sequence(?, ?)")) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                sequenceName = rs.getString(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                String.format("SELECT setval('%s', (SELECT COALESCE(MAX(%s), 0) FROM %s))",
                        sequenceName, columnName, tableName))) {
            ps.executeQuery();
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT nextval(?)")) {
            ps.setString(1, sequenceName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}