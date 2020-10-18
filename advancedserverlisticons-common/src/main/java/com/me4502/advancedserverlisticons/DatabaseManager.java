/*
 * Copyright (c) 2017 Me4502 (Madeline Miller)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.me4502.advancedserverlisticons;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private HikariDataSource dataSource;

    public DatabaseManager(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(this.jdbcUrl);
        dataSource.setUsername(this.username);
        dataSource.setPassword(this.password);

        if (!doesTableExist("player_addresses")) {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement("CREATE TABLE player_addresses (`uuid` CHAR(36) PRIMARY KEY, `address` VARCHAR(20));");
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void disconnect() {
        try {
            if (!dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception e) {
        }
    }

    public void addPlayerAddress(UUID player, String address) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO player_addresses (`uuid`, `address`) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE `address`=VALUES(`address`);");
            statement.setString(1, player.toString());
            statement.setString(2, address);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerAddress(UUID player) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `address` FROM player_addresses WHERE `uuid` = ? LIMIT 1;");
            statement.setString(1, player.toString());

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("address");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public UUID getPlayerUUID(String address) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `uuid` FROM player_addresses WHERE `address` = ? LIMIT 1;");
            statement.setString(1, address);

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return UUID.fromString(resultSet.getString("uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void clearAll() {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM player_addresses;");

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearAll(UUID player) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM player_addresses WHERE `uuid` = ?;");
            statement.setString(1, player.toString());

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean doesTableExist(String name) {
        boolean ret = false;

        try (Connection connection = getConnection()) {
            DatabaseMetaData dbm = connection.getMetaData();
            ResultSet set = dbm.getTables(null, null, name, null);
            if (set.next()) {
                ret = true;
            }
            set.close();
        } catch (SQLException ex) {
            ret = false;
        }

        return ret;
    }

    private Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
