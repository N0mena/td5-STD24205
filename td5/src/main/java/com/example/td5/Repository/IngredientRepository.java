package com.example.td5.Repository;

import com.example.td5.Entity.Ingredient;
import com.example.td5.Entity.StockValue;
import com.example.td5.Enum.CategoryEnum;
import com.example.td5.Enum.Unit;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class IngredientRepository {

    private final DataSource dataSource;

    public IngredientRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Ingredient> findAll() throws SQLException {
        String sql = "SELECT id, name, category, price FROM ingredient";
        List<Ingredient> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapIngredient(rs));
            }
        }
        return list;
    }

    public Ingredient findById(Integer id) throws SQLException {
        String sql = "SELECT id, name, category, price FROM ingredient WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapIngredient(rs);
                }
            }
        }
        return null;
    }

    public List<Ingredient> findIngredients(int page, int size) throws SQLException {
        String sql = "SELECT id, name, category, price FROM ingredient ORDER BY id LIMIT ? OFFSET ?";
        int offset = (page - 1) * size;
        List<Ingredient> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapIngredient(rs));
                }
            }
        }
        return list;
    }


    public List<Ingredient> findIngredientsByCriteria(String ingredientName,
                                                      CategoryEnum category,
                                                      String dishName,
                                                      int page,
                                                      int size) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT i.id, i.name, i.category, i.price
                FROM ingredient i
                """);

        if (dishName != null && !dishName.isBlank()) {
            sql.append("""
                    JOIN dish_ingredient di ON di.id_ingredient = i.id
                    JOIN dish d ON d.id = di.id_dish
                    """);
        }

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (ingredientName != null && !ingredientName.isBlank()) {
            conditions.add("LOWER(i.name) LIKE LOWER(?)");
            params.add("%" + ingredientName + "%");
        }
        if (category != null) {
            conditions.add("i.category = ?::category");
            params.add(category.name());
        }
        if (dishName != null && !dishName.isBlank()) {
            conditions.add("LOWER(d.name) LIKE LOWER(?)");
            params.add("%" + dishName + "%");
        }

        if (!conditions.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY i.id LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);

        List<Ingredient> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapIngredient(rs));
                }
            }
        }
        return list;
    }

    public List<Ingredient> createIngredients(List<Ingredient> ingredients) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check for duplicates first
                for (Ingredient ingredient : ingredients) {
                    if (existsByName(conn, ingredient.getName())) {
                        throw new RuntimeException(
                                "Ingredient with name '" + ingredient.getName() + "' already exists");
                    }
                }

                String sql = """
                        INSERT INTO ingredient (id, name, category, price)
                        VALUES (?, ?, ?::category, ?)
                        """;
                List<Integer> newIds = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int nextId = getNextSerialValue(conn, "ingredient", "id");
                    for (Ingredient ingredient : ingredients) {
                        newIds.add(nextId);
                        ps.setInt(1, nextId++);
                        ps.setString(2, ingredient.getName());
                        ps.setString(3, ingredient.getCategory().name());
                        ps.setDouble(4, ingredient.getPrice());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();

                // Return freshly fetched ingredients
                List<Ingredient> created = new ArrayList<>();
                for (Integer id : newIds) {
                    created.add(findById(id));
                }
                return created;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private boolean existsByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT 1 FROM ingredient WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public StockValue getStockValueAt(Integer ingredientId, Instant at, Unit unit) throws SQLException {
        String sql = """
                SELECT unit,
                       SUM(CASE
                           WHEN type = 'IN' THEN quantity
                           WHEN type = 'OUT' THEN -1 * quantity
                           ELSE 0 END) AS actual_quantity
                FROM stock_movement
                WHERE id_ingredient = ?
                  AND unit = ?
                  AND creation_datetime <= ?
                GROUP BY unit
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            ps.setString(2, unit.name());
            ps.setTimestamp(3, Timestamp.from(at));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StockValue sv = new StockValue();
                    sv.setQuantity(rs.getDouble("actual_quantity"));
                    sv.setUnit(Unit.valueOf(rs.getString("unit")));
                    return sv;
                }
            }
        }
        return null;
    }

    private Ingredient mapIngredient(ResultSet rs) throws SQLException {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(rs.getInt("id"));
        ingredient.setName(rs.getString("name"));
        ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));
        ingredient.setPrice(rs.getDouble("price"));
        return ingredient;
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