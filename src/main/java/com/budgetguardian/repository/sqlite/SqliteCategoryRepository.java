package com.budgetguardian.repository.sqlite;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Category;
import com.budgetguardian.repository.CategoryRepository;
import com.budgetguardian.repository.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC access to the {@code category} table.
 *
 * <p><b>Purpose:</b> hydrates the eleven seeded categories into a
 * {@code HashMap<Integer, Category>}. Categories are fixed — read-only
 * repository.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass (n = 11).</p>
 */
public final class SqliteCategoryRepository implements CategoryRepository {

    private final Connection connection;

    public SqliteCategoryRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public HashMap<Integer, Category> findAll() throws StorageException {
        HashMap<Integer, Category> categories = new HashMap<>();
        String sql = "SELECT id, name, is_danger FROM category ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Category category = new Category(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("is_danger") == 1);
                categories.put(category.id(), category);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load categories", e);
        }
        return categories;
    }
}
