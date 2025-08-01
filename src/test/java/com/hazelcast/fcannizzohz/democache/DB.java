package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.fcannizzohz.democache.model.Product;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public class DB {

    public static DB newDB() {
        Map<String, String> env = null;
        try {
            env = EnvLoader.load(".env");
        } catch (IOException e) {
            throw new RuntimeException("Unable to load env file.", e);
        }
        String url = env.getOrDefault("POSTGRES_URL", "jdbc:postgresql://localhost:5432/postgres");
        String user = env.getOrDefault("POSTGRES_USER", "postgres");
        String password = env.getOrDefault("POSTGRES_PASSWORD", "");

        Connection connection = null;
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to connect to database.", e);
        }
        return new DB(connection);
    }

    private final Connection connection;

    public DB(Connection connection) {
        this.connection = connection;
    }

    public List<Product> getProducts() throws SQLException {
        List<Product> products = new ArrayList<>();

        String query = "SELECT id, name, price FROM products";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Integer id = rs.getInt("id");
                String name = rs.getString("name");
                BigDecimal price = rs.getBigDecimal("price");

                products.add(new Product(id, name, price));
            }
        }

        return products;
    }

    public boolean isClosed() {
        try {
            return this.connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    public void close() {
        try {
            this.connection.close();
        } catch (SQLException e) {
            // pass
        }
    }
}
